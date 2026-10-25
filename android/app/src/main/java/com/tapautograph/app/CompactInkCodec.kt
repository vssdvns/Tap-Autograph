package com.tapautograph.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import kotlin.math.roundToInt

/** TAG1: a versioned, bounded, cross-platform binary autograph container. Not encryption. */
object CompactInkCodec {
    const val MAX_PACKET = 65_536
    const val MAX_BODY = 1_048_576
    const val MAX_POINTS = 30_000
    const val MAX_STROKES = 1_000
    const val MAX_DURATION = 86_400_000L
    const val HEADER_SIZE = 13
    private val magic = byteArrayOf(0x54, 0x41, 0x47, 0x31) // TAG1

    fun encode(autograph: Autograph, compress: Boolean = true): ByteArray {
        val body = encodeBody(autograph)
        val zipped = if (compress) ByteArrayOutputStream().also { buffer -> DeflaterOutputStream(buffer).use { it.write(body) } }.toByteArray() else body
        val useZip = compress && zipped.size < body.size
        val payload = if (useZip) zipped else body
        require(payload.size + HEADER_SIZE <= MAX_PACKET) { "Compact autograph exceeds 64 KiB. Use a shorter autograph." }
        return ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.write(magic); out.writeByte(if (useZip) 1 else 0)
                out.writeInt(body.size); out.writeInt(CRC32().also { it.update(body) }.value.toInt())
                out.write(payload)
            }
        }.toByteArray()
    }

    internal fun encodeBody(autograph:Autograph):ByteArray {
        require(autograph.valid()) { "Invalid autograph." }
        require(autograph.color in 0..0xffffffffL) { "Invalid ink color." }
        require(autograph.strokes.size <= MAX_STROKES && autograph.strokes.sumOf { it.points.size } <= MAX_POINTS) { "Use a shorter autograph (maximum 30,000 points)." }
        val firstTime = autograph.strokes.first().points.first().time
        var previousTime = firstTime
        val bodyBuffer = ByteArrayOutputStream()
        DataOutputStream(bodyBuffer).use { out ->
            out.writeShort((autograph.aspect * 1024).roundToInt().coerceIn(1, 20479))
            out.writeShort((autograph.width * 65535).roundToInt())
            out.writeInt(autograph.color.toInt())
            out.variable(autograph.strokes.size)
            for (stroke in autograph.strokes) {
                out.variable(stroke.points.size)
                var x = 0; var y = 0
                for (p in stroke.points) {
                    require(p.time >= previousTime && p.time - firstTime <= MAX_DURATION) { "Handwriting timestamps must increase and span no more than 24 hours." }
                    require(p.pressure in 0f..2f && p.tool in 0..4) { "Invalid pen pressure or tool type." }
                    val nextX = (p.x * 4095).roundToInt(); val nextY = (p.y * 4095).roundToInt()
                    out.variable(zigzag(nextX - x)); out.variable(zigzag(nextY - y))
                    out.variable((p.time - previousTime).toInt())
                    out.writeByte((p.pressure * 127.5f).roundToInt()); out.writeByte(p.tool)
                    x = nextX; y = nextY; previousTime = p.time
                }
            }
        }
        val body = bodyBuffer.toByteArray()
        require(body.size <= MAX_BODY) { "Autograph exceeds decoded size limit." }
        return body
    }

    fun decode(packet: ByteArray): Autograph {
        if(packet.size>=4 && String(packet,0,4,Charsets.US_ASCII) in listOf("TAG3","TAG4"))return ColumnInkCodec.decode(packet)
        if(packet.size>=4 && String(packet,0,4,Charsets.US_ASCII)=="TAG2")return RasterInkCodec.decode(packet)
        require(packet.size in (HEADER_SIZE + 1)..MAX_PACKET) { "Invalid compact payload size." }
        val envelope = DataInputStream(ByteArrayInputStream(packet))
        val version = ByteArray(4); envelope.readFully(version)
        require(version.contentEquals(magic)) { "Unknown compact format version." }
        val flags = envelope.readUnsignedByte()
        require(flags in 0..1) { "Unknown compact format flags." }
        val bodyLength = envelope.readInt()
        require(bodyLength in 1..MAX_BODY) { "Invalid decoded size." }
        val checksum = envelope.readInt().toLong() and 0xffffffffL
        val payload = packet.copyOfRange(HEADER_SIZE, packet.size)
        val body = if (flags == 1) inflate(payload, bodyLength) else payload.also { require(it.size == bodyLength) { "Incorrect body length." } }
        require(CRC32().also { it.update(body) }.value == checksum) { "Compact payload checksum mismatch." }
        return decodeBody(body,packet)
    }

    internal fun decodeBody(body:ByteArray,packet:ByteArray):Autograph {
        try {
            val input = DataInputStream(ByteArrayInputStream(body))
            val aspect = input.readUnsignedShort() / 1024f
            val width = input.readUnsignedShort() / 65535f
            val color = input.readInt().toLong() and 0xffffffffL
            val strokeCount = input.variable(MAX_STROKES)
            require(strokeCount > 0) { "Empty autograph." }
            var total = 0; var time = 0L
            val strokes = List(strokeCount) {
                val count = input.variable(MAX_POINTS)
                require(count > 0 && total + count <= MAX_POINTS) { "Invalid point count." }
                total += count
                var x = 0; var y = 0
                InkStroke(List(count) {
                    x += unzigzag(input.variable(8190)); y += unzigzag(input.variable(8190))
                    require(x in 0..4095 && y in 0..4095) { "Coordinates outside canvas." }
                    time += input.variable(MAX_DURATION.toInt())
                    require(time <= MAX_DURATION) { "Playback duration exceeds limit." }
                    val pressure = input.readUnsignedByte() / 127.5f
                    val tool = input.readUnsignedByte()
                    require(tool in 0..4) { "Unsupported tool type." }
                    InkPoint(x / 4095f, y / 4095f, time, pressure, tool)
                })
            }
            require(input.available() == 0) { "Unexpected trailing handwriting data." }
            val digest = MessageDigest.getInstance("SHA-256").digest(packet).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
            return Autograph(id = "compact-$digest", aspect = aspect, strokes = strokes, color = color, width = width).also { require(it.valid()) { "Invalid decoded autograph." } }
        } catch (e: java.io.EOFException) { throw IllegalArgumentException("Truncated handwriting data.", e) }
    }

    internal fun inflate(payload: ByteArray, declared: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(payload)
            val out = ByteArrayOutputStream(minOf(declared, 8192)); val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                require(out.size() + n <= declared) { "Inflated data exceeds declared length." }
                if (n > 0) out.write(buffer, 0, n)
                else require(inflater.finished()) { "Invalid or incomplete compressed data." }
            }
            require(inflater.remaining == 0 && out.size() == declared) { "Compressed length mismatch or trailing bytes." }
            return out.toByteArray()
        } catch (e: java.util.zip.DataFormatException) { throw IllegalArgumentException("Invalid compressed payload.", e) }
        finally { inflater.end() }
    }
    private fun zigzag(value: Int) = (value shl 1) xor (value shr 31)
    private fun unzigzag(value: Int) = (value ushr 1) xor -(value and 1)
    private fun DataOutputStream.variable(value: Int) {
        require(value >= 0)
        var n = value
        while (n >= 128) { writeByte((n and 127) or 128); n = n ushr 7 }
        writeByte(n)
    }
    private fun DataInputStream.variable(maximum: Int): Int {
        var result = 0L
        for (i in 0..4) {
            val b = readUnsignedByte()
            result = result or ((b and 127).toLong() shl (i * 7))
            if (b and 128 == 0) {
                require(i == 0 || b != 0) { "Non-canonical variable integer." }
                require(result <= maximum) { "Compact integer exceeds limit." }
                return result.toInt()
            }
        }
        throw IllegalArgumentException("Variable integer is too long.")
    }
}
