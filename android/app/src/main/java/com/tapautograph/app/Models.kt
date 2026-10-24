package com.tapautograph.app

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Serializable data class InkPoint(val x: Float, val y: Float, val time: Long, val pressure: Float = 1f, val tool: Int = 0)
@Serializable data class InkStroke(val points: List<InkPoint>)
@Serializable data class Autograph(val schema: Int = 1, val id: String = UUID.randomUUID().toString(), val aspect: Float, val strokes: List<InkStroke>, val color: Long = 0xFF18365FL, val width: Float = 0.006f)
@Serializable data class Preferences(val theme: String = "dark", val reducedMotion: Boolean = false, val speed: Float = 1f)
@Serializable data class Transaction(val id: String, val time: Long, val tip: Int, val card: Int, val autographId: String? = null, val subtotal: Int = 950, val tax: Int = 83, val merchant: String = "Harbor Coffee", val items: List<ReceiptItem> = listOf(ReceiptItem("latte", "Iced latte", 1, 550), ReceiptItem("croissant", "Croissant", 1, 400)))
@Serializable data class SavedData(val autograph: Autograph? = null, val preferences: Preferences = Preferences(), val transactions: List<Transaction> = emptyList(), val gallery: List<StudioDesign> = emptyList(), val receiptDesignId: String? = null, val receiptEnabled: Boolean = false)
data class DemoCard(val title: String, val digits: String, val owner: String)
val demoCards = listOf(DemoCard("Everyday", "2048", "TAP AUTOGRAPH"), DemoCard("Weekend", "7310", "TAP AUTOGRAPH"))
enum class CheckoutStage { REVIEW, PROCESSING, TIP, AUTOGRAPH, PLAYBACK, RECEIPT }
data class Checkout(val id: String = UUID.randomUUID().toString(), val card: Int = 0, val tip: Int = 0, val stage: CheckoutStage = CheckoutStage.REVIEW, val sharedId: String? = null)
fun money(cents: Int): String = NumberFormat.getCurrencyInstance(Locale.US).format(cents / 100.0)
fun percentageTip(percent: Int): Int = BigDecimal(950).multiply(BigDecimal(percent)).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).intValueExact()
fun customTip(raw: String): Int? = runCatching {
    if (!Regex("\\d{1,3}(\\.\\d{0,2})?").matches(raw)) return null
    BigDecimal(raw).movePointRight(2).intValueExact().takeIf { it in 0..10000 }
}.getOrNull()
data class Fit(val scale: Float, val left: Float, val top: Float)
fun fit(aspect: Float, width: Float, height: Float): Fit {
    val scale = minOf(width / aspect, height)
    return Fit(scale, (width - aspect * scale) / 2, (height - scale) / 2)
}
/** Rebase timestamps and cap idle gaps without modifying saved source data. */
fun playbackStrokes(strokes: List<InkStroke>): List<InkStroke> {
    var sourcePrevious: Long? = null
    var clock = 0L
    return strokes.map { stroke ->
        InkStroke(stroke.points.mapIndexed { index, p ->
            sourcePrevious?.let { clock += (p.time - it).coerceIn(0, if (index == 0) 350L else 100L) }
            sourcePrevious = p.time
            p.copy(time = clock)
        })
    }
}
fun appendTransaction(existing: List<Transaction>, value: Transaction): List<Transaction> =
    if (existing.any { it.id == value.id }) existing else (listOf(value) + existing).take(100)
fun Autograph.valid(): Boolean = schema == 1 && aspect.isFinite() && aspect > 0 && aspect < 20 &&
    width.isFinite() && width in .001f..0.04f && strokes.isNotEmpty() && strokes.size <= 1000 &&
    strokes.sumOf { it.points.size } <= 100000 && strokes.all { s -> s.points.isNotEmpty() && s.points.all { p ->
        p.x.isFinite() && p.y.isFinite() && p.x in 0f..1f && p.y in 0f..1f && p.time >= 0 && p.pressure.isFinite()
    }}

fun playbackPosition(elapsedNanos: Long, speed: Float): Long =
    (elapsedNanos.coerceAtLeast(0) / 1_000_000.0 * speed.coerceIn(.5f, 2f)).toLong()
