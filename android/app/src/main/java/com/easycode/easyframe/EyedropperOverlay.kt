package com.easycode.easyframe

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Transparent overlay — intercepts touches, draws loupe above finger.
// No dark background: photo shows through.
// Panel handles the "Взять" confirmation in dropper card.
class EyedropperOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var zoomableView: ZoomableImageView? = null

    // Called live as finger moves with the current pixel color
    var onColorChanged: ((Int) -> Unit)? = null

    // Called when finger lifts (color stays in panel card for confirmation)
    var onFingerLifted: (() -> Unit)? = null

    private var touchX = 0f
    private var touchY = 0f
    private var isFingerDown = false
    private val clipPath = Path()
    private val dp = context.resources.displayMetrics.density

    // Design: loupe 116dp diameter = 58dp radius
    private val loupeR = 58f * dp

    // Vertical gap between the finger and the aim/crosshair point (so the finger never covers it).
    private val aimOffset = loupeR + 24f * dp

    // Paints
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    // Pixel grid: rgba(0,0,0,0.12) = 31 alpha
    private val gridPaint = Paint().apply {
        color = Color.argb(31, 0, 0, 0)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    // Crosshair lines: rgba(255,255,255,0.45) = 115 alpha
    private val crosshairPaint = Paint().apply {
        color = Color.argb(115, 255, 255, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    // Center target: white stroke box + dark outer
    private val targetWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(242, 255, 255, 255) // 95% white
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val targetDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(102, 0, 0, 0) // 40% black
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    // Ring around loupe: 3dp white + 1.5dp dark
    private val ringWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(242, 255, 255, 255) // rgba(255,255,255,0.95)
    }
    private val ringDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(89, 0, 0, 0) // rgba(0,0,0,0.35)
    }

    // Drop shadow (approximated)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(128, 0, 0, 0)
        maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
    }

    // Hint text at bottom of preview
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(153, 255, 255, 255) // tMid = 60% white
        textSize = 12.5f * context.resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
        setShadowLayer(2f, 0f, 1f, Color.BLACK)
    }

    init {
        // Transparent background — photo shows through
        setBackgroundColor(Color.TRANSPARENT)
        // Required for drawBitmap with BlurMaskFilter
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        // Hint text near bottom
        canvas.drawText(
            "Двигайте палец по фото, чтобы взять цвет пикселя",
            width / 2f, height - 14f * dp, hintPaint
        )

        if (!isFingerDown) return

        // Aim point = the sampled pixel, offset above the finger so the finger (and the user's
        // hand) can sit below/outside the photo while the crosshair targets edge pixels.
        val aimX = touchX
        val aimY = touchY - aimOffset
        // Loupe is drawn at the aim point, but clamped so it always stays fully on screen.
        val lx = aimX.coerceIn(loupeR + 8f, width - loupeR - 8f)
        val ly = aimY.coerceIn(loupeR + 8f, height - loupeR - 8f)

        drawLoupe(canvas, lx, ly, aimX, aimY)
    }

    private fun drawLoupe(canvas: Canvas, cx: Float, cy: Float, sampleX: Float, sampleY: Float) {
        val r = loupeR

        // Drop shadow behind the loupe
        canvas.drawCircle(cx, cy + 6f * dp, r, shadowPaint)

        // --- Clip circle and draw content ---
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Magnified image: sample ~20 bitmap-px radius, scale to fill loupe
        val view = zoomableView
        val bmp = view?.getDisplayedBitmap()
        if (bmp != null) {
            val coords = view.getBitmapCoordsFromScreen(sampleX, sampleY)
            if (coords != null) {
                val bx = coords[0]
                val by = coords[1]
                // Sample radius in bitmap space: ~18px gives good magnification
                val sr = 18f
                val src = RectF(
                    (bx - sr).coerceAtLeast(0f),
                    (by - sr).coerceAtLeast(0f),
                    (bx + sr).coerceAtMost(bmp.width.toFloat()),
                    (by + sr).coerceAtMost(bmp.height.toFloat())
                )
                val dst = RectF(cx - r, cy - r, cx + r, cy + r)
                // roundOut() avoids asymmetric truncation of toInt()
                val srcRect = Rect(); src.roundOut(srcRect)
                canvas.drawBitmap(bmp, srcRect, dst, bitmapPaint)
            }
        } else {
            canvas.drawColor(Color.DKGRAY)
        }

        // Pixel grid — 14px cells (design: backgroundSize '14px 14px')
        val cell = 14f
        var x = cx - r
        while (x <= cx + r) { canvas.drawLine(x, cy - r, x, cy + r, gridPaint); x += cell }
        var y = cy - r
        while (y <= cy + r) { canvas.drawLine(cx - r, y, cx + r, y, gridPaint); y += cell }

        // Full crosshair lines across the circle
        canvas.drawLine(cx - r, cy, cx + r, cy, crosshairPaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, crosshairPaint)

        // Center target cell: 16x16, white ring + dark outer
        val half = 8f
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, targetWhitePaint)
        canvas.drawRect(cx - half - 1f, cy - half - 1f, cx + half + 1f, cy + half + 1f, targetDarkPaint)

        canvas.restore()

        // Rings outside the clip (drawn after restore so they're on top)
        // White ring: 3dp stroke (design: 0 0 0 3px rgba(255,255,255,0.95))
        ringWhite.strokeWidth = 3f * dp
        canvas.drawCircle(cx, cy, r + 1.5f * dp, ringWhite)
        // Dark ring: 1.5dp stroke (design: 0 0 0 4.5px rgba(0,0,0,0.35))
        ringDark.strokeWidth = 1.5f * dp
        canvas.drawCircle(cx, cy, r + 3f * dp + 0.75f * dp, ringDark)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                isFingerDown = true
                sampleAndNotify()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isFingerDown = false
                onFingerLifted?.invoke()
                invalidate()
            }
        }
        return true
    }

    private fun sampleAndNotify() {
        // Sample at the aim point (offset above the finger), matching the loupe crosshair.
        val color = zoomableView?.getPixelAtScreenPos(touchX, touchY - aimOffset) ?: return
        onColorChanged?.invoke(color)
    }
}

