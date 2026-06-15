package com.example.photoframe

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
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
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val requestStoragePermissionCode = 1002

    private lateinit var loadButton: Button
    private lateinit var saveButton: Button
    private lateinit var pickColorButton: Button
    private lateinit var previewImage: ZoomableImageView
    private lateinit var eyedropperOverlay: EyedropperOverlay
    private lateinit var verticalSeekBar: VerticalSeekBar
    private lateinit var horizontalSeekBar: VerticalSeekBar
    private lateinit var verticalValueText: TextView
    private lateinit var horizontalValueText: TextView
    private lateinit var dimensionsText: TextView
    private lateinit var linkButton: Button
    private lateinit var linkStatusText: TextView
    private lateinit var btnRotate: Button

    private lateinit var colorHexText: TextView
    private lateinit var colorPreviewSwatch: View
    private lateinit var btnAuto: Button
    private lateinit var btnEyedropper: Button
    private lateinit var btnColorWheel: Button

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

    private lateinit var emptyStateView: View
    private lateinit var swatchesRow: LinearLayout
    private lateinit var dropperCard: View
    private lateinit var dropperColorSwatch: View
    private lateinit var dropperHexText: TextView
    private lateinit var btnDropperConfirm: Button

    private var sourceBitmap: Bitmap? = null
    private var previewBitmap: Bitmap? = null
    private var customColor: Int = Color.parseColor("#808080")
    private var pendingSaveBitmap: Bitmap? = null
    private var sizesLinked = true
    private var eyedropperActiveColor: Int = Color.GRAY

    private var renderJob: Job? = null
    private var autoColorJob: Job? = null

    private var colorMode = COLOR_CUSTOM

    companion object {
        const val COLOR_CUSTOM = 0
        const val COLOR_WHITE = 1
        const val COLOR_DARK = 2
        const val COLOR_GRAY = 3
        const val COLOR_WARM = 4
        const val COLOR_AUTO = 5
    }

    private val colorWhite = Color.parseColor("#FAFAFA")
    private val colorDark = Color.parseColor("#16161A")
    private val colorGray = Color.parseColor("#8C8C92")
    private val colorWarm = Color.parseColor("#CAA46F")

    // Modern image picker — replaces deprecated startActivityForResult
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadSourceImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadButton          = findViewById(R.id.loadButton)
        saveButton          = findViewById(R.id.saveButton)
        pickColorButton     = findViewById(R.id.pickColorButton)
        previewImage        = findViewById(R.id.previewImage)
        eyedropperOverlay   = findViewById(R.id.eyedropperOverlay)
        verticalSeekBar     = findViewById(R.id.verticalSeekBar)
        horizontalSeekBar   = findViewById(R.id.horizontalSeekBar)
        verticalValueText   = findViewById(R.id.verticalValueText)
        horizontalValueText = findViewById(R.id.horizontalValueText)
        dimensionsText      = findViewById(R.id.dimensionsText)
        linkButton          = findViewById(R.id.linkButton)
        linkStatusText      = findViewById(R.id.linkStatusText)
        btnRotate           = findViewById(R.id.btnRotate)

        colorHexText        = findViewById(R.id.colorHexText)
        colorPreviewSwatch  = findViewById(R.id.colorPreviewSwatch)
        btnAuto             = findViewById(R.id.btnAuto)
        btnEyedropper       = findViewById(R.id.btnEyedropper)
        btnColorWheel       = findViewById(R.id.btnColorWheel)

        swatchSelected      = findViewById(R.id.swatchSelected)
        swatchSelectedRing  = findViewById(R.id.swatchSelectedRing)
        swatchWhite         = findViewById(R.id.swatchWhite)
        swatchWhiteRing     = findViewById(R.id.swatchWhiteRing)
        swatchDark          = findViewById(R.id.swatchDark)
        swatchDarkRing      = findViewById(R.id.swatchDarkRing)
        swatchGray          = findViewById(R.id.swatchGray)
        swatchGrayRing      = findViewById(R.id.swatchGrayRing)
        swatchWarm          = findViewById(R.id.swatchWarm)
        swatchWarmRing      = findViewById(R.id.swatchWarmRing)
        swatchPlus          = findViewById(R.id.swatchPlus)

        emptyStateView      = findViewById(R.id.emptyStateView)
        swatchesRow         = findViewById(R.id.swatchesRow)
        dropperCard         = findViewById(R.id.dropperCard)
        dropperColorSwatch  = findViewById(R.id.dropperColorSwatch)
        dropperHexText      = findViewById(R.id.dropperHexText)
        btnDropperConfirm   = findViewById(R.id.btnDropperConfirm)

        swatchWhite.setBackgroundColor(colorWhite)
        swatchDark.setBackgroundColor(colorDark)
        swatchGray.setBackgroundColor(colorGray)
        swatchWarm.setBackgroundColor(colorWarm)

        verticalSeekBar.value   = 30
        horizontalSeekBar.value = 30
        verticalSeekBar.max     = 500
        horizontalSeekBar.max   = 500

        eyedropperOverlay.zoomableView = previewImage
        eyedropperOverlay.onColorChanged = { color ->
            eyedropperActiveColor = color
            updateDropperCard(color)
        }
        eyedropperOverlay.onFingerLifted = { /* color stays in card */ }

        saveButton.isEnabled = false
        btnColorWheel.post { drawColorWheelButton(btnColorWheel) }

        updateSwatchUI()
        updateColorInfo()
        updateLinkButton()

        emptyStateView.setOnClickListener   { openImagePicker() }
        loadButton.setOnClickListener       { openImagePicker() }
        saveButton.setOnClickListener       { savePreviewImage() }
        pickColorButton.setOnClickListener  { openColorPicker() }
        btnRotate.setOnClickListener        { rotateSourceImage() }

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

        btnEyedropper.setOnClickListener   { showEyedropper() }
        btnColorWheel.setOnClickListener   { openColorPicker() }

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
        btnDropperConfirm.setOnClickListener  { confirmDropperColor() }

        linkButton.setOnClickListener {
            sizesLinked = !sizesLinked
            updateLinkButton()
            if (sizesLinked) {
                val avg = (verticalSeekBar.value + horizontalSeekBar.value) / 2
                verticalSeekBar.value   = avg
                horizontalSeekBar.value = avg
                verticalValueText.text   = avg.toString()
                horizontalValueText.text = avg.toString()
                renderPreview(keepZoom = true)
            }
        }

        verticalSeekBar.onValueChanged = { value, isDragging ->
            verticalValueText.text = value.toString()
            if (sizesLinked) {
                horizontalSeekBar.value  = value
                horizontalValueText.text = value.toString()
            }
            updateDimensionsText()
            if (!isDragging) renderPreview(keepZoom = true)
        }

        horizontalSeekBar.onValueChanged = { value, isDragging ->
            horizontalValueText.text = value.toString()
            if (sizesLinked) {
                verticalSeekBar.value  = value
                verticalValueText.text = value.toString()
            }
            updateDimensionsText()
            if (!isDragging) renderPreview(keepZoom = true)
        }
    }

    // ── Color mode ────────────────────────────────────────────────────────────

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
        if (colorMode == COLOR_AUTO) {
            btnAuto.background = ContextCompat.getDrawable(this, R.drawable.bg_tool_circle_active)
            btnAuto.setTextColor(ContextCompat.getColor(this, R.color.canvas_bg))
        } else {
            btnAuto.background = ContextCompat.getDrawable(this, R.drawable.bg_tool_circle)
            btnAuto.setTextColor(ContextCompat.getColor(this, R.color.tMid))
        }
    }

    private fun updateColorInfo() {
        val color = resolveFrameColor()
        colorPreviewSwatch.setBackgroundColor(color)
        colorHexText.text = String.format("#%06X", 0xFFFFFF and color)
    }

    private fun resolveFrameColor(): Int = when (colorMode) {
        COLOR_WHITE -> colorWhite
        COLOR_DARK  -> colorDark
        COLOR_GRAY  -> colorGray
        COLOR_WARM  -> colorWarm
        else        -> customColor
    }

    // ── Link button ───────────────────────────────────────────────────────────

    private fun updateLinkButton() {
        if (sizesLinked) {
            linkButton.background = ContextCompat.getDrawable(this, R.drawable.bg_link_linked)
            linkButton.setTextColor(ContextCompat.getColor(this, R.color.canvas_bg))
            linkStatusText.text = "связаны"
        } else {
            linkButton.background = ContextCompat.getDrawable(this, R.drawable.bg_link_unlinked)
            linkButton.setTextColor(ContextCompat.getColor(this, R.color.tMid))
            linkStatusText.text = "раздельно"
        }
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

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
            // Auto-color: compute asynchronously, then re-render with result
            autoColorJob?.cancel()
            autoColorJob = lifecycleScope.launch autoColor@{
                val src = sourceBitmap ?: return@autoColor
                val color = withContext(Dispatchers.Default) { computeAutoColor(src) }
                if (sourceBitmap === src) {
                    customColor = color
                    colorMode = COLOR_AUTO
                    updateSwatchUI()
                    updateColorInfo()
                    renderPreview(keepZoom = false)  // always fit-to-screen on initial load
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

    // ── Render ────────────────────────────────────────────────────────────────

    private fun renderPreview(keepZoom: Boolean = true) {
        val source = sourceBitmap ?: run {
            previewImage.setImageDrawable(null)
            saveButton.isEnabled = false
            dimensionsText.text = "Фото не загружено"
            emptyStateView.visibility = View.VISIBLE
            return
        }
        emptyStateView.visibility = View.GONE
        val borderV    = verticalSeekBar.value
        val borderH    = horizontalSeekBar.value
        val frameColor = resolveFrameColor()

        renderJob?.cancel()
        renderJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                val w = source.width  + borderH * 2
                val h = source.height + borderV * 2
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).apply {
                    drawColor(frameColor)
                    drawBitmap(source, borderH.toFloat(), borderV.toFloat(), null)
                }
                bmp
            }
            val old = previewBitmap
            if (keepZoom) previewImage.updateFrameBitmap(bitmap) else previewImage.setImageBitmap(bitmap)
            previewBitmap = bitmap
            old?.recycle()
            saveButton.isEnabled = true
            dimensionsText.text =
                "${bitmap.width} × ${bitmap.height} px  |  рамка: ${borderH}px ↔  ${borderV}px ↕"
        }
    }

    private fun updateDimensionsText() {
        val source  = sourceBitmap ?: return
        val borderH = horizontalSeekBar.value
        val borderV = verticalSeekBar.value
        dimensionsText.text =
            "${source.width + borderH * 2} × ${source.height + borderV * 2} px  |  рамка: ${borderH}px ↔  ${borderV}px ↕"
    }

    // ── Eyedropper ────────────────────────────────────────────────────────────

    private fun showEyedropper() {
        if (previewBitmap == null) return
        eyedropperActiveColor = customColor
        updateDropperCard(eyedropperActiveColor)
        eyedropperOverlay.visibility = View.VISIBLE
        dropperCard.visibility       = View.VISIBLE
        swatchesRow.visibility       = View.GONE
    }

    private fun updateDropperCard(color: Int) {
        dropperColorSwatch.background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 9f * resources.displayMetrics.density
        }
        dropperHexText.text = String.format("#%06X", 0xFFFFFF and color)
    }

    private fun confirmDropperColor() {
        customColor = eyedropperActiveColor
        colorMode   = COLOR_CUSTOM
        hideEyedropper()
        updateSwatchUI()
        updateColorInfo()
        renderPreview(keepZoom = true)
    }

    private fun hideEyedropper() {
        eyedropperOverlay.visibility = View.GONE
        dropperCard.visibility       = View.GONE
        swatchesRow.visibility       = View.VISIBLE
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
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val filename = "Framing-${System.currentTimeMillis()}.jpg"
                    val bytes = ByteArrayOutputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                        out.toByteArray()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
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

    private fun showToast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
