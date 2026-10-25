package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RasterInkCodecTest {
    private fun art():Autograph {
        val w=128;val h=96;val strokes=mutableListOf<InkStroke>()
        for(y in 12..83){
            val runs=if(y in 25..35 || y in 58..63)listOf(20 to 36,52 to 70,86 to 107) else listOf(20 to 107)
            for((lo,hi) in runs)strokes.add(InkStroke(listOf(InkPoint((lo+.5f)/w,(y+.5f)/h,0),InkPoint((hi+.5f)/w,(y+.5f)/h,0))))
        }
        return Autograph(aspect=w.toFloat()/h,width=.95f/h,color=0xFF18365FL,strokes=strokes)
    }
    @Test fun bitmapRoundTripRetainsEveryRunAndShrinksMeme(){
        val ink=art();val old=CompactInkCodec.encode(ink);val packet=TapPacket.encode(ink)
        assertEquals("TAG2",String(packet,0,4));assertTrue(packet.size<old.size)
        val decoded=CompactInkCodec.decode(packet)
        assertEquals(ink.strokes,decoded.strokes);assertEquals(ink.aspect,decoded.aspect,0f);assertEquals(ink.color,decoded.color);assertEquals(ink.width,decoded.width,.00002f)
        val sim=AutographReaderSimulator.run(packet);assertArrayEquals(packet,sim.packet)
        File("build/raster.tai").writeBytes(packet);File("build/raster-reader.json").writeText(sim.transcript)
        File("build/raster-metrics.txt").writeText("Synthetic 128x96 meme: TAG1 ${old.size} bytes / ${(old.size+191)/192} chunks; TAG2 ${packet.size} bytes / ${(packet.size+191)/192} chunks.")
    }
    @Test fun unsupportedArtworkKeepsVectorPacket(){
        val ink=art();assertNull(RasterInkCodec.encodeOrNull(ink.copy(strokes=ink.strokes.map{stroke->InkStroke(stroke.points.map{it.copy(time=1)})})))
        assertNull(RasterInkCodec.encodeOrNull(ink.copy(strokes=ink.strokes.map{stroke->InkStroke(stroke.points.map{it.copy(pressure=.5f)})})))
        val source=Autograph(aspect=1.8f,strokes=listOf(InkStroke(listOf(InkPoint(.1f,.2f,0),InkPoint(.8f,.7f,200)))))
        assertTrue(String(TapPacket.encode(source),0,4) in listOf("TAG1","TAG3","TAG4"))
    }
    @Test fun corruptionAndTrailingBytesAreRejected(){
        val packet=requireNotNull(RasterInkCodec.encodeOrNull(art()))
        for(bad in listOf(packet.copyOf().also{it[9]=(it[9].toInt() xor 1).toByte()},packet+byteArrayOf(0),packet.copyOf(13),packet.copyOf().also{it[5]=0x7f})){
            assertTrue(runCatching{CompactInkCodec.decode(bad)}.isFailure)
        }
    }
    @Test fun thumbnailUsesInkBoundsWithoutChangingOriginal(){
        val source=Autograph(aspect=2f,width=.006f,strokes=listOf(InkStroke(listOf(InkPoint(.45f,.45f,0),InkPoint(.55f,.55f,10)))))
        val cropped=thumbnailInk(source);val p=cropped.strokes.single().points
        assertTrue(p.first().x<.1f&&p.last().x>.9f);assertTrue(p.first().y<.1f&&p.last().y>.9f)
        assertEquals(.45f,source.strokes.single().points.first().x,0f)
    }
}
