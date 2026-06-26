package com.easycode.easyframe

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private val requestStoragePermissionCode = 1002

    private lateinit var loadButton: Button
    private lateinit var saveButton: Button
    private lateinit var previewImage: ZoomableImageView
    private lateinit var eyedropperOverlay: EyedropperOverlay
    private lateinit var btnRotate: Button

    // Adaptive layout: toolbar always on top; contentArea flips orientation for landscape.
    private lateinit var rootLayout: LinearLayout
    private lateinit var contentArea: LinearLayout
    private lateinit var previewArea: FrameLayout
    private lateinit var panelArea: LinearLayout
    private lateinit var panelHandle: View
    private lateinit var controlsScroll: View
    private var panelExpanded = true
    private var isAnimating = false

    // Border thickness (2D pad + numeric fields)
    private lateinit var borderPad: BorderPadView

    // Color
    private lateinit var activeSwatch: View
    private lateinit var hexInput: EditText
    private lateinit var btnAuto: Button
    private lateinit var btnEyedropper: Button
    private lateinit var btnColorWheel: Button
    private lateinit var opacitySlider: OpacitySliderView
    private lateinit var opacityValueText: TextView

    private lateinit var swatchSelected: View
    private lateinit var swatchSelectedRing: View
    private lateinit var swatchWhite: View
    private lateinit var swatchWhiteRing: View
    private lateinit var swatchDark: View
    private lateinit var swatchDarkRing: View
    private lateinit var swatchGray: View
    private lateinit var swatchGrayRing: View
    private lateinit var swatchWarm: View
    private lateinit var swatchWarmRing: View
    private lateinit var swatchPlus: View

    private lateinit var donateButton: TextView
    private lateinit var emptyStateView: View

    private var sourceBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var customColor: Int = Color.parseColor("#808080")
    private var pendingSaveBitmap: Bitmap? = null
    private var eyedropperActiveColor: Int = Color.GRAY

    // Border model — the single source of truth (image px):
    //   borderSides    = left/right thickness (adds to WIDTH),  pad X axis
    //   borderTopBottom = top/bottom thickness (adds to HEIGHT), pad Y axis
    private var borderSides = 30
    private var borderTopBottom = 30
    private var frameOpacity = 1f
    private val maxBorder = 500

    private var renderJob: Job? = null
    private var autoColorJob: Job? = null
    private var isSyncing = false   // guards pad ↔ field ↔ hex update loops

    private var colorMode = COLOR_CUSTOM

    companion object {
        const val COLOR_CUSTOM = 0
        const val COLOR_WHITE = 1
        const val COLOR_DARK = 2
        const val COLOR_GRAY = 3
        const val COLOR_WARM = 4
        const val COLOR_AUTO = 5

        // Free donation page opened in the browser.
        const val DONATION_URL = "https://ko-fi.com/easycodefoundation"
    }

    private val colorWhite = Color.parseColor("#FAFAFA")
    private val colorDark = Color.parseColor("#16161A")
    private val colorGray = Color.parseColor("#8C8C92")
    private val colorWarm = Color.parseColor("#CAA46F")

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { loadSourceImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadButton        = findViewById(R.id.loadButton)
        saveButton        = findViewById(R.id.saveButton)
        previewImage      = findViewById(R.id.previewImage)
        eyedropperOverlay = findViewById(R.id.eyedropperOverlay)
        btnRotate         = findViewById(R.id.btnRotate)

        rootLayout        = findViewById(R.id.rootLayout)
        contentArea       = findViewById(R.id.contentArea)
        previewArea       = findViewById(R.id.previewArea)
        panelArea         = findViewById(R.id.panelArea)
        panelHandle       = findViewById(R.id.panelHandle)
        controlsScroll    = findViewById(R.id.controlsScroll)
        setupPanelHandleTouch()

        borderPad         = findViewById(R.id.borderPad)

        activeSwatch      = findViewById(R.id.activeSwatch)
        hexInput          = findViewById(R.id.hexInput)
        btnAuto           = findViewById(R.id.btnAuto)
        btnEyedropper     = findViewById(R.id.btnEyedropper)
        btnColorWheel     = findViewById(R.id.btnColorWheel)
        opacitySlider     = findViewById(R.id.opacitySlider)
        opacityValueText  = findViewById(R.id.opacityValueText)

        swatchSelected     = findViewById(R.id.swatchSelected)
        swatchSelectedRing = findViewById(R.id.swatchSelectedRing)
        swatchWhite        = findViewById(R.id.swatchWhite)
        swatchWhiteRing    = findViewById(R.id.swatchWhiteRing)
        swatchDark         = findViewById(R.id.swatchDark)
        swatchDarkRing     = findViewById(R.id.swatchDarkRing)
        swatchGray         = findViewById(R.id.swatchGray)
        swatchGrayRing     = findViewById(R.id.swatchGrayRing)
        swatchWarm         = findViewById(R.id.swatchWarm)
        swatchWarmRing     = findViewById(R.id.swatchWarmRing)
        swatchPlus         = findViewById(R.id.swatchPlus)

        donateButton       = findViewById(R.id.donateButton)
        emptyStateView     = findViewById(R.id.emptyStateView)

        swatchWhite.setBackgroundColor(colorWhite)
        swatchDark.setBackgroundColor(colorDark)
        swatchGray.setBackgroundColor(colorGray)
        swatchWarm.setBackgroundColor(colorWarm)

        // ── 2D pad (shows sides / top-bottom as axis coordinates) ───────────
        borderPad.maxValue = maxBorder
        borderPad.sides = borderSides
        borderPad.topBottom = borderTopBottom
        borderPad.onValuesChanged = { s, tb, dragging ->
            borderSides = s; borderTopBottom = tb
            if (!dragging) renderPreview(keepZoom = false)
        }

        // ── Opacity ─────────────────────────────────────────────────────────
        opacitySlider.value = frameOpacity
        opacitySlider.color = resolveFrameColor()
        opacitySlider.onValueChanged = { v, dragging ->
            frameOpacity = v
            opacityValueText.text = "${(v * 100).roundToInt()}%"
            if (!dragging) renderPreview(keepZoom = true)
        }

        // ── Eyedropper overlay ──────────────────────────────────────────────
        eyedropperOverlay.zoomableView = previewImage
        // Track the colour live; apply it immediately when the finger lifts (no confirmation).
        eyedropperOverlay.onColorChanged = { color -> eyedropperActiveColor = color }
        eyedropperOverlay.onFingerLifted = { applyEyedropperColor() }

        saveButton.isEnabled = false
        // Draw the wheel once the button actually has a size (the panel may start hidden → width 0).
        btnColorWheel.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int,
                                        ol: Int, ot: Int, or_: Int, ob: Int) {
                if (v.width > 0) {
                    v.removeOnLayoutChangeListener(this)
                    drawColorWheelButton(btnColorWheel)
                }
            }
        })

        setupHexInput()
        updateSwatchUI()
        updateColorInfo()
        layoutPanel()

        loadButton.setOnClickListener      { openImagePicker() }
        emptyStateView.setOnClickListener  { openImagePicker() }
        saveButton.setOnClickListener      { savePreviewImage() }
        btnRotate.setOnClickListener       { rotateSourceImage() }
        donateButton.setOnClickListener    { openDonationPage() }

        // Auto (magic wand)
        btnAuto.setOnClickListener {
            val src = sourceBitmap ?: return@setOnClickListener
            autoColorJob?.cancel()
            autoColorJob = lifecycleScope.launch {
                val color = withContext(Dispatchers.Default) { computeAutoColor(src) }
                customColor = color
                colorMode = COLOR_AUTO
                updateSwatchUI()
                updateColorInfo()
                renderPreview(keepZoom = true)
            }
        }

        btnEyedropper.setOnClickListener { showEyedropper() }
        btnColorWheel.setOnClickListener { openColorPicker() }

        swatchSelected.setOnClickListener     { selectColorMode(COLOR_CUSTOM) }
        swatchSelectedRing.setOnClickListener { selectColorMode(COLOR_CUSTOM) }
        swatchWhite.setOnClickListener        { selectColorMode(COLOR_WHITE) }
        swatchWhiteRing.setOnClickListener    { selectColorMode(COLOR_WHITE) }
        swatchDark.setOnClickListener         { selectColorMode(COLOR_DARK) }
        swatchDarkRing.setOnClickListener     { selectColorMode(COLOR_DARK) }
        swatchGray.setOnClickListener         { selectColorMode(COLOR_GRAY) }
        swatchGrayRing.setOnClickListener     { selectColorMode(COLOR_GRAY) }
        swatchWarm.setOnClickListener         { selectColorMode(COLOR_WARM) }
        swatchWarmRing.setOnClickListener     { selectColorMode(COLOR_WARM) }
        swatchPlus.setOnClickListener         { openColorPicker() }

        renderPreview()   // initial empty state: hide the controls panel until a photo is loaded
    }

    // ── Editable hex ────────────────────────────────────────────────────────

    private fun setupHexInput() {
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isSyncing) return
                val text = s?.toString()?.trim() ?: return
                val hex = if (text.startsWith("#")) text else "#$text"
                if (!Regex("^#[0-9a-fA-F]{6}$").matches(hex)) return
                val color = try { Color.parseColor(hex) } catch (e: Exception) { return }
                customColor = color
                colorMode = COLOR_CUSTOM
                updateSwatchUI()
                // update swatch + opacity but NOT the hex text (user is typing)
                activeSwatch.background = roundedSwatch(color)
                opacitySlider.color = color
                renderPreview(keepZoom = true)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }

    // ── Color mode ──────────────────────────────────────────────────────────

    private fun selectColorMode(mode: Int) {
        colorMode = mode
        updateSwatchUI()
        updateColorInfo()
        renderPreview(keepZoom = true)
    }

    private fun updateSwatchUI() {
        swatchSelectedRing.visibility = if (colorMode == COLOR_CUSTOM) View.VISIBLE else View.INVISIBLE
        swatchWhiteRing.visibility    = if (colorMode == COLOR_WHITE)  View.VISIBLE else View.INVISIBLE
        swatchDarkRing.visibility     = if (colorMode == COLOR_DARK)   View.VISIBLE else View.INVISIBLE
        swatchGrayRing.visibility     = if (colorMode == COLOR_GRAY)   View.VISIBLE else View.INVISIBLE
        swatchWarmRing.visibility     = if (colorMode == COLOR_WARM)   View.VISIBLE else View.INVISIBLE
        swatchSelected.setBackgroundColor(customColor)
    }

    private fun updateColorInfo() {
        val color = resolveFrameColor()
        activeSwatch.background = roundedSwatch(color)
        opacitySlider.color = color
        isSyncing = true
        hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
        isSyncing = false
    }

    private fun roundedSwatch(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 4f * resources.displayMetrics.density
        setColor(color)
    }

    private fun resolveFrameColor(): Int = when (colorMode) {
        COLOR_WHITE -> colorWhite
        COLOR_DARK  -> colorDark
        COLOR_GRAY  -> colorGray
        COLOR_WARM  -> colorWarm
        else        -> customColor
    }

    private fun applyAlpha(color: Int, opacity: Float): Int {
        val a = (opacity * 255f).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    // ── Image loading ───────────────────────────────────────────────────────

    private fun openImagePicker() { imagePickerLauncher.launch("image/*") }

    private fun loadSourceImage(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val raw = contentResolver.openInputStream(uri)
                        ?.use { BitmapFactory.decodeStream(it) }
                        ?: return@withContext null
                    val orientation = contentResolver.openInputStream(uri)?.use { stream ->
                        ExifInterface(stream).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                        )
                    } ?: ExifInterface.ORIENTATION_NORMAL
                    val rotated = applyExifRotation(raw, orientation)
                    if (rotated !== raw) raw.recycle()
                    rotated
                } catch (e: Exception) { null }
            }
            if (result == null) { showToast("Не удалось загрузить изображение"); return@launch }
            sourceBitmap?.recycle()
            sourceBitmap = result
            btnRotate.visibility = View.VISIBLE
            renderPreview(keepZoom = false)
            // Auto-color on load
            autoColorJob?.cancel()
            autoColorJob = lifecycleScope.launch autoColor@{
                val src = sourceBitmap ?: return@autoColor
                val color = withContext(Dispatchers.Default) { computeAutoColor(src) }
                if (sourceBitmap === src) {
                    customColor = color
                    colorMode = COLOR_AUTO
                    updateSwatchUI()
                    updateColorInfo()
                    renderPreview(keepZoom = false)
                }
            }
        }
    }

    private fun applyExifRotation(bmp: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bmp
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    private fun rotateSourceImage() {
        val src = sourceBitmap ?: return
        lifecycleScope.launch {
            val rotated = withContext(Dispatchers.Default) {
                val matrix = Matrix().apply { postRotate(90f) }
                Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            }
            src.recycle()
            sourceBitmap = rotated
            renderPreview(keepZoom = false)
        }
    }

    // ── Render ──────────────────────────────────────────────────────────────

    private fun renderPreview(keepZoom: Boolean = true) {
        val source = sourceBitmap ?: run {
            previewImage.setImageDrawable(null)
            saveButton.isEnabled = false
            emptyStateView.visibility = View.VISIBLE
            panelArea.visibility = View.GONE   // no photo → no controls, just the load prompt
            return
        }
        emptyStateView.visibility = View.GONE
        panelArea.visibility = View.VISIBLE
        val borderH    = borderSides       // width padding (left/right)
        val borderV    = borderTopBottom   // height padding (top/bottom)
        val frameColor = applyAlpha(resolveFrameColor(), frameOpacity)

        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                try {
                    val w = source.width  + borderH * 2
                    val h = source.height + borderV * 2
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).apply {
                        drawColor(frameColor)
                        drawBitmap(source, borderH.toFloat(), borderV.toFloat(), null)
                    }
                    bmp
                } catch (e: OutOfMemoryError) { null }
            }
            if (bitmap == null) { showToast("Изображение слишком большое для такой рамки"); return@launch }
            val old = previewBitmap
            if (keepZoom) previewImage.updateFrameBitmap(bitmap) else previewImage.setImageBitmap(bitmap)
            previewBitmap = bitmap
            old?.recycle()
            saveButton.isEnabled = true
        }
    }

    // ── Eyedropper ──────────────────────────────────────────────────────────

    private fun showEyedropper() {
        if (previewBitmap == null) return
        eyedropperActiveColor = customColor
        eyedropperOverlay.visibility = View.VISIBLE
    }

    // Called when the finger lifts: apply the picked colour right away, no confirmation.
    private fun applyEyedropperColor() {
        eyedropperOverlay.visibility = View.GONE
        customColor = eyedropperActiveColor
        colorMode   = COLOR_CUSTOM
        updateSwatchUI()
        updateColorInfo()
        renderPreview(keepZoom = true)
    }

    // ── Color picker ──────────────────────────────────────────────────────────

    private fun openColorPicker(initial: Int = customColor) {
        ColorPickerDialog(
            context             = this,
            initialColor        = initial,
            sourceBitmap        = sourceBitmap,
            onColorSelected     = { color ->
                customColor = color
                colorMode   = COLOR_CUSTOM
                updateSwatchUI()
                updateColorInfo()
                renderPreview(keepZoom = true)
            },
            onEyedropperRequested = { showEyedropper() }
        ).show()
    }

    // ── Auto color ────────────────────────────────────────────────────────────

    private fun computeAutoColor(bitmap: Bitmap): Int {
        val sw = 300; val sh = 300
        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        try {
            val pts = listOf(sw / 3 to sh / 3, 2 * sw / 3 to sh / 3,
                             sw / 3 to 2 * sh / 3, 2 * sw / 3 to 2 * sh / 3)
            val r = minOf(sw, sh) / 6; val r2 = r * r

            val zones: List<List<FloatArray>> = pts.map { (cx, cy) ->
                buildList {
                    for (y in (cy - r).coerceAtLeast(0)..(cy + r).coerceAtMost(sh - 1))
                        for (x in (cx - r).coerceAtLeast(0)..(cx + r).coerceAtMost(sw - 1)) {
                            if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > r2) continue
                            val hsv = FloatArray(3)
                            Color.colorToHSV(scaled.getPixel(x, y), hsv)
                            add(hsv)
                        }
                }
            }

            for (zone in zones) {
                val color = detectContrastObject(zone)
                if (color != null) return color
            }
            return averageRgb(zones.flatten())
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun detectContrastObject(pixels: List<FloatArray>): Int? {
        val sat = pixels.filter { it[1] > 0.20f && it[2] > 0.15f }
        if (sat.size < pixels.size * 0.12f) return null
        val buckets = IntArray(12)
        sat.forEach { buckets[(it[0] / 30f).toInt().coerceIn(0, 11)]++ }
        val domBucket = buckets.indices.maxByOrNull { buckets[it] } ?: return null
        val domHue = domBucket * 30f + 15f
        val contrast = sat.filter { hueDiff(it[0], domHue) > 50f }
        if (contrast.size < sat.size * 0.15f) return null
        return averageRgb(contrast)
    }

    private fun averageRgb(pixels: List<FloatArray>): Int {
        if (pixels.isEmpty()) return Color.GRAY
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        pixels.forEach { hsv ->
            val c = Color.HSVToColor(hsv)
            rSum += Color.red(c); gSum += Color.green(c); bSum += Color.blue(c)
        }
        val n = pixels.size
        return Color.rgb((rSum / n).toInt(), (gSum / n).toInt(), (bSum / n).toInt())
    }

    private fun hueDiff(a: Float, b: Float): Float {
        val d = Math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    // ── Color wheel button ────────────────────────────────────────────────────

    private fun drawColorWheelButton(button: Button) {
        val size = button.width.coerceAtLeast(1)
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cx   = size / 2f; val cy = size / 2f; val radius = size / 2f

        val canvas = Canvas(bmp)
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(cx, cy, intArrayOf(
                Color.RED, Color.YELLOW, Color.GREEN,
                Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
            ), null)
        })
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, radius,
                intArrayOf(Color.WHITE, Color.TRANSPARENT),
                floatArrayOf(0f, 0.6f), Shader.TileMode.CLAMP)
        })

        button.background = object : Drawable() {
            override fun draw(c: Canvas) { c.drawBitmap(bmp, 0f, 0f, null) }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun savePreviewImage() {
        val bitmap = previewBitmap ?: run { showToast("Сначала загрузите фото"); return }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSaveBitmap = bitmap
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestStoragePermissionCode
            )
            return
        }
        doSaveBitmap(bitmap)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestStoragePermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingSaveBitmap?.let { doSaveBitmap(it) }
            } else {
                showToast("Разрешение не предоставлено")
            }
            pendingSaveBitmap = null
        }
    }

    private fun doSaveBitmap(bitmap: Bitmap) {
        // PNG preserves alpha when the frame is semi-transparent; JPEG otherwise (smaller).
        val usePng = frameOpacity < 1f
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val ext = if (usePng) "png" else "jpg"
                    val mime = if (usePng) "image/png" else "image/jpeg"
                    val filename = "Framing-${System.currentTimeMillis()}.$ext"
                    val bytes = ByteArrayOutputStream().use { out ->
                        if (usePng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        else        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.toByteArray()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, mime)
                            put(MediaStore.Images.Media.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/Framing")
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val uri = contentResolver.insert(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                        ) ?: return@withContext false
                        contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                    } else {
                        val folder = java.io.File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            "Framing"
                        )
                        folder.mkdirs()
                        java.io.File(folder, filename).outputStream().use { it.write(bytes) }
                    }
                    true
                } catch (e: Exception) { false }
            }
            showToast(if (ok) "Сохранено в Галерею → Framing" else "Не удалось сохранить")
        }
    }

    // ── Adaptive orientation ────────────────────────────────────────────────

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutPanel()
        // Preview area changes size → ZoomableImageView.onSizeChanged re-fits automatically.
    }

    private fun togglePanel() {
        if (panelExpanded) collapseSheet() else expandSheet()
    }

    private fun expandSheet() {
        if (panelExpanded || isAnimating) return
        panelExpanded = true
        isAnimating = true
        // Push panel off screen before revealing controls so layout recalc happens hidden.
        panelArea.translationY = 2000f
        controlsScroll.visibility = View.VISIBLE
        controlsScroll.post {
            val h = controlsScroll.height.toFloat()
            panelArea.translationY = h
            panelArea.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { isAnimating = false }
                })
                .start()
        }
    }

    private fun collapseSheet() {
        if (!panelExpanded || isAnimating) return
        panelExpanded = false
        isAnimating = true
        val h = controlsScroll.height.toFloat()
        panelArea.animate()
            .translationY(h)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    controlsScroll.visibility = View.GONE
                    panelArea.translationY = 0f
                    isAnimating = false
                }
            })
            .start()
    }

    private fun setupPanelHandleTouch() {
        val density = resources.displayMetrics.density
        val tapThreshold = 10 * density
        val swipeThreshold = 20 * density
        var startY = 0f
        panelHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> true
                MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - startY
                    when {
                        dy < -swipeThreshold && !panelExpanded -> expandSheet()
                        dy > swipeThreshold && panelExpanded   -> collapseSheet()
                        Math.abs(dy) < tapThreshold            -> togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Arranges preview + panel by orientation.
    // Portrait: contentArea vertical, panel is a bottom sheet with grab handle.
    // Landscape: contentArea horizontal, panel is a side column (no collapse).
    private fun layoutPanel() {
        val config = resources.configuration
        val portrait = config.orientation == Configuration.ORIENTATION_PORTRAIT

        panelHandle.visibility = if (portrait) View.VISIBLE else View.GONE

        if (portrait) {
            contentArea.orientation = LinearLayout.VERTICAL
            previewArea.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            panelArea.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            controlsScroll.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (!panelExpanded) controlsScroll.visibility = View.GONE
        } else {
            // Landscape/tablet: side panel, always expanded
            val panelW = (286 * resources.displayMetrics.density).toInt()
            contentArea.orientation = LinearLayout.HORIZONTAL
            previewArea.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            panelArea.layoutParams = LinearLayout.LayoutParams(
                panelW, LinearLayout.LayoutParams.MATCH_PARENT)
            panelExpanded = true
            controlsScroll.visibility = View.VISIBLE
            controlsScroll.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // lifecycleScope cancels coroutines automatically; free the large bitmaps here.
        previewImage.setImageDrawable(null)
        sourceBitmap?.recycle();  sourceBitmap = null
        previewBitmap?.recycle(); previewBitmap = null
    }

    private fun openDonationPage() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL)))
        } catch (e: ActivityNotFoundException) {
            showToast("Не удалось открыть страницу")
        }
    }

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
