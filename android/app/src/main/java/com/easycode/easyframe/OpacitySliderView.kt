package com.easycode.easyframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Vertical transparency slider (0..1). Track shows a checkerboard with a transparent→solid
// gradient of the current frame colour; top = fully opaque, bottom = fully transparent.
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
    private val trackW = 10f * dp
    private val thumbR = 9f * dp

    private val checkPaint = Paint()
    private val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAFA")
        setShadowLayer(6f, 0f, 2f, Color.argb(128, 0, 0, 0))
    }
    private var gradientDirty = true

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
        checkPaint.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { gradientDirty = true }

    private fun ensureGradient() {
        if (!gradientDirty || height == 0) return
        val transp = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
        // top (thumbR) = opaque colour, bottom (height-thumbR) = transparent
        gradPaint.shader = LinearGradient(
            0f, thumbR, 0f, height - thumbR, color, transp, Shader.TileMode.CLAMP
        )
        gradientDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        ensureGradient()
        val cx = width / 2f
        val r = trackW / 2f
        val top = thumbR; val bottom = height - thumbR
        // Checkerboard, then colour gradient over it (vertical track)
        canvas.drawRoundRect(cx - r, top, cx + r, bottom, r, r, checkPaint)
        canvas.drawRoundRect(cx - r, top, cx + r, bottom, r, r, gradPaint)

        val usableH = height - thumbR * 2
        val thumbY = (thumbR + (1f - value) * usableH).coerceIn(thumbR, height - thumbR)
        canvas.drawCircle(cx, thumbY, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val usableH = height - thumbR * 2
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                value = (1f - (event.y - thumbR) / usableH).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, true)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                value = (1f - (event.y - thumbR) / usableH).coerceIn(0f, 1f)
                onValueChanged?.invoke(value, false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
