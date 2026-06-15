package com.example.photoframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class ColorWheelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var hue = 0f
        private set
    var saturation = 1f
        private set

    var onColorChanged: ((hue: Float, saturation: Float) -> Unit)? = null

    private var wheelBitmap: Bitmap? = null
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private val selectorOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.BLACK
    }
    private val selectorInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        radius  = minOf(w, h) / 2f - 6f
        buildWheelBitmapAsync(w, h)
    }

    // Builds the wheel pixel-by-pixel on a background thread, then posts result to UI.
    private fun buildWheelBitmapAsync(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val cx = centerX; val cy = centerY; val r = radius
        Thread {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(w * h)
            val hsv = floatArrayOf(0f, 0f, 1f) // V=1 — no black-overlay bug
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val dx = x - cx; val dy = y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist <= r) {
                        hsv[0] = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()
                        hsv[1] = (dist / r).coerceIn(0f, 1f)
                        pixels[y * w + x] = Color.HSVToColor(hsv)
                    }
                }
            }
            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
            post {
                wheelBitmap?.recycle()
                wheelBitmap = bmp
                invalidate()
            }
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = wheelBitmap ?: return
        canvas.drawBitmap(bmp, 0f, 0f, null)

        val angleRad = Math.toRadians((hue - 90.0 + 360.0) % 360.0)
        val sx = centerX + (saturation * radius * cos(angleRad)).toFloat()
        val sy = centerY + (saturation * radius * sin(angleRad)).toFloat()
        canvas.drawCircle(sx, sy, 14f, selectorOuter)
        canvas.drawCircle(sx, sy, 14f, selectorInner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val dx = event.x - centerX
            val dy = event.y - centerY
            hue        = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()
            saturation = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
            onColorChanged?.invoke(hue, saturation)
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun setColor(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hue        = hsv[0]
        saturation = hsv[1]
        invalidate()
        return hsv[2].coerceIn(0.05f, 1f)
    }

    fun getColor(value: Float): Int = Color.HSVToColor(floatArrayOf(hue, saturation, value))

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        wheelBitmap?.recycle()
        wheelBitmap = null
    }
}
