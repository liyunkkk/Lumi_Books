package com.huangder.lumibooks.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import com.huangder.lumibooks.data.local.model.CoverFlowBookData
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

object Book3DCoverRenderer {

    private data class SlotConfig(
        val offset: Int,
        val rotationY: Float,
        val scale: Float,
        val xOffsetRatio: Float,
        val yOffsetRatio: Float,
        val brightness: Float,
        val shadowAlpha: Float
    )

    private val SLOTS = listOf(
        SlotConfig(-2, 48f, 0.72f, -1.02f, 0.04f, 0.65f, 0.20f),
        SlotConfig(2, -48f, 0.72f, 1.02f, 0.04f, 0.65f, 0.20f),
        SlotConfig(-1, 32f, 0.86f, -0.56f, 0.02f, 0.84f, 0.28f),
        SlotConfig(1, -32f, 0.86f, 0.56f, 0.02f, 0.84f, 0.28f),
        SlotConfig(0, -2f, 1.00f, 0.00f, 0.00f, 1.00f, 0.38f)
    )

    fun render(
        context: Context,
        books: List<CoverFlowBookData>,
        selectedIndex: Int,
        widthPx: Int,
        heightPx: Int,
        accentColor: Int,
        isDark: Boolean
    ): Bitmap {
        val targetWidth = widthPx.coerceAtLeast(300)
        val targetHeight = heightPx.coerceAtLeast(200)
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (books.isEmpty()) {
            drawEmptyState(canvas, targetWidth, targetHeight, isDark)
            return output
        }

        val clampedSelected = selectedIndex.coerceIn(0, books.size - 1)
        val density = context.resources.displayMetrics.density

        // Center book reference dimensions (approx standard 1:1.44 novel ratio)
        val bookWidth = (targetWidth * 0.38f).coerceIn(100f * density, 160f * density)
        val bookHeight = (bookWidth * 1.45f).coerceAtMost(targetHeight * 0.78f)
        val centerX = targetWidth * 0.50f
        val centerY = targetHeight * 0.45f

        // Draw slots back to front
        for (slot in SLOTS) {
            val bookIndex = if (books.size >= 5) {
                (clampedSelected + slot.offset + books.size * 2) % books.size
            } else {
                val target = clampedSelected + slot.offset
                if (target in books.indices) target else continue
            }

            val book = books[bookIndex]
            val x = centerX + bookWidth * slot.xOffsetRatio
            val y = centerY + bookHeight * slot.yOffsetRatio

            // 1. Draw floor shadow
            drawBookFloorShadow(
                canvas = canvas,
                x = x,
                y = y + (bookHeight * slot.scale * 0.5f) - 2f * density,
                width = bookWidth * slot.scale * 0.95f,
                height = 14f * density * slot.scale,
                alpha = slot.shadowAlpha,
                isDark = isDark
            )

            // 2. Prepare 2D textured book cover
            val coverBitmap = getOrGenerateCover(
                context = context,
                book = book,
                targetWidth = bookWidth.roundToInt(),
                targetHeight = bookHeight.roundToInt(),
                density = density,
                isDark = isDark
            )

            // 3. Render 3D transformed book
            drawTransformedBook(
                canvas = canvas,
                cover = coverBitmap,
                x = x,
                y = y,
                bookWidth = bookWidth,
                bookHeight = bookHeight,
                rotationY = slot.rotationY,
                scale = slot.scale,
                brightness = slot.brightness
            )
        }

        // 4. Draw carousel indicators at the bottom
        drawIndicators(
            canvas = canvas,
            count = min(books.size, 5),
            activeIndex = if (books.size >= 5) clampedSelected % 5 else clampedSelected,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            density = density,
            accentColor = accentColor,
            isDark = isDark
        )

        return output
    }

    private fun drawTransformedBook(
        canvas: Canvas,
        cover: Bitmap,
        x: Float,
        y: Float,
        bookWidth: Float,
        bookHeight: Float,
        rotationY: Float,
        scale: Float,
        brightness: Float
    ) {
        val camera = Camera()
        val matrix = Matrix()

        camera.save()
        camera.rotateY(rotationY)
        camera.getMatrix(matrix)
        camera.restore()

        matrix.preTranslate(-bookWidth / 2f, -bookHeight / 2f)
        matrix.postScale(scale, scale)
        matrix.postTranslate(x, y)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (brightness < 1.0f) {
            val darkness = ((1.0f - brightness) * 255).roundToInt().coerceIn(0, 255)
            paint.colorFilter = PorterDuffColorFilter(
                Color.argb(darkness, 0, 0, 0),
                PorterDuff.Mode.SRC_ATOP
            )
        }

        canvas.drawBitmap(cover, matrix, paint)
    }

    private fun drawBookFloorShadow(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        alpha: Float,
        isDark: Boolean
    ) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            val shadowColor = if (isDark) Color.BLACK else Color.argb((alpha * 255).roundToInt(), 20, 20, 30)
            shader = RadialGradient(
                x, y,
                (width / 2f).coerceAtLeast(10f),
                intArrayOf(shadowColor, Color.TRANSPARENT),
                floatArrayOf(0.2f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        val shadowRect = RectF(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
        canvas.drawOval(shadowRect, shadowPaint)
    }

    private fun getOrGenerateCover(
        context: Context,
        book: CoverFlowBookData,
        targetWidth: Int,
        targetHeight: Int,
        density: Float,
        isDark: Boolean
    ): Bitmap {
        val decoded = decodeCoverFile(book.coverPath, targetWidth, targetHeight)
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val radius = 8f * density
        val rect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())

        // 1. Draw rounded cover image or generated hardcover texture
        if (decoded != null) {
            val roundedCover = createRoundedBitmap(decoded, targetWidth, targetHeight, radius)
            canvas.drawBitmap(roundedCover, 0f, 0f, null)
        } else {
            drawProceduralCover(canvas, rect, book, radius, density)
        }

        // 2. Add realistic book spine lighting and right page edge
        val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, targetWidth * 0.12f, 0f,
                intArrayOf(
                    Color.argb(90, 0, 0, 0),
                    Color.argb(30, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.25f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, spinePaint)

        // Subtle 1dp outer border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        return result
    }

    private fun drawProceduralCover(
        canvas: Canvas,
        rect: RectF,
        book: CoverFlowBookData,
        radius: Float,
        density: Float
    ) {
        val hash = abs(book.title.hashCode())
        val palette = COVER_PALETTES[hash % COVER_PALETTES.size]

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                palette.first, palette.second,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // Delicate inner frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = Color.argb(70, 255, 255, 255)
        }
        val innerRect = RectF(rect).apply { inset(6f * density, 6f * density) }
        canvas.drawRoundRect(innerRect, radius - 2f * density, radius - 2f * density, framePaint)

        // Title text
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val authorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            textSize = 10f * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val titleX = rect.centerX()
        val titleY = rect.centerY() - 6f * density
        val titleText = if (book.title.length > 8) book.title.take(7) + "…" else book.title
        canvas.drawText(titleText, titleX, titleY, titlePaint)

        val authorText = if (book.author.isNotBlank()) book.author.take(6) else "未知作者"
        canvas.drawText(authorText, titleX, titleY + 16f * density, authorPaint)
    }

    private fun createRoundedBitmap(
        source: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        radius: Float
    ): Bitmap {
        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val rect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())

        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        val srcRect = Rect(0, 0, source.width, source.height)
        canvas.drawBitmap(source, srcRect, rect, paint)
        return output
    }

    private fun decodeCoverFile(path: String?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth || bounds.outHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    private fun drawIndicators(
        canvas: Canvas,
        count: Int,
        activeIndex: Int,
        targetWidth: Int,
        targetHeight: Int,
        density: Float,
        accentColor: Int,
        isDark: Boolean
    ) {
        if (count <= 1) return

        val dotNormalWidth = 5f * density
        val dotActiveWidth = 14f * density
        val dotHeight = 5f * density
        val spacing = 5f * density

        val totalWidth = (count - 1) * (dotNormalWidth + spacing) + dotActiveWidth
        var startX = (targetWidth - totalWidth) / 2f
        val startY = targetHeight - 14f * density

        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.argb(70, 255, 255, 255) else Color.argb(60, 0, 0, 0)
            style = Paint.Style.FILL
        }

        for (i in 0 until count) {
            val isActive = (i == activeIndex)
            val w = if (isActive) dotActiveWidth else dotNormalWidth
            val rect = RectF(startX, startY, startX + w, startY + dotHeight)
            canvas.drawRoundRect(rect, dotHeight / 2f, dotHeight / 2f, if (isActive) activePaint else inactivePaint)
            startX += w + spacing
        }
    }

    private fun drawEmptyState(
        canvas: Canvas,
        targetWidth: Int,
        targetHeight: Int,
        isDark: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rect = RectF(
            targetWidth * 0.3f, targetHeight * 0.2f,
            targetWidth * 0.7f, targetHeight * 0.8f
        )
        canvas.drawRoundRect(rect, 12f, 12f, paint)
    }

    private val COVER_PALETTES = listOf(
        Pair(Color.parseColor("#1B2A4A"), Color.parseColor("#0C1527")), // Midnight Navy
        Pair(Color.parseColor("#1F3B2E"), Color.parseColor("#0F1E17")), // Forest Emerald
        Pair(Color.parseColor("#3D1F28"), Color.parseColor("#200F15")), // Burgundy Crimson
        Pair(Color.parseColor("#38281B"), Color.parseColor("#1E140C")), // Warm Amber Mocha
        Pair(Color.parseColor("#262938"), Color.parseColor("#13151D")), // Slate Graphite
        Pair(Color.parseColor("#2D2338"), Color.parseColor("#17111E"))  // Deep Violet
    )
}
