package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EncodingBenchmarkTest {
    @Test fun realGeneratedNamesAndDoodlesKeepSmallestSupportedPacket(){
        val lines=mutableListOf("Artwork | TAG1 bytes | TAG3 bytes | selected bytes | chunks")
        val artwork=nameStyles.indices.map{"Name ${nameStyles[it]}" to NameArt.create("Subha Sai",it,.15f,true)}+doodleTitles.indices.map{"Doodle $it" to DoodleArt.create(it)}
        for((title,ink) in artwork){
            val prepared=TapPacket.prepare(ink)
            val a=CompactInkCodec.encode(prepared);val b=ColumnInkCodec.encode(prepared)
            val before=CompactInkCodec.decode(a);val after=CompactInkCodec.decode(b)
            assertEquals(before.copy(id=after.id),after)
            val selected=TapPacket.encode(ink);assertTrue(selected.size<=a.size)
            lines.add("$title | ${a.size} | ${b.size} | ${selected.size} | ${(selected.size+191)/192}")
        }
        File("build/encoding-benchmark.txt").writeText(lines.joinToString("\n"))
    }
}
