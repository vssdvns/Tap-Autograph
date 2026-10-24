package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CompactInkCodecTest {
    private fun autograph() = Autograph(id="test-source", aspect=1.8f, strokes=listOf(
        InkStroke(listOf(InkPoint(.1f,.2f,150,.4f,2),InkPoint(.3f,.7f,187,1.2f,2),InkPoint(.2f,.4f,230,.8f,2))),
        InkStroke(listOf(InkPoint(.8f,.3f,15000,1f,1)))
    ))
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected invalid payload rejection") } catch (_: IllegalArgumentException) { }
    }
    @Test fun roundTripPreservesStrokesTimingToolAndBoundedGeometry() {
        val source=autograph(); val decoded=CompactInkCodec.decode(CompactInkCodec.encode(source))
        assertEquals(source.strokes.map { it.points.size }, decoded.strokes.map { it.points.size })
        assertEquals(source.color,decoded.color)
        assertEquals(source.aspect,decoded.aspect,1f/1024)
        assertEquals(source.width,decoded.width,1f/65535)
        for ((a,b) in source.strokes.flatMap { it.points }.zip(decoded.strokes.flatMap { it.points })) {
            assertEquals(a.x,b.x,.5f/4095 + 1e-7f);assertEquals(a.y,b.y,.5f/4095 + 1e-7f)
            assertEquals(a.time-150,b.time);assertEquals(a.tool,b.tool);assertEquals(a.pressure,b.pressure,.5001f/127.5f)
        }
    }
    @Test fun extremeValidAspectAndWidthRoundTrip() {
        for (aspect in listOf(.00001f, 19.99999f)) for (width in listOf(.001f,.04f)) {
            val decoded=CompactInkCodec.decode(CompactInkCodec.encode(autograph().copy(aspect=aspect,width=width)))
            assertTrue(decoded.valid());assertEquals(aspect,decoded.aspect,1f/1024)
        }
    }
    @Test fun sameInputProducesDeterministicBytes() { assertArrayEquals(CompactInkCodec.encode(autograph()),CompactInkCodec.encode(autograph())) }
    @Test fun dotAndUncompressedEnvelopeWork() {
        val a=Autograph(aspect=1.8f, strokes=listOf(InkStroke(listOf(InkPoint(.5f,.5f,123)))))
        val packet=CompactInkCodec.encode(a,false)
        assertEquals(0,packet[4].toInt());assertEquals(1,CompactInkCodec.decode(packet).strokes.single().points.size)
    }
    @Test fun denseInkUsesZlibAndIsSmallerThanJson() {
        val a=dense();val packet=CompactInkCodec.encode(a)
        assertEquals(1,packet[4].toInt());assertTrue(packet.size<Json.encodeToString(a).length)
        assertEquals(1000,CompactInkCodec.decode(packet).strokes.single().points.size)
    }
    @Test fun invalidVersionFlagsAndTruncationAreRejected() {
        val packet=CompactInkCodec.encode(autograph(),false)
        rejects { CompactInkCodec.decode(packet.copyOf().also { it[0]=0 }) }
        rejects { CompactInkCodec.decode(packet.copyOf().also { it[4]=2 }) }
        for (n in packet.indices) rejects { CompactInkCodec.decode(packet.copyOf(n)) }
    }
    @Test fun corruptedBodyAndTrailingDataAreRejected() {
        val packet=CompactInkCodec.encode(autograph(),false)
        rejects { CompactInkCodec.decode(packet.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }) }
        rejects { CompactInkCodec.decode(packet+byteArrayOf(0)) }
        rejects { CompactInkCodec.decode(CompactInkCodec.encode(dense())+byteArrayOf(0)) }
    }
    @Test fun oversizedLengthIsRejectedBeforeInflation() {
        val packet=CompactInkCodec.encode(autograph())
        rejects { CompactInkCodec.decode(packet.copyOf().also { it[5]=0x7f }) }
        rejects { CompactInkCodec.decode(ByteArray(CompactInkCodec.MAX_PACKET+1)) }
    }
    @Test fun decreasingTimesUnsupportedToolsAndTooManyPointsAreRejected() {
        val a=autograph()
        rejects { CompactInkCodec.encode(a.copy(strokes=listOf(InkStroke(listOf(InkPoint(0f,0f,100),InkPoint(1f,1f,99)))))) }
        rejects { CompactInkCodec.encode(a.copy(strokes=listOf(InkStroke(listOf(InkPoint(0f,0f,0,tool=8)))))) }
        rejects { CompactInkCodec.encode(a.copy(strokes=listOf(InkStroke(List(30001){InkPoint(.5f,.5f,it.toLong())})))) }
    }
    @Test fun malformedCountsAndCoordinatesAreRejectedEvenWithValidCrc() {
        val bytes=CompactInkCodec.encode(autograph(),false)
        val body=bytes.copyOfRange(13,bytes.size)
        // Body has 8 fixed bytes then stroke count; zero strokes is not valid.
        rejects { CompactInkCodec.decode(envelope(body.copyOf().also { it[8]=0 })) }
        // A continued varint that overflows the allowed stroke count.
        rejects { CompactInkCodec.decode(envelope(body.copyOf().also { it[8]=0xff.toByte();it[9]=0x7f })) }
    }
    @Test fun validCrcDoesNotAuthenticateInput() {
        val bytes=CompactInkCodec.encode(autograph(),false);val body=bytes.copyOfRange(13,bytes.size)
        body[4]=0x7f // A changed ink color with a recomputed checksum is accepted.
        val changed=CompactInkCodec.decode(envelope(body));assertNotEquals(autograph().color,changed.color)
    }
    @Test fun writesCrossLanguageFixturesAndMeasurements() {
        val folder=File("build/codec-fixtures").apply { mkdirs() }
        val json=Json { encodeDefaults=true }
        for ((name,a) in listOf("short" to autograph(),"dense" to dense())) {
            val packet=CompactInkCodec.encode(a);val decoded=CompactInkCodec.decode(packet)
            File(folder,"$name.tai").writeBytes(packet)
            File(folder,"$name.decoded.json").writeText(json.encodeToString(decoded))
            File(folder,"$name.source.json").writeText(json.encodeToString(a))
            println("$name: JSON=${json.encodeToString(a).toByteArray().size} compact=${packet.size} points=${a.strokes.sumOf { it.points.size }}")
        }
    }
    private fun dense()=Autograph(id="dense-test",aspect=1.8f,strokes=listOf(InkStroke(List(1000){i->InkPoint(i/999f,(.5+.25*sin(i/100.0)).toFloat(),150+i*3L,1f,2)})))
    private fun envelope(body:ByteArray):ByteArray=ByteArrayOutputStream().also { buffer->DataOutputStream(buffer).use { out->
        out.write(byteArrayOf(0x54,0x41,0x47,0x31));out.writeByte(0);out.writeInt(body.size);out.writeInt(CRC32().also { it.update(body) }.value.toInt());out.write(body)
    }}.toByteArray()
}
