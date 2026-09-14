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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
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

        // 主书尺寸微调饱满化，完美还原图二
        val bookWidth = (targetWidth * 0.44f).coerceIn(105f * density, 185f * density)
        val bookHeight = (bookWidth * 1.45f).coerceAtMost(targetHeight * 0.80f)
        val centerX = targetWidth * 0.50f
        val centerY = targetHeight * 0.45f

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

            // 1. 地面阴影光晕
            drawBookFloorShadow(
                canvas = canvas,
                x = x,
                y = y + (bookHeight * slot.scale * 0.5f) - 2f * density,
                width = bookWidth * slot.scale * 0.95f,
                height = 14f * density * slot.scale,
                alpha = slot.shadowAlpha,
                isDark = isDark
            )

            // 2. 带立体质感的封面
            val coverBitmap = getOrGenerateCover(
                context = context,
                book = book,
                targetWidth = bookWidth.roundToInt(),
                targetHeight = bookHeight.roundToInt(),
                density = density,
                isDark = isDark
            )

            // 3. 3D 旋转与投影
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

        // 4. 底部 5 个小圆点指示器（图二原版样式）
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
        val decoded = loadCoverBitmap(context, book.coverPath, targetWidth, targetHeight)
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val radius = 8f * density
        val rect = RectF(0f, 0f, targetWidth.toFloat(), targetHeight.toFloat())

        if (decoded != null) {
            val roundedCover = createRoundedBitmap(decoded, targetWidth, targetHeight, radius)
            canvas.drawBitmap(roundedCover, 0f, 0f, null)
        } else {
            drawProceduralCover(canvas, rect, book, radius, density)
        }

        // 书脊立体光影
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

        // 边框微描边
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = if (isDark) Color.argb(40, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        return result
    }

    private fun loadCoverBitmap(context: Context, coverPath: String?, targetWidth: Int, targetHeight: Int): Bitmap? {
        if (coverPath.isNullOrBlank() || targetWidth <= 0 || targetHeight <= 0) return null
        val targetSizePx = maxOf(targetWidth, targetHeight)
        if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
            val cached = downloadToCache(context, coverPath) ?: return null
            return decodeCoverFile(cached.absolutePath, targetSizePx)
        }
        return decodeCoverFile(coverPath, targetSizePx)
    }

    private fun downloadToCache(context: Context, url: String): File? {
        val cacheDir = File(context.filesDir, "widget_covers").apply { mkdirs() }
        val key = md5(url)
        val target = File(cacheDir, "$key.img")
        if (target.isFile && target.length() > 0) return target
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            }
            if (conn.responseCode in 200..299) {
                val tmp = File(cacheDir, "$key.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tmp.length() > 0 && isValidImage(tmp)) {
                    if (target.exists()) target.delete()
                    if (tmp.renameTo(target)) target else null
                } else {
                    tmp.delete()
                    null
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isValidImage(file: File): Boolean {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.outWidth > 0 && opts.outHeight > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            color = Color.argb(70, 255, 255, 255)
        }
        val innerRect = RectF(rect).apply { inset(6f * density, 6f * density) }
        canvas.drawRoundRect(innerRect, radius - 2f * density, radius - 2f * density, framePaint)

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

        val scale = maxOf(targetWidth.toFloat() / source.width, targetHeight.toFloat() / source.height)
        val scaledW = source.width * scale
        val scaledH = source.height * scale
        val dx = (targetWidth - scaledW) / 2f
        val dy = (targetHeight - scaledH) / 2f

        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    private fun decodeCoverFile(filePath: String, targetSizePx: Int): Bitmap? {
        val file = File(filePath)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sample = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > targetSizePx * 2 || h > targetSizePx * 2) {
                sample *= 2
                w /= 2
                h /= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(filePath, decodeOptions)
        } catch (_: Throwable) {
            null
        }
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
        val dotRadius = 2.5f * density
        val spacing = 7f * density
        val totalWidth = count * (dotRadius * 2) + (count - 1) * spacing
        var startX = (targetWidth - totalWidth) / 2f + dotRadius
        val dotY = targetHeight - 12f * density

        val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.WHITE else Color.argb(200, 40, 40, 50)
        }
        val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDark) Color.argb(60, 255, 255, 255) else Color.argb(50, 0, 0, 0)
        }

        for (i in 0 until count) {
            val paint = if (i == activeIndex) activePaint else inactivePaint
            canvas.drawCircle(startX, dotY, dotRadius, paint)
            startX += dotRadius * 2 + spacing
        }
    }

    private fun drawEmptyState(canvas: Canvas, width: Int, height: Int, isDark: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.argb(80, 255, 255, 255) else Color.argb(80, 0, 0, 0)
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("暂无书籍", width / 2f, height / 2f, paint)
    }

    private val COVER_PALETTES = listOf(
        Color.parseColor("#1B2A4A") to Color.parseColor("#2E4A7D"),
        Color.parseColor("#2C3E50") to Color.parseColor("#4CA1AF"),
        Color.parseColor("#1E3C72") to Color.parseColor("#2A5298"),
        Color.parseColor("#373B44") to Color.parseColor("#4286F4"),
        Color.parseColor("#283048") to Color.parseColor("#859398"),
        Color.parseColor("#141E30") to Color.parseColor("#243B55")
    )
}
