package com.example.photoframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Horizontal brightness slider with gradient track (black → endColor)
// Matches design: linear-gradient(90deg, #000, frameColor) track + white circle thumb
class BrightnessSliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var value = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    var endColor = Color.WHITE
        set(c) { field = c; gradientDirty = true; invalidate() }

    // (value, isDragging) — matches VerticalSeekBar convention so callers know drag-complete
    var onValueChanged: ((Float, Boolean) -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val trackH = 8f * dp
    private val thumbR = 9f * dp

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        setShadowLayer(6f, 0f, 2f, Color.argb(128, 0, 0, 0))
    }
    private var gradientDirty = true

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        gradientDirty = true
    }

    private fun ensureGradient() {
        if (!gradientDirty || width == 0) return
        trackPaint.shader = LinearGradient(
            thumbR, 0f, width - thumbR, 0f,
            Color.BLACK, endColor,
            Shader.TileMode.CLAMP
        )
        gradientDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        ensureGradient()
        val cy = height / 2f
        val usableW = width - thumbR * 2

        // Track
        canvas.drawRoundRect(
            thumbR, cy - trackH / 2, width - thumbR, cy + trackH / 2,
            trackH / 2, trackH / 2, trackPaint
        )

        // Thumb
        val thumbX = (thumbR + value * usableW).coerceIn(thumbR, width - thumbR)
        canvas.drawCircle(thumbX, cy, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val usableW = width - thumbR * 2
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                value = ((event.x - thumbR) / usableW).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                value = ((event.x - thumbR) / usableW).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
