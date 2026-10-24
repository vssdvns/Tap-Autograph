package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AutographApduTest {
    private val packet get()=CompactInkCodec.encode(Autograph(aspect=1.8f,strokes=listOf(InkStroke(List(1000){i->InkPoint(i/999f,((i*37)%1000)/999f,i*3L,1f,2)}))))
    private fun sw(bytes:ByteArray)=AutographApdu.hex(bytes.takeLast(2).toByteArray())
    private fun meta(card:AutographApdu)=card.process(AutographApdu.SELECT).dropLast(2).toByteArray()
    private fun read(m:ByteArray,index:Int)=AutographApdu.command(0x10,m.copyOfRange(1,9)+byteArrayOf((index ushr 8).toByte(),index.toByte()))
    private fun finish(m:ByteArray)=AutographApdu.command(0x20,m.copyOfRange(1,9)+m.copyOfRange(17,49))
    @Test fun unarmedNeverReleasesMetadataOrInk() {
        val card=AutographApdu();assertEquals("6985",sw(card.process(AutographApdu.SELECT)))
        assertEquals("6985",sw(card.process(AutographApdu.command(0x10,ByteArray(10)))))
    }
    @Test fun metadataAndChunkBoundsMatchPacket() {
        val card=AutographApdu();val p=packet;card.arm(p);val m=meta(card)
        assertEquals(49,m.size);assertEquals(1,m[0].toInt());assertArrayEquals(AutographApdu.digest(p),m.copyOfRange(17,49))
        assertArrayEquals(p.take(192).toByteArray(),card.process(read(m,0)).drop(10).dropLast(2).toByteArray())
        assertEquals("6B00",sw(card.process(read(m,65535))))
    }
    @Test fun duplicateChunksAndReconnectKeepSameSnapshot() {
        val card=AutographApdu();card.arm(packet);val m=meta(card);val first=card.process(read(m,0))
        assertArrayEquals(first,card.process(read(m,0)));card.deactivate()
        assertEquals("6985",sw(card.process(read(m,0))))
        assertArrayEquals(m,meta(card));assertArrayEquals(first,card.process(read(m,0)))
    }
    @Test fun expiryDisarmsEvenDuringTransfer() {
        var clock=0L;val card=AutographApdu{clock};card.arm(packet);val m=meta(card)
        clock=60_000;assertEquals("6985",sw(card.process(read(m,0))));assertEquals("Sharing expired",card.status())
    }
    @Test fun staleSessionAndWrongHashCannotFinish() {
        val card=AutographApdu();card.arm(packet);val old=meta(card);card.arm(packet);val m=meta(card)
        assertEquals("6985",sw(card.process(read(old,0))))
        assertEquals("6985",sw(card.process(finish(m).also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() })))
    }
    @Test fun finishRequiresAllChunksThenErasesPacket() {
        val card=AutographApdu();val p=packet;card.arm(p);val m=meta(card)
        assertEquals("6985",sw(card.process(finish(m))))
        for(i in 0 until (p.size+191)/192)assertEquals("9000",sw(card.process(read(m,i))))
        assertEquals("9000",sw(card.process(finish(m))));assertEquals("9000",sw(card.process(finish(m))))
        assertEquals("6985",sw(card.process(read(m,0))));assertEquals("6985",sw(card.process(AutographApdu.SELECT)))
    }
    @Test fun malformedCommandsDoNotCrashOrReturnInk() {
        val card=AutographApdu();card.arm(packet);meta(card)
        for(n in 0..4)assertEquals("6700",sw(card.process(ByteArray(n))))
        assertEquals("6700",sw(card.process(AutographApdu.unhex("8010000000"))))
        assertEquals("6D00",sw(card.process(AutographApdu.unhex("807F000000"))))
        assertEquals("6E00",sw(card.process(AutographApdu.unhex("0010000000"))))
        assertEquals("6A86",sw(card.process(AutographApdu.unhex("8010010000"))))
    }
    @Test fun stopClearsGrantAndNewArmResetsProgress() {
        val card=AutographApdu();card.arm(packet);val m=meta(card);card.process(read(m,0));card.disarm()
        assertEquals("6985",sw(card.process(read(m,0))));assertEquals(0,card.remainingSeconds())
        card.arm(packet);assertFalse(m.contentEquals(meta(card)))
    }
    @Test fun sourceArrayIsNotChangedBySharing() {
        val p=packet;val copy=p.copyOf();val card=AutographApdu();card.arm(p);card.disarm();assertArrayEquals(copy,p)
    }
    @Test fun manyChunkPacketSurvivesSimulation() {
        val random=java.util.Random(7)
        val a=Autograph(aspect=1.8f,strokes=listOf(InkStroke(List(3500){i->InkPoint(random.nextFloat(),random.nextFloat(),i*4L,.5f+random.nextFloat(),2)})))
        val p=CompactInkCodec.encode(a);val result=AutographReaderSimulator.run(p)
        assertTrue(result.chunks>26);assertArrayEquals(p,result.packet)
        val folder=File("build/hce-fixtures").apply{mkdirs()}
        File(folder,"large.json").writeText(result.transcript);File(folder,"large.tai").writeBytes(p)
    }
    @Test fun simulationProducesInteropTranscriptWithRetryAndReconnect() {
        val p=packet;val result=AutographReaderSimulator.run(p);assertArrayEquals(p,result.packet)
        assertTrue(result.transcript.contains("\"protocol\":\"TAP1\""));assertTrue(result.transcript.contains("\"simulated\":true"))
        val folder=File("build/hce-fixtures").apply{mkdirs()}
        File(folder,"reader.json").writeText(result.transcript);File(folder,"reader.tai").writeBytes(p)
    }
}
