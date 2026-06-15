package com.example.photoframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var max: Int = 500
        set(v) { field = v; invalidate() }

    var value: Int = 0
        set(v) {
            field = v.coerceIn(0, max)
            invalidate()
        }

    var onValueChanged: ((value: Int, isDragging: Boolean) -> Unit)? = null

    private val trackWidthDp = 6f
    private val thumbRadiusDp = 11f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        style = Paint.Style.FILL
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44000000")
        style = Paint.Style.FILL
    }

    private val dp get() = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val trackW = trackWidthDp * dp
        val thumbR = thumbRadiusDp * dp
        val cx = width / 2f

        // Track extends from thumbR (top margin) to height - thumbR (bottom margin)
        val trackTop = thumbR
        val trackBottom = height - thumbR
        val trackLen = trackBottom - trackTop

        // Background track
        val rect = RectF(cx - trackW / 2, trackTop, cx + trackW / 2, trackBottom)
        trackPaint.color = Color.parseColor("#1FFFFFFF")
        canvas.drawRoundRect(rect, trackW / 2, trackW / 2, trackPaint)

        // Filled portion: from bottom up to thumb
        val fraction = if (max > 0) value.toFloat() / max else 0f
        val thumbY = trackBottom - fraction * trackLen

        if (thumbY < trackBottom) {
            val fillRect = RectF(cx - trackW / 2, thumbY, cx + trackW / 2, trackBottom)
            canvas.drawRoundRect(fillRect, trackW / 2, trackW / 2, fillPaint)
        }

        // Shadow
        canvas.drawCircle(cx, thumbY, thumbR + 2 * dp, shadowPaint)
        // Thumb
        canvas.drawCircle(cx, thumbY, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val thumbR = thumbRadiusDp * dp
        val trackBottom = height - thumbR
        val trackTop = thumbR
        val trackLen = trackBottom - trackTop

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val fraction = 1f - ((event.y - trackTop) / trackLen).coerceIn(0f, 1f)
                val newValue = (fraction * max).toInt()
                value = newValue
                onValueChanged?.invoke(value, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val fraction = 1f - ((event.y - trackTop) / trackLen).coerceIn(0f, 1f)
                val newValue = (fraction * max).toInt()
                value = newValue
                onValueChanged?.invoke(value, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val density = resources.displayMetrics.density
        val minW = (36 * density).toInt()
        val minH = (200 * density).toInt()  // sensible default so view isn't invisible in UNSPECIFIED containers
        val w = resolveSize(minW, widthMeasureSpec)
        val h = resolveSize(minH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }
}
