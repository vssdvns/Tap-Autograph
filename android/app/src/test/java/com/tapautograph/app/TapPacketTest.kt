package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class TapPacketTest {
    private fun ink(points:List<InkPoint>)=Autograph(aspect=1.8f,strokes=listOf(InkStroke(points)))
    @Test fun redundantPenSamplesShrinkAndPlaybackIsBoundedWithoutEditingOriginal(){
        val source=ink((0..1000).map{InkPoint(.1f+it*.0007f,.3f,it*10L)})
        val raw=CompactInkCodec.encode(source);val packet=TapPacket.encode(source);val decoded=CompactInkCodec.decode(packet)
        java.io.File("build/tap-packet-metrics.txt").writeText("Synthetic 1001-point straight stroke: ${raw.size} -> ${packet.size} bytes; original retained.")
        assertTrue(packet.size<raw.size);assertEquals(2,decoded.strokes.single().points.size)
        assertEquals(1200L,decoded.strokes.last().points.last().time)
        assertEquals(1001,source.strokes.single().points.size);assertEquals(10000L,source.strokes.last().points.last().time)
        assertEquals(.8f,decoded.strokes.single().points.last().x,.001f)
    }
    @Test fun cornersPressureAndSeparateStrokesSurvive(){
        val source=ink(listOf(InkPoint(.1f,.1f,0),InkPoint(.5f,.1f,100),InkPoint(.5f,.8f,200)))
        assertEquals(3,CompactInkCodec.decode(TapPacket.encode(source)).strokes.single().points.size)
        val pressure=ink(listOf(InkPoint(.1f,.2f,0,.2f),InkPoint(.5f,.2f,100,1.8f),InkPoint(.9f,.2f,200,.2f)))
        assertEquals(3,CompactInkCodec.decode(TapPacket.encode(pressure)).strokes.single().points.size)
        val meme=source.copy(strokes=listOf(InkStroke(listOf(InkPoint(.1f,.2f,0),InkPoint(.7f,.2f,0))),InkStroke(listOf(InkPoint(.1f,.5f,0),InkPoint(.7f,.5f,0)))))
        assertArrayEquals(CompactInkCodec.encode(meme),TapPacket.encode(meme))
    }
    @Test fun wheelChoiceFreezesAtFinalOfferAndCannotExtendWindow(){
        var time=0L;val c=TwoTapCard{time}
        val first=TapPacket.encode(ink(listOf(InkPoint(.1f,.2f,0),InkPoint(.9f,.2f,100))))
        val second=TapPacket.encode(ink(listOf(InkPoint(.1f,.4f,0),InkPoint(.9f,.4f,100))))
        val binding=ByteArray(32){it.toByte()}
        fun offer(phase:Int)=AutographApdu.command(0x30,binding+ByteBuffer.allocate(5).putInt(1234).put(phase.toByte()).array())
        c.start(first,autoShare=true);c.process(AutographApdu.SELECT);c.process(offer(1));c.process(AutographApdu.command(0x32,byteArrayOf(1)))
        time=10000;assertTrue(c.choose(second));assertEquals(35L,c.remainingSeconds())
        c.process(offer(2));assertFalse(c.choose(first))
        val metadata=c.process(AutographApdu.command(0x33,byteArrayOf()))
        assertArrayEquals(AutographApdu.digest(second),metadata.copyOfRange(17,49))
        val session=metadata.copyOfRange(1,9)
        val chunk=c.process(AutographApdu.command(0x10,session+byteArrayOf(0,0)))
        assertArrayEquals(second,chunk.copyOfRange(10,chunk.size-2));assertEquals(1f,c.progress(),0f)
        time=45000;assertEquals(0,c.state());assertTrue(c.timedOut);assertFalse(c.choose(first))
    }
}
