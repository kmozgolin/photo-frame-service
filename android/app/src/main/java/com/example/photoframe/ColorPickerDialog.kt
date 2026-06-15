package com.example.photoframe

import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.graphics.toColorInt

// Glass popover matching the design:
// Dark semi-transparent (rgba(20,20,24,0.92)), borderRadius 24dp
// ColorWheel 150dp + brightness slider + HEX/eyedropper/confirm + "ПО ФОТО" row
private const val MIN_BRIGHTNESS = 0.15f   // floor below which we treat brightness as "too dark"

class ColorPickerDialog(
    private val context: Context,
    private val initialColor: Int,
    private val sourceBitmap: Bitmap? = null,     // for "ПО ФОТО" suggestions
    private val onColorSelected: (Int) -> Unit,
    private val onEyedropperRequested: (() -> Unit)? = null
) {
    private var currentValue = 1f
    private var isUpdating = false

    fun show() {
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        // Build the glass container
        val root = buildRoot(dp, dialog)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val w = (300 * dp).toInt()
            setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }
        dialog.show()
    }

    private fun buildRoot(dp: Float, dialog: Dialog): View {
        val bg = GradientDrawable().apply {
            setColor(Color.argb(235, 20, 20, 24)) // rgba(20,20,24,0.92)
            cornerRadius = 24 * dp
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            background = bg
        }

        // ── Title row: "Цвет рамки" + X ──────────────────────────────────────
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(lp_mw, lp_ww).also {
                it.bottomMargin = (16 * dp).toInt()
            }
        }
        titleRow.addView(TextView(context).apply {
            text = "Цвет рамки"
            textSize = 15f
            setTextColor(Color.argb(245, 255, 255, 255))
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, lp_ww, 1f)
        })
        titleRow.addView(TextView(context).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.argb(153, 255, 255, 255))
            setPadding((4 * dp).toInt(), 0, 0, 0)
            setOnClickListener { dialog.dismiss() }
        })
        root.addView(titleRow)

        // ── Color wheel (150dp, centered) ─────────────────────────────────────
        val wheelView = ColorWheelView(context).apply {
            val sz = (150 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz).also {
                it.gravity = Gravity.CENTER_HORIZONTAL
                it.bottomMargin = (16 * dp).toInt()
            }
        }
        currentValue = wheelView.setColor(initialColor).let { if (it < MIN_BRIGHTNESS) 1f else it }
        root.addView(wheelView)

        // ── Brightness slider ─────────────────────────────────────────────────
        val brightnessSlider = BrightnessSliderView(context).apply {
            layoutParams = LinearLayout.LayoutParams(lp_mw, (28 * dp).toInt()).also {
                it.bottomMargin = (16 * dp).toInt()
            }
            value = currentValue
            endColor = wheelView.getColor(1f)
        }
        root.addView(brightnessSlider)

        // ── HEX input + eyedropper + confirm ──────────────────────────────────
        val hexRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(lp_mw, lp_ww).also {
                it.bottomMargin = (16 * dp).toInt()
            }
        }

        // Swatch + HEX input box
        val hexBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(15, 255, 255, 255)) // rgba(255,255,255,0.06)
                setStroke(1, Color.argb(23, 255, 255, 255)) // rgba(255,255,255,0.09)
                cornerRadius = 11 * dp
            }
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(0, (42 * dp).toInt(), 1f).also {
                it.marginEnd = (9 * dp).toInt()
            }
        }
        val colorSquare = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((16 * dp).toInt(), (16 * dp).toInt()).also {
                it.marginEnd = (8 * dp).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 4 * dp
                setColor(wheelView.getColor(currentValue))
            }
        }
        hexBox.addView(colorSquare)
        val hexInput = EditText(context).apply {
            setText(toHex(wheelView.getColor(currentValue)))
            hint = "#RRGGBB"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            textSize = 14f
            setTextColor(Color.argb(245, 255, 255, 255))
            setHintTextColor(Color.argb(102, 255, 255, 255))
            background = null
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, lp_ww, 1f)
        }
        hexBox.addView(hexInput)
        hexRow.addView(hexBox)

        // Eyedropper button
        val eyedropBtn = buildIconButton(dp, "💧", (42 * dp).toInt()).apply {
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt()).also {
                it.marginEnd = (9 * dp).toInt()
            }
            setOnClickListener { dialog.dismiss(); onEyedropperRequested?.invoke() }
        }
        hexRow.addView(eyedropBtn)

        // Confirm (white check) button
        val confirmBtn = buildWhiteButton(dp, "✓", (42 * dp).toInt()).apply {
            setOnClickListener {
                onColorSelected(wheelView.getColor(currentValue))
                dialog.dismiss()
            }
        }
        hexRow.addView(confirmBtn)
        root.addView(hexRow)

        // ── "ПО ФОТО" color suggestions ───────────────────────────────────────
        val photoColors = extractPhotoColors()
        if (photoColors.isNotEmpty()) {
            val photoRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(lp_mw, lp_ww)
            }
            // "✦" sparkle
            photoRow.addView(TextView(context).apply {
                text = "✦"
                textSize = 11f
                setTextColor(Color.argb(153, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(lp_ww, lp_ww).also { it.marginEnd = (6 * dp).toInt() }
            })
            // "ПО ФОТО" label
            photoRow.addView(TextView(context).apply {
                text = "ПО ФОТО"
                textSize = 9f
                letterSpacing = 0.1f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.argb(102, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(lp_ww, lp_ww).also { it.marginEnd = (8 * dp).toInt() }
            })
            // Color bars (flex:1 each, height 24dp, radius 6dp)
            photoColors.forEachIndexed { i, color ->
                val bar = View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = 6 * dp
                    }
                    layoutParams = LinearLayout.LayoutParams(0, (24 * dp).toInt(), 1f).also {
                        if (i < photoColors.lastIndex) it.marginEnd = (6 * dp).toInt()
                    }
                    setOnClickListener {
                        // Apply this suggestion as current color
                        isUpdating = true
                        val hsv = FloatArray(3)
                        Color.colorToHSV(color, hsv)
                        wheelView.setColor(color)
                        currentValue = hsv[2].let { if (it < MIN_BRIGHTNESS) 1f else it }
                        brightnessSlider.value = currentValue
                        brightnessSlider.endColor = wheelView.getColor(1f)
                        val finalColor = wheelView.getColor(currentValue)
                        colorSquare.background = GradientDrawable().apply {
                            cornerRadius = 4 * dp; setColor(finalColor)
                        }
                        hexInput.setText(toHex(finalColor))
                        isUpdating = false
                    }
                }
                photoRow.addView(bar)
            }
            root.addView(photoRow)
        }

        // ── Wire up change callbacks ──────────────────────────────────────────
        fun syncSwatch() {
            if (isUpdating) return
            val color = wheelView.getColor(currentValue)
            colorSquare.background = GradientDrawable().apply {
                cornerRadius = 4 * dp; setColor(color)
            }
            isUpdating = true
            hexInput.setText(toHex(color))
            isUpdating = false
        }

        wheelView.onColorChanged = { _, _ ->
            brightnessSlider.endColor = wheelView.getColor(1f)
            syncSwatch()
        }
        brightnessSlider.onValueChanged = { v, _ ->
            currentValue = v
            syncSwatch()
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                try {
                    val color = Color.parseColor(s.toString())
                    isUpdating = true
                    currentValue = wheelView.setColor(color).let { if (it < MIN_BRIGHTNESS) 1f else it }
                    brightnessSlider.value = currentValue
                    brightnessSlider.endColor = wheelView.getColor(1f)
                    colorSquare.background = GradientDrawable().apply {
                        cornerRadius = 4 * dp; setColor(color)
                    }
                    isUpdating = false
                } catch (_: Exception) {}
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return root
    }

    private fun buildIconButton(dp: Float, icon: String, size: Int): Button = Button(context).apply {
        text = icon
        textSize = 16f
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(15, 255, 255, 255))
            setStroke(1, Color.argb(41, 255, 255, 255))
        }
        layoutParams = LinearLayout.LayoutParams(size, size)
        minWidth = 0; minHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
    }

    private fun buildWhiteButton(dp: Float, icon: String, size: Int): Button = Button(context).apply {
        text = icon
        textSize = 18f
        setTextColor(Color.parseColor("#101012"))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FAFAFA"))
        }
        layoutParams = LinearLayout.LayoutParams(size, size)
        minWidth = 0; minHeight = 0
        setPadding(0, 0, 0, 0)
        stateListAnimator = null
    }

    private fun extractPhotoColors(): List<Int> {
        val bmp = sourceBitmap ?: return emptyList()
        val scaled = Bitmap.createScaledBitmap(bmp, 100, 100, true)
        try {
            val count = 5
            val zoneW = 100 / count
            return (0 until count).map { i ->
                val sx = i * zoneW
                var r = 0L; var g = 0L; var b = 0L; var n = 0
                for (y in 0 until 100) {
                    for (x in sx until minOf(sx + zoneW, 100)) {
                        val p = scaled.getPixel(x, y)
                        r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
                    }
                }
                if (n > 0) Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
                else Color.GRAY
            }
        } finally {
            if (scaled !== bmp) scaled.recycle()
        }
    }

    private fun toHex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    companion object {
        private const val lp_ww = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val lp_mw = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
