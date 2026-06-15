package com.example.photoframe

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

class ZoomableImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    var isEyedropperMode = false
    var onEyedropperColor: ((Int) -> Unit)? = null

    private val imgMatrix = Matrix()
    private val savedMatrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var scaleFactor = 1f
    private var mode = NONE
    private val startPoint = PointF()

    init {
        scaleType = ScaleType.MATRIX
        imageMatrix = imgMatrix
    }

    fun resetZoom() {
        val d = drawable ?: return
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0 } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0 } ?: return
        val vw = width.toFloat().takeIf { it > 0 } ?: return
        val vh = height.toFloat().takeIf { it > 0 } ?: return
        scaleFactor = minOf(vw / dw, vh / dh)
        imgMatrix.reset()
        imgMatrix.setScale(scaleFactor, scaleFactor)
        imgMatrix.postTranslate((vw - dw * scaleFactor) / 2f, (vh - dh * scaleFactor) / 2f)
        imageMatrix = imgMatrix
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { resetZoom() }
    }

    // Use this when updating the frame bitmap - preserves current zoom/pan
    fun updateFrameBitmap(bm: android.graphics.Bitmap?) {
        val snapshot = Matrix(imgMatrix)   // renamed to avoid shadowing class-level savedMatrix field
        val savedScale = scaleFactor
        super.setImageBitmap(bm)
        scaleFactor = savedScale
        imgMatrix.set(snapshot)
        imageMatrix = imgMatrix
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    fun getDisplayedBitmap(): Bitmap? = (drawable as? BitmapDrawable)?.bitmap

    fun getBitmapCoordsFromScreen(screenX: Float, screenY: Float): FloatArray? {
        val inv = Matrix()
        if (!imageMatrix.invert(inv)) return null
        val pts = floatArrayOf(screenX, screenY)
        inv.mapPoints(pts)
        return pts
    }

    fun getPixelAtScreenPos(screenX: Float, screenY: Float): Int? {
        val bmp = getDisplayedBitmap() ?: return null
        val pts = getBitmapCoordsFromScreen(screenX, screenY) ?: return null
        val bx = pts[0].toInt()
        val by = pts[1].toInt()
        if (bx < 0 || by < 0 || bx >= bmp.width || by >= bmp.height) return null
        return bmp.getPixel(bx, by)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isEyedropperMode) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val color = getPixelAtScreenPos(event.x, event.y)
                if (color != null) {
                    isEyedropperMode = false
                    onEyedropperColor?.invoke(color)
                }
            }
            return true
        }

        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(imgMatrix)
                startPoint.set(event.x, event.y)
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> mode = ZOOM
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> mode = NONE
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG && !scaleDetector.isInProgress) {
                    imgMatrix.set(savedMatrix)
                    imgMatrix.postTranslate(event.x - startPoint.x, event.y - startPoint.y)
                    imageMatrix = imgMatrix
                }
            }
        }
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(0.1f, 10f)
            val factor = newScale / scaleFactor
            scaleFactor = newScale
            imgMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            imageMatrix = imgMatrix
            return true
        }
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
    }
}
