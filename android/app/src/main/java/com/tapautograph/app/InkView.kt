package com.tapautograph.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/** Shared native renderer: no bitmap flattening and no Compose recomposition per sample. */
class InkView(context: Context) : View(context) {
    var autograph: Autograph? = null
        set(value) { field = value; invalidate() }
    var thumbnail = false
        set(value){field=value;invalidate()}
    var editable = false
    var elapsed = Long.MAX_VALUE
        set(value) { field = value; invalidate() }
    var onStroke: (InkStroke) -> Unit = {}
    var onAspect: (Float) -> Unit = {}
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
    private var activePointer = -1
    private var activePoints = mutableListOf<InkPoint>()
    private var startedAt = 0L
    private var sourceStart = 0L
    private var lastLiftAt = 0L
    init { contentDescription = "Autograph drawing area. Use S Pen or finger."; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val a = autograph ?: return
        val margin = (if(thumbnail)2f else 18f) * resources.displayMetrics.density
        val f = fit(a.aspect, max(1f, width - 2 * margin), max(1f, height - 2 * margin))
        canvas.save(); canvas.translate(f.left + margin, f.top + margin)
        if (editable) {
            paint.color = Color.rgb(211, 220, 233); paint.strokeWidth = resources.displayMetrics.density
            canvas.drawLine(0f, f.scale * .75f, a.aspect * f.scale, f.scale * .75f, paint)
        }
        paint.color = a.color.toInt()
        for (stroke in a.strokes) drawPoints(canvas, a, f, stroke.points)
        if (activePoints.isNotEmpty()) drawPoints(canvas, a, f, activePoints)
        canvas.restore()
    }
    private fun drawPoints(canvas: Canvas, a: Autograph, f: Fit, points: List<InkPoint>) {
            if (points.isEmpty() || points.first().time > elapsed) return
            val first = points.first()
            if (points.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(first.x * a.aspect * f.scale, first.y * f.scale, a.width * f.scale / 2, paint)
                paint.style = Paint.Style.STROKE
            }
            for (i in 1 until points.size) {
                val previous = points[i - 1]; val point = points[i]
                if (previous.time > elapsed) break
                val fraction = if (point.time <= elapsed || point.time == previous.time) 1f else
                    ((elapsed - previous.time).toFloat() / (point.time - previous.time)).coerceIn(0f, 1f)
                val pressure = ((previous.pressure + point.pressure) / 2).coerceIn(.25f, 1.2f)
                paint.strokeWidth = a.width * f.scale * (.7f + .3f * pressure)
                if (previous.x == point.x && previous.y == point.y) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(previous.x * a.aspect * f.scale, previous.y * f.scale, paint.strokeWidth / 2, paint)
                    paint.style = Paint.Style.STROKE
                } else canvas.drawLine(previous.x * a.aspect * f.scale, previous.y * f.scale,
                    (previous.x + (point.x - previous.x) * fraction) * a.aspect * f.scale,
                    (previous.y + (point.y - previous.y) * fraction) * f.scale, paint)
                if (fraction < 1) break
            }
    }
    private fun sample(event: MotionEvent, index: Int, history: Int? = null) {
        val a = autograph ?: return
        val margin = (if(thumbnail)2f else 18f) * resources.displayMetrics.density
        val f = fit(a.aspect, max(1f, width - 2 * margin), max(1f, height - 2 * margin))
        val x = history?.let { event.getHistoricalX(index, it) } ?: event.getX(index)
        val y = history?.let { event.getHistoricalY(index, it) } ?: event.getY(index)
        val t = history?.let { event.getHistoricalEventTime(it) } ?: event.eventTime
        val pressure = history?.let { event.getHistoricalPressure(index, it) } ?: event.getPressure(index)
        activePoints.add(InkPoint(((x - margin - f.left) / (a.aspect * f.scale)).coerceIn(0f, 1f),
            ((y - margin - f.top) / f.scale).coerceIn(0f, 1f),
            sourceStart + (t - startedAt).coerceAtLeast(0), if (pressure.isFinite()) pressure.coerceIn(.25f, 1.2f) else 1f, event.getToolType(index)))
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editable) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) return false
                activePointer = event.getPointerId(0)
                activePoints = mutableListOf()
                startedAt = event.eventTime
                sourceStart = (autograph?.strokes?.lastOrNull()?.points?.lastOrNull()?.time ?: 0L) +
                    if (lastLiftAt > 0) (event.eventTime - lastLiftAt).coerceAtLeast(0) else 150L
                parent?.requestDisallowInterceptTouchEvent(true)
                sample(event, 0)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index >= 0) {
                    for (history in 0 until event.historySize) sample(event, index, history)
                    sample(event, index)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) {
                    sample(event, event.actionIndex)
                    if (activePoints.isNotEmpty()) onStroke(InkStroke(activePoints.toList()))
                    activePoints.clear(); activePointer = -1; lastLiftAt = event.eventTime
                    parent?.requestDisallowInterceptTouchEvent(false)
                    performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                activePoints.clear(); activePointer = -1
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
