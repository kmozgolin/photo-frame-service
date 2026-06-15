package com.example.photoframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Horizontal transparency slider (0..1). Track shows a checkerboard with a transparent→solid
// gradient of the current frame color, so the thumb position reads as "how opaque".
class OpacitySliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var value = 1f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }

    var color = Color.WHITE
        set(c) { field = c; gradientDirty = true; invalidate() }

    // (value, isDragging)
    var onValueChanged: ((Float, Boolean) -> Unit)? = null

    private val dp = context.resources.displayMetrics.density
    private val trackH = 10f * dp
    private val thumbR = 9f * dp

    private val checkPaint = Paint()
    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        setShadowLayer(6f, 0f, 2f, Color.argb(128, 0, 0, 0))
    }
    private var gradientDirty = true
    private var checkerShader: BitmapShader? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        buildChecker()
    }

    private fun buildChecker() {
        val s = (5f * dp).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val light = Color.parseColor("#3A3A42"); val dark = Color.parseColor("#26262C")
        c.drawColor(dark)
        val p = Paint().apply { color = light }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        c.drawRect(s.toFloat(), s.toFloat(), (s * 2).toFloat(), (s * 2).toFloat(), p)
        checkerShader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        checkPaint.shader = checkerShader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { gradientDirty = true }

    private fun ensureGradient() {
        if (!gradientDirty || width == 0) return
        val transp = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        gradPaint.shader = LinearGradient(
            thumbR, 0f, width - thumbR, 0f, transp, color, Shader.TileMode.CLAMP
        )
        gradientDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        ensureGradient()
        val cy = height / 2f
        val r = trackH / 2f
        val l = thumbR; val rt = width - thumbR
        // Checkerboard, then color gradient over it
        canvas.drawRoundRect(l, cy - r, rt, cy + r, r, r, checkPaint)
        canvas.drawRoundRect(l, cy - r, rt, cy + r, r, r, gradPaint)

        val usableW = width - thumbR * 2
        val thumbX = (thumbR + value * usableW).coerceIn(thumbR, width - thumbR)
        canvas.drawCircle(thumbX, cy, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val usableW = width - thumbR * 2
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                value = ((event.x - thumbR) / usableW).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                value = ((event.x - thumbR) / usableW).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
