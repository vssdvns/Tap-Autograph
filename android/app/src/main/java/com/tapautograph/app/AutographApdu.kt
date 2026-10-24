package com.tapautograph.app

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Experimental non-payment protocol. In-memory consent only; no keys or payment data. */
class AutographApdu(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    companion object {
        const val AID = "F054415041555401"
        const val CHUNK = 192
        const val TTL = 60_000L
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 255) }
        fun unhex(text: String): ByteArray { require(text.length % 2 == 0 && text.matches(Regex("[0-9a-fA-F]*"))); return text.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
        val SELECT: ByteArray get() = unhex("00A4040008${AID}00")
        fun command(ins: Int, data: ByteArray) = byteArrayOf(0x80.toByte(), ins.toByte(), 0, 0, data.size.toByte()) + data
        fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
    private var packet: ByteArray? = null
    private var session = ByteArray(0)
    private var hash = ByteArray(0)
    private var deadline = 0L
    private var selected = false
    private var completed = false
    private val served = mutableSetOf<Int>()
    private var state = "Sharing is off"
    @Synchronized fun arm(bytes: ByteArray) {
        CompactInkCodec.decode(bytes) // UI/background work, never performed in the APDU callback.
        disarm(); packet = bytes.copyOf(); session = ByteArray(8).also { SecureRandom().nextBytes(it) }
        hash = digest(bytes); deadline = now() + TTL; state = "Ready for one transfer"
    }
    @Synchronized fun disarm() { packet?.fill(0); packet=null;session=ByteArray(0);hash=ByteArray(0);served.clear();selected=false;completed=false;deadline=0;state="Sharing is off" }
    @Synchronized fun deactivate() { selected=false }
    @Synchronized fun progress():Float { expire();val size=packet?.size ?: return if(completed)1f else 0f;return served.size.toFloat()/((size+CHUNK-1)/CHUNK) }
    @Synchronized fun status(): String { expire(); return state }
    @Synchronized fun remainingSeconds(): Long { expire();return ((deadline-now()).coerceAtLeast(0)+999)/1000 }
    private fun expire() { if (deadline>0 && now()>=deadline) { disarm();state="Sharing expired" } }
    private fun sw(code:Int)=byteArrayOf((code ushr 8).toByte(),code.toByte())
    private fun ok(data:ByteArray=byteArrayOf())=data+sw(0x9000)
    @Synchronized fun process(command: ByteArray): ByteArray {
        expire()
        if (command.contentEquals(SELECT)) {
            selected=false
            val bytes=packet ?: return sw(0x6985)
            selected=true
            val metadata=ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
                out.writeByte(1);out.write(session);out.writeInt(bytes.size);out.writeShort(CHUNK);out.writeShort((bytes.size+CHUNK-1)/CHUNK);out.write(hash)
            }}.toByteArray()
            return ok(metadata)
        }
        if (command.size<5) return sw(0x6700)
        if ((command[0].toInt() and 255) != 0x80) return sw(0x6E00)
        if (command[2].toInt()!=0 || command[3].toInt()!=0) return sw(0x6A86)
        val ins=command[1].toInt() and 255
        if (ins !in listOf(0x10,0x20)) return sw(0x6D00)
        val length=command[4].toInt() and 255
        if (command.size!=5+length || length!=(if(ins==0x10)10 else 40)) return sw(0x6700)
        if (!selected || deadline==0L) return sw(0x6985)
        if (!command.copyOfRange(5,13).contentEquals(session)) return sw(0x6985)
        if (ins==0x20) {
            if (!command.copyOfRange(13,45).contentEquals(hash)) return sw(0x6985)
            if (completed) return ok() // A repeated finish ACK on the same link is harmless.
            val bytes=packet ?: return sw(0x6985)
            if (served.size!=(bytes.size+CHUNK-1)/CHUNK) return sw(0x6985)
            bytes.fill(0);packet=null;completed=true;state="Transfer acknowledged by reader";return ok()
        }
        val bytes=packet ?: return sw(0x6985)
        val index=((command[13].toInt() and 255) shl 8) or (command[14].toInt() and 255)
        val start=index*CHUNK
        if (start>=bytes.size) return sw(0x6B00)
        served.add(index);state="Sent ${served.size} / ${(bytes.size+CHUNK-1)/CHUNK} chunks"
        return ok(session+byteArrayOf((index ushr 8).toByte(),index.toByte())+bytes.copyOfRange(start,minOf(start+CHUNK,bytes.size)))
    }
}

@Serializable data class ApduExchange(val command:String,val response:String)
@Serializable data class ReaderTranscript(val protocol:String="TAP1",val simulated:Boolean=true,val exchanges:List<ApduExchange>)
data class ReaderSimulation(val packet:ByteArray,val transcript:String,val chunks:Int)
object AutographReaderSimulator {
    private val transcriptJson = Json { encodeDefaults = true }
    /** Uses an isolated card engine, never the live sharing grant. Includes a duplicate read + link loss. */
    fun run(packet:ByteArray):ReaderSimulation {
        val card=AutographApdu();card.arm(packet);val exchanges=mutableListOf<ApduExchange>()
        fun send(command:ByteArray):ByteArray {
            val response=card.process(command);exchanges.add(ApduExchange(AutographApdu.hex(command),AutographApdu.hex(response)))
            require(response.size>=2 && response[response.size-2]==0x90.toByte() && response.last()==0.toByte()) { "Simulated reader received a failure status." }
            return response.dropLast(2).toByteArray()
        }
        val meta=send(AutographApdu.SELECT);val session=meta.copyOfRange(1,9)
        val count=((meta[15].toInt() and 255) shl 8) or (meta[16].toInt() and 255)
        val output=ByteArrayOutputStream()
        for(i in 0 until count) {
            val command=AutographApdu.command(0x10,session+byteArrayOf((i ushr 8).toByte(),i.toByte()))
            val response=send(command);output.write(response.copyOfRange(10,response.size))
            if(i==0) { require(send(command).contentEquals(response));card.deactivate();require(send(AutographApdu.SELECT).contentEquals(meta)) }
        }
        val assembled=output.toByteArray();require(assembled.contentEquals(packet));CompactInkCodec.decode(assembled)
        send(AutographApdu.command(0x20,session+AutographApdu.digest(assembled)))
        card.disarm()
        return ReaderSimulation(assembled,transcriptJson.encodeToString(ReaderTranscript(exchanges=exchanges)),count)
    }
}
