package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ColumnInkCodecTest {
    @Test fun quantizedValuesSurviveAcrossStrokesPressureToolsAndTiming(){
        val random=Random(42)
        repeat(50){
            var time=0L
            val ink=Autograph(aspect=2.3f,color=0x88334455L,strokes=List(1+random.nextInt(15)){
                InkStroke(List(1+random.nextInt(80)){time+=random.nextLong(0,40);InkPoint(random.nextFloat(),random.nextFloat(),time,random.nextFloat()*2,random.nextInt(5))})})
            val before=CompactInkCodec.decode(CompactInkCodec.encode(ink))
            for(predictive in listOf(false,true)){val packet=ColumnInkCodec.encode(ink,predictive);val after=CompactInkCodec.decode(packet);assertEquals(before.copy(id=after.id),after)}
        }
    }
    @Test fun syntheticVectorShrinksAndCrossPlatformFixtureIsGenerated(){
        val ink=Autograph(aspect=2f,strokes=List(40){s->InkStroke(List(60){p->InkPoint(p/60f,(s*3+p%3)/130f,(s*60+p)*5L)})})
        val old=CompactInkCodec.encode(ink);val packet=ColumnInkCodec.encode(ink)
        assertTrue("Column encoding should shrink this synthetic example",packet.size<old.size)
        val sim=AutographReaderSimulator.run(packet);assertArrayEquals(packet,sim.packet)
        File("build/column-expected.json").writeText(Json { encodeDefaults=true }.encodeToString(CompactInkCodec.decode(packet)))
        File("build/column.tai").writeBytes(packet);File("build/column-reader.json").writeText(sim.transcript)
        val predictive=ColumnInkCodec.encode(ink,true);assertTrue(predictive.size<old.size)
        assertEquals(CompactInkCodec.decode(packet).strokes,CompactInkCodec.decode(predictive).strokes)
        val predictedSim=AutographReaderSimulator.run(predictive)
        File("build/predictive.tai").writeBytes(predictive);File("build/predictive-reader.json").writeText(predictedSim.transcript)
        File("build/predictive-metrics.txt").writeText("Synthetic vector: TAG3 ${packet.size} bytes / ${(packet.size+191)/192} chunks; TAG4 ${predictive.size} bytes / ${(predictive.size+191)/192} chunks.")
        File("build/column-metrics.txt").writeText("Synthetic vector: TAG1 ${old.size} bytes / ${(old.size+191)/192} chunks; TAG3 ${packet.size} bytes / ${(packet.size+191)/192} chunks.")
    }
    @Test fun predictiveCoordinatesHandleReversalsAndRestartAtEachStroke(){
        val source=Autograph(aspect=1.5f,strokes=List(3){stroke->InkStroke(List(8){p->InkPoint(if(p%2==0)0f else 1f,if(p%2==0)1f else 0f,(stroke*8+p)*200L,p/4f,p%5)})})
        val a=CompactInkCodec.decode(CompactInkCodec.encode(source));val b=CompactInkCodec.decode(ColumnInkCodec.encode(source,true))
        assertEquals(a.copy(id=b.id),b)
    }
    @Test fun damagedAndOversizedColumnsAreRejected(){
        val source=Autograph(aspect=2f,strokes=listOf(InkStroke(listOf(InkPoint(.2f,.3f,0),InkPoint(.6f,.8f,25)))))
        val packet=ColumnInkCodec.encode(source,true)
        for(bad in listOf(packet.copyOf(13),packet+byteArrayOf(0),packet.copyOf().also{it[9]=(it[9].toInt() xor 1).toByte()},packet.copyOf().also{it[5]=0x7f},packet.copyOf().also{it[4]=2}))assertTrue(runCatching{CompactInkCodec.decode(bad)}.isFailure)
    }
}
