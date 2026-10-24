package com.tapautograph.app

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ModelsTest {
    @Test fun moneyIsRoundedOnSubtotal() {
        assertEquals(143, percentageTip(15)); assertEquals(190, percentageTip(20))
        assertEquals(0, percentageTip(0)); assertEquals(1223, 1033 + percentageTip(20))
    }
    @Test fun customInputIsBoundedAndExact() {
        assertEquals(125, customTip("1.25")); assertEquals(10000, customTip("100"))
        listOf("-1", "1.234", "100.01", "NaN", "", "1e2").forEach { assertNull(customTip(it)) }
    }
    @Test fun fitPreservesAspect() {
        val f = fit(2f, 300f, 300f)
        assertEquals(150f, f.scale); assertEquals(0f, f.left); assertEquals(75f, f.top)
    }
    @Test fun playbackCapsIdleAndRebases() {
        val raw = listOf(InkStroke(listOf(InkPoint(0f,0f,1000), InkPoint(1f,1f,1050))), InkStroke(listOf(InkPoint(.5f,.5f,10000))))
        val playback = playbackStrokes(raw)
        assertEquals(0L, playback[0].points[0].time)
        assertEquals(50L, playback[0].points[1].time)
        assertEquals(400L, playback[1].points[0].time)
        assertEquals(10000L, raw[1].points[0].time)
    }
    @Test fun playbackSpeedChangesTimeNotSource() {
        assertEquals(750L, playbackPosition(1_000_000_000L, .75f))
        assertEquals(1500L, playbackPosition(1_000_000_000L, 1.5f))
        assertEquals(0L, playbackPosition(-1L, 1f))
    }
    @Test fun strokesRoundTrip() {
        val a = Autograph(aspect=2f, strokes=listOf(InkStroke(listOf(InkPoint(.2f,.3f,10,.8f,2)))))
        assertEquals(a, Json.decodeFromString<Autograph>(Json.encodeToString(a))); assertTrue(a.valid())
        assertFalse(a.copy(aspect=Float.NaN).valid())
    }
    @Test fun checkoutHistoryIsIdempotent() {
        val t = Transaction("session", 100, 143, 0)
        val once = appendTransaction(emptyList(), t)
        assertEquals(once, appendTransaction(once, t))
    }
}
