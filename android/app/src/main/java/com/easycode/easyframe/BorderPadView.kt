package com.easycode.easyframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

// 2D input pad for border thickness — replaces the two vertical sliders + "linked" toggle.
//   X axis  → sides     (left/right thickness, adds to width)
//   Y axis  → topBottom (top/bottom thickness, adds to height); UP = more
// Origin is bottom-left (no border). Dragging near the equal-frame diagonal magnetically snaps
// the two values together (replaces the old boolean "linked").
class BorderPadView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var maxValue = 500
        set(v) { field = v.coerceAtLeast(1); invalidate() }

    var sides = 30
        set(v) { field = v.coerceIn(0, maxValue); invalidate() }

    var topBottom = 30
        set(v) { field = v.coerceIn(0, maxValue); invalidate() }

    // (sides, topBottom, isDragging) — isDragging=false on finger lift
    var onValuesChanged: ((Int, Int, Boolean) -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val inset = 16f * dp          // keeps the handle off the edges
    private val handleR = 11f * dp

    // Snap thresholds scaled from the PRD (SNAP=8, highlight≈2.5 at MAX=120) to our maxValue.
    // Snap is +20% stickier than the PRD baseline (8 → 9.6) per user preference.
    private val snap get() = (maxValue * 9.6f / 120f).roundToInt().coerceAtLeast(1)
    private val highlightThresh get() = (maxValue * 2.5f / 120f).coerceAtLeast(1f)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#14FFFFFF")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#29FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#12FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val diagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3DFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        pathEffect = DashPathEffect(floatArrayOf(6f * dp, 5f * dp), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1FFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
        setShadowLayer(6f, 0f, 2f, Color.argb(120, 0, 0, 0))
    }
    private val handleInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#16161A")
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        textSize = 11f * dp
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // for handle shadow
    }

    private fun usableW() = width - inset * 2
    private fun usableH() = height - inset * 2

    private fun valueToX(v: Int) = inset + (v.toFloat() / maxValue) * usableW()
    private fun valueToY(v: Int) = inset + (1f - v.toFloat() / maxValue) * usableH()

    override fun onDraw(canvas: Canvas) {
        val left = inset; val top = inset
        val right = width - inset; val bottom = height - inset
        val radius = 14f * dp

        // Panel
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, bgPaint)
        canvas.drawRoundRect(
            0.5f * dp, 0.5f * dp, width - 0.5f * dp, height - 0.5f * dp, radius, radius, borderPaint
        )

        // Thirds grid (orientation aid only)
        val gx1 = left + usableW() / 3f; val gx2 = left + usableW() * 2f / 3f
        val gy1 = top + usableH() / 3f;  val gy2 = top + usableH() * 2f / 3f
        canvas.drawLine(gx1, top, gx1, bottom, gridPaint)
        canvas.drawLine(gx2, top, gx2, bottom, gridPaint)
        canvas.drawLine(left, gy1, right, gy1, gridPaint)
        canvas.drawLine(left, gy2, right, gy2, gridPaint)

        // Equal-frame diagonal (bottom-left → top-right). Brighter when values are nearly equal.
        val near = abs(sides - topBottom) < highlightThresh
        diagPaint.color = if (near) Color.parseColor("#99FFFFFF") else Color.parseColor("#3DFFFFFF")
        diagPaint.strokeWidth = (if (near) 2f else 1.5f) * dp
        canvas.drawLine(left, bottom, right, top, diagPaint)

        // Cross-hair guides from axes to handle
        val hx = valueToX(sides); val hy = valueToY(topBottom)
        canvas.drawLine(left, hy, hx, hy, guidePaint)
        canvas.drawLine(hx, hy, hx, bottom, guidePaint)

        // Handle
        canvas.drawCircle(hx, hy, handleR, handleFill)
        canvas.drawCircle(hx, hy, handleR * 0.42f, handleInner)

        // Axis-coordinate readouts: ↕ vertical (top/bottom) at top-left, ↔ horizontal (sides) at bottom-right.
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("↕ $topBottom", left + 6f * dp, top + 15f * dp, labelPaint)
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("↔ $sides", right - 6f * dp, bottom - 7f * dp, labelPaint)
    }

    private fun applyFromPointer(px: Float, py: Float, dragging: Boolean) {
        val lx = (px - inset).coerceIn(0f, usableW())
        val ly = (py - inset).coerceIn(0f, usableH())
        var s = (lx / usableW() * maxValue).roundToInt()
        var tb = ((1f - ly / usableH()) * maxValue).roundToInt()
        // Magnetic diagonal — only on the drag path, never on numeric field entry.
        if (abs(s - tb) <= snap) {
            val v = ((s + tb) / 2f).roundToInt()
            s = v; tb = v
        }
        sides = s; topBottom = tb
        onValuesChanged?.invoke(sides, topBottom, dragging)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                applyFromPointer(event.x, event.y, true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                applyFromPointer(event.x, event.y, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onValuesChanged?.invoke(sides, topBottom, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultH = (190 * dp).toInt()
        val w = resolveSize((240 * dp).toInt(), widthMeasureSpec)
        val h = resolveSize(defaultH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
