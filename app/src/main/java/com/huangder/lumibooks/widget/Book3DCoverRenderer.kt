package com.huangder.lumibooks.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * App 主页同款 3D 景深封面流渲染器（widget 版）。
 *
 * 视觉规范完全对齐 CardFlowHomeScreen：
 * - 卡片宽高比 0.68（竖版）
 * - rotationY = -offset * 20°（左右卡片向内收拢叠放）
 * - scale = 1 - absOffset * 0.12（相邻卡片约 0.88）
 * - 相邻书露出约 40% 封面
 * - 中心卡正下方：书名（粗体）+ 作者（次级色）
 */
internal object Book3DCoverRenderer {

    private const val CARD_ASPECT = 0.68f
    private const val CARD_CORNER_RATIO = 0.10f // 圆角 / 卡片宽
    private const val GUTTER_RATIO = 0.30f      // 相邻卡水平步进 / 卡片宽（露出约 40%）

    fun render(
        context: Context,
        books: List<CoverFlowBookDataCompat>,
        selectedIndex: Int,
        widthPx: Int,
        heightPx: Int,
        accentColor: Int,
        isDark: Boolean,
        backgroundOpacity: Int = 100
    ): Bitmap {
        if (widthPx <= 0 || heightPx <= 0) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景（可透明）
        if (backgroundOpacity > 0) {
            val bgAlpha = (backgroundOpacity.coerceIn(0, 100) * 2.55f).roundToInt()
            val bgBase = if (isDark) 0xFF1C1C1E.toInt() else 0xFFF7F7FA.toInt()
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (bgBase and 0x00FFFFFF) or (bgAlpha shl 24)
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                widthPx * 0.085f,
                widthPx * 0.085f,
                bgPaint
            )
        }

        if (books.isEmpty()) return bitmap

        val index = selectedIndex.coerceIn(0, books.size - 1)

        // 排版参数：书名区按比例预留（主页里书名在卡片下方）
        val titleBandRatio = 0.16f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFFF5F5F7.toInt() else 0xFF111111.toInt()
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textSize = heightPx * 0.046f
            textAlign = Paint.Align.CENTER
        }
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0xFFAEAEB2.toInt() else 0xFF6E6E73.toInt()
            typeface = Typeface.DEFAULT
            textSize = heightPx * 0.034f
            textAlign = Paint.Align.CENTER
        }
        val titleBandPx = heightPx * titleBandRatio

        // 舞台区域
        val stageTop = 0f
        val stageBottom = heightPx - titleBandPx
        val stageHeight = stageBottom - stageTop

        // 卡片尺寸：以高度定尺寸，保证 3 张卡 + 露边都在画布内
        val cardHeight = stageHeight * 0.94f
        val cardWidth = cardHeight * CARD_ASPECT
        val gutter = cardWidth * GUTTER_RATIO
        // 三张卡总宽 = cardWidth + 2 * gutter（左右卡被中心卡遮住约 60%）
        val totalSpread = cardWidth + 2 * gutter
        val scaleDown = min(1f, widthPx * 0.96f / totalSpread)
        val drawCardWidth = cardWidth * scaleDown
        val drawCardHeight = cardHeight * scaleDown
        val drawGutter = gutter * scaleDown

        val centerX = widthPx / 2f
        val cardTop = stageTop + (stageHeight - drawCardHeight) / 2f

        // 解码 5 本书封面（带 URL 下载缓存）
        val covers = books.take(5).map { loadCoverBitmap(context, it.coverPath, (drawCardHeight * 1.6f).roundToInt()) }
        val displayIndex = index.coerceIn(0, covers.size - 1)
        val centerBook = books[displayIndex]

        // 绘制顺序：外侧 → 内侧 → 中心（远→近，保证叠放正确）
        val layerSpecs = buildList {
            add(-2); add(2); add(-1); add(1); add(0)
        }
        for (slot in layerSpecs) {
            val bookIdx = displayIndex + slot
            if (bookIdx < 0 || bookIdx >= covers.size) continue
            val cover = covers[bookIdx]
            val pageOffset = slot.toFloat()
            // —— 完全对齐主页 CardFlowHomeScreen 变换公式 ——
            val absOffset = abs(pageOffset)
            val scale = 1f - (absOffset * 0.12f).coerceIn(0f, 0.18f)
            val rotationY = -pageOffset.coerceIn(-1.5f, 1.5f) * 20f
            val alpha = 1f - (absOffset * 0.18f).coerceIn(0f, 0.30f)
            // translationX: -pageOffset * 10dp（主页逻辑，中心卡稍向滑动反方向平移）
            val translationX = -pageOffset * drawGutter * 0.18f

            draw3DCard(
                canvas = canvas,
                bitmap = cover,
                book = books[bookIdx],
                centerX = centerX,
                cardTop = cardTop,
                cardWidth = drawCardWidth,
                cardHeight = drawCardHeight,
                gutter = drawGutter,
                pageOffset = pageOffset,
                scale = scale,
                rotationY = rotationY,
                alpha = alpha,
                translationX = translationX,
                isDark = isDark,
                accentColor = accentColor,
                isCenter = slot == 0,
                coverTargetSize = (drawCardHeight * 1.6f).roundToInt()
            )
        }

        // 中心书名 + 作者（主页同款：卡片下方居中）
        val cy = stageBottom + titleBandPx * 0.40f
        canvas.drawText(ellipsize(centerBook.title, titlePaint, widthPx * 0.86f), centerX, cy, titlePaint)
        if (centerBook.author.isNotBlank()) {
            canvas.drawText(
                ellipsize(centerBook.author, authorPaint, widthPx * 0.86f),
                centerX,
                cy + titleBandPx * 0.42f,
                authorPaint
            )
        }
        return bitmap
    }

    /** 单张 3D 卡片：透视 + 圆角 + 阴影 + 书脊渐变 + 无封面回退。 */
    private fun draw3DCard(
        canvas: Canvas,
        bitmap: Bitmap?,
        book: CoverFlowBookDataCompat,
        centerX: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        gutter: Float,
        pageOffset: Float,
        scale: Float,
        rotationY: Float,
        alpha: Float,
        translationX: Float,
        isDark: Boolean,
        accentColor: Int,
        isCenter: Boolean,
        coverTargetSize: Int
    ) {
        val saveCount = canvas.save()
        val pivotX = centerX + pageOffset * gutter + translationX
        val pivotY = cardTop + cardHeight / 2f
        canvas.translate(pivotX, pivotY)
        canvas.scale(scale, scale)
        // 透视旋转（cameraDistance = 16 * density 的等效投影强度）
        applyPerspectiveRotation(canvas, rotationY)
        canvas.translate(-cardWidth / 2f, -cardHeight / 2f)

        val cardAlpha = (alpha * 255).roundToInt().coerceIn(40, 255)
        val radius = cardWidth * CARD_CORNER_RATIO
        val cardRect = RectF(0f, 0f, cardWidth, cardHeight)

        // 中心卡阴影（主页 center 20dp / 侧卡 6dp 阴影）
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x66000000 else 0x40000000
            setShadowLayer(if (isCenter) cardWidth * 0.06f else cardWidth * 0.02f, 0f, cardHeight * 0.02f, color)
        }
        canvas.drawRoundRect(cardRect, radius, radius, shadowPaint)

        // 封面 / 回退生成封面
        val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.alpha = cardAlpha
        }
        if (bitmap != null && !bitmap.isRecycled) {
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix()
            val scaleMat = maxOf(cardWidth / bitmap.width, cardHeight / bitmap.height)
            matrix.setScale(scaleMat, scaleMat)
            matrix.postTranslate((cardWidth - bitmap.width * scaleMat) / 2f, (cardHeight - bitmap.height * scaleMat) / 2f)
            shader.setLocalMatrix(matrix)
            coverPaint.shader = shader
            canvas.drawRoundRect(cardRect, radius, radius, coverPaint)
            coverPaint.shader = null
        } else {
            // 生成式回退封面：从书名取色渐变 + 缩写
            val hue = (abs(book.title.hashCode()) % 360).toFloat()
            val topColor = Color.HSVToColor(floatArrayOf(hue, 0.42f, if (isDark) 0.30f else 0.62f))
            val bottomColor = Color.HSVToColor(floatArrayOf((hue + 28f) % 360f, 0.50f, if (isDark) 0.16f else 0.38f))
            val gradient = android.graphics.LinearGradient(
                0f, 0f, 0f, cardHeight, topColor, bottomColor, Shader.TileMode.CLAMP
            )
            coverPaint.shader = gradient
            canvas.drawRoundRect(cardRect, radius, radius, coverPaint)
            coverPaint.shader = null
            // 中心缩写文字
            val initial = book.title.trim().take(1).ifEmpty { "书" }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xEEFFFFFF.toInt()
                textSize = cardWidth * 0.30f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            }
            val textY = cardHeight / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(initial, cardWidth / 2f, textY, textPaint)
        }

        // 书脊高光（左缘亮线，模拟 3D 旋转受光面）
        val spineWidth = cardWidth * 0.045f
        val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, spineWidth * 2.4f, 0f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, spineWidth * 2.4f, cardHeight), radius, radius, spinePaint)

        // 中心卡底部进度指示条（accent 色）
        if (isCenter && book.readingProgress > 0f) {
            val barH = cardHeight * 0.020f
            val barW = cardWidth * book.readingProgress.coerceIn(0f, 1f)
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
            canvas.drawRoundRect(
                RectF(0f, cardHeight - barH, barW, cardHeight),
                barH / 2f, barH / 2f, barPaint
            )
        }
        canvas.restoreToCount(saveCount)
    }

    /**
     * Y 轴透视旋转（围绕当前画布原点，即卡片中心）。
     * 使用 android.graphics.Camera 默认相机距离，产生经典 CoverFlow 透视；
     * 若验证后需要更平缓的透视，可调小 setRotationY 前的插值或后续微调。
     */
    private fun applyPerspectiveRotation(canvas: Canvas, rotationY: Float) {
        val camera = android.graphics.Camera()
        camera.save()
        camera.rotateY(rotationY)
        val matrix = Matrix()
        camera.getMatrix(matrix)
        camera.restore()
        canvas.concat(matrix)
    }

    /** 加载封面：本地文件直读，https URL 下载并缓存到 filesDir/widget_covers/。 */
    private fun loadCoverBitmap(context: Context, coverPath: String?, targetSizePx: Int): Bitmap? {
        if (coverPath.isNullOrBlank() || targetSizePx <= 0) return null
        if (coverPath.startsWith("http://") || coverPath.startsWith("https://")) {
            val cached = downloadToCache(context, coverPath) ?: return null
            return decodeCoverFile(cached, targetSizePx)
        }
        return decodeCoverFile(coverPath, targetSizePx)
    }

    private fun downloadToCache(context: Context, url: String): File? {
        val cacheDir = File(context.filesDir, "widget_covers").apply { mkdirs() }
        val key = md5(url)
        val target = File(cacheDir, "$key.img")
        if (target.isFile && target.length() > 0) return target
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LumiBooksWidget")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }
            val tmp = File(cacheDir, "$key.tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { output ->
                    input.copyTo(output, 16 * 1024)
                }
            }
            conn.disconnect()
            // 校验确为图片再落盘
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(tmp.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                tmp.delete()
                return null
            }
            if (tmp.renameTo(target)) target else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun decodeCoverFile(path: String, targetSizePx: Int): Bitmap? {
        return decodeWidgetCover(path, targetSizePx)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isBlank()) return text
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end) + "…") > maxWidth) {
            end--
        }
        return text.take(end) + "…"
    }
}

/** 渲染器与 Provider 之间的轻量数据载体（与 CoverFlowBookData 字段对齐）。 */
internal data class CoverFlowBookDataCompat(
    val bookId: String,
    val title: String,
    val author: String,
    val coverPath: String?,
    val readingProgress: Float
) {
    companion object {
        fun from(books: List<com.huangder.lumibooks.data.local.model.CoverFlowBookData>): List<CoverFlowBookDataCompat> {
            return books.map {
                CoverFlowBookDataCompat(
                    bookId = it.bookId,
                    title = it.title,
                    author = it.author,
                    coverPath = it.coverPath,
                    readingProgress = it.readingProgress
                )
            }
        }
    }
}
