package com.huangder.lumibooks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.model.CoverFlowBookData
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.parseAppAccentArgb
import com.huangder.lumibooks.util.LocaleHelper
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class CoverFlowWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_PREV -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    cycleWidgetIndex(context, appWidgetId, -1)
                }
                return
            }
            ACTION_NEXT -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    cycleWidgetIndex(context, appWidgetId, 1)
                }
                return
            }
            ACTION_SELECT -> {
                val targetIndex = intent.getIntExtra(EXTRA_TARGET_INDEX, -1)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && targetIndex >= 0) {
                    setWidgetIndex(context, appWidgetId, targetIndex)
                }
                return
            }
            ACTION_REFRESH -> {
                updateAsync(context, widgetIds(context, CoverFlowWidgetProvider::class.java))
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAsync(context, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runWidgetUpdate(TAG) {
            updateInternal(context, appWidgetIds)
        }
    }

    private suspend fun updateInternal(context: Context, appWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val localizedContext = LocaleHelper.applyLanguage(appContext)
        val entryPoint = appContext.widgetDataEntryPoint()
        val bookDao = entryPoint.bookDao()
        val noteDao = entryPoint.noteDao()
        val dataStoreManager = entryPoint.dataStoreManager()
        val rawBooks = bookDao.getRecentCoverFlowBooks(MAX_DISPLAY_BOOKS)

        val accentHex = dataStoreManager.appAccentColor.firstOrNull() ?: DEFAULT_APP_ACCENT_HEX
        val accentColor = try {
            parseAppAccentArgb(accentHex)
        } catch (_: Throwable) {
            Color.parseColor(DEFAULT_APP_ACCENT_HEX)
        }

        val widgetThemeMode = dataStoreManager.widgetThemeMode.firstOrNull() ?: "follow_app"
        val backgroundOpacity = dataStoreManager.widgetBackgroundOpacity.firstOrNull() ?: 100
        val appDarkModeSetting = dataStoreManager.darkMode.firstOrNull().orEmpty()
        val systemDark = (appContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (widgetThemeMode) {
            "dark" -> true
            "light" -> false
            else -> when (appDarkModeSetting) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }
        }

        val manager = AppWidgetManager.getInstance(appContext)
        val density = appContext.resources.displayMetrics.density

        val pillWidthPx = (80 * density).roundToInt().coerceAtLeast(84)
        val pillHeightPx = (26 * density).roundToInt().coerceAtLeast(28)
        val pillBitmap = createPillBitmap(pillWidthPx, pillHeightPx, accentColor, 13f * density)

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(localizedContext.packageName, R.layout.widget_cover_flow)
            val options = manager.getAppWidgetOptions(appWidgetId)
            var widgetDpWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            var widgetDpHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            if (widgetDpWidth <= 0) widgetDpWidth = 320
            if (widgetDpHeight <= 0) widgetDpHeight = 140

            val totalWidthPx = (widgetDpWidth * density).roundToInt().coerceAtLeast(300)
            val totalHeightPx = (widgetDpHeight * density).roundToInt().coerceAtLeast(140)

            // 1. 动态生成可调透明度与主题模式的卡片背景（0-100% 透明度）
            val bgBitmap = createCardBackgroundBitmap(
                widthPx = totalWidthPx,
                heightPx = totalHeightPx,
                opacityPercent = backgroundOpacity,
                isDark = isDark,
                density = density
            )
            views.setImageViewBitmap(R.id.widget_card_bg_dynamic, bgBitmap)

            if (rawBooks.isEmpty()) {
                views.setViewVisibility(R.id.widget_coverflow_content, View.GONE)
                views.setViewVisibility(R.id.widget_coverflow_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_coverflow_empty,
                    localizedContext.getString(R.string.widget_no_recent_book)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_coverflow_root,
                    openBookshelfPendingIntent(appContext, EMPTY_REQUEST_CODE_BASE + appWidgetId)
                )
            } else {
                views.setViewVisibility(R.id.widget_coverflow_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_coverflow_empty, View.GONE)

                val prefs = getWidgetPrefs(appContext)
                val storedIndex = prefs.getInt(keyForIndex(appWidgetId), 0)
                val selectedIndex = storedIndex.coerceIn(0, rawBooks.size - 1)
                val currentBook = rawBooks[selectedIndex]

                // 左侧 3D 书架舞台分辨率（约占总宽度的 46%）
                val canvasWidthPx = ((totalWidthPx - 24 * density) * 0.46f).roundToInt().coerceIn(240, 720)
                val canvasHeightPx = (totalHeightPx - 24 * density).roundToInt().coerceIn(160, 480)

                // 2. 绘制 5 书 3D 展台（图二经典视觉）
                val coverFlowBitmap = Book3DCoverRenderer.render(
                    context = appContext,
                    books = rawBooks,
                    selectedIndex = selectedIndex,
                    widthPx = canvasWidthPx,
                    heightPx = canvasHeightPx,
                    accentColor = accentColor,
                    isDark = isDark
                )
                views.setImageViewBitmap(R.id.widget_coverflow_canvas, coverFlowBitmap)

                // 3. 右侧书籍详细信息
                val rawQuote = noteDao.getLatestQuoteForBook(currentBook.bookId)
                val quote = resolveQuote(rawQuote, currentBook.title)
                val chapter = extractChapterInfo(currentBook.locatorJson, currentBook.readingProgress)
                val progressVal = currentBook.readingProgress.coerceIn(0f, 1f) * 100f
                val progressStr = if (progressVal > 0f && progressVal < 100f && progressVal % 1f != 0f) {
                    String.format(Locale.getDefault(), "已读 %.1f%%", progressVal)
                } else {
                    "已读 ${progressVal.roundToInt()}%"
                }

                views.setTextViewText(R.id.widget_book_title, currentBook.title)
                views.setTextViewText(
                    R.id.widget_book_author,
                    if (currentBook.author.isNotBlank()) "${currentBook.author} 著" else ""
                )
                views.setTextViewText(R.id.widget_book_quote, quote)
                views.setTextViewText(R.id.widget_progress_text, progressStr)
                views.setTextViewText(R.id.widget_chapter_text, chapter)

                // 4. 水平进度条
                views.setProgressBar(
                    R.id.widget_progress_bar,
                    1000,
                    (currentBook.readingProgress.coerceIn(0f, 1f) * 1000f).roundToInt(),
                    false
                )

                // 5. 主按钮胶囊强调色染色
                views.setImageViewBitmap(R.id.widget_btn_continue_bg, pillBitmap)

                // 6. 隐形触摸区点击事件（左/右切书，中打开，纯透明绝无大箭头）
                views.setOnClickPendingIntent(
                    R.id.widget_touch_prev,
                    coverFlowActionPendingIntent(appContext, appWidgetId, ACTION_PREV)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_touch_next,
                    coverFlowActionPendingIntent(appContext, appWidgetId, ACTION_NEXT)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_touch_read,
                    openBookPendingIntent(appContext, READ_TOUCH_REQUEST_CODE_BASE + appWidgetId, currentBook.bookId)
                )

                // 7. 顶部及右侧操作按钮点击事件
                views.setOnClickPendingIntent(
                    R.id.widget_header_tag,
                    openBookshelfPendingIntent(appContext, HEADER_REQUEST_CODE_BASE + appWidgetId)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_history,
                    openBookDetailsPendingIntent(appContext, HISTORY_REQUEST_CODE_BASE + appWidgetId, currentBook.bookId)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_more,
                    coverFlowActionPendingIntent(appContext, appWidgetId, ACTION_NEXT)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_continue_wrap,
                    openBookPendingIntent(appContext, CONTINUE_REQUEST_CODE_BASE + appWidgetId, currentBook.bookId)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_bookshelf,
                    openBookshelfPendingIntent(appContext, BOOKSHELF_REQUEST_CODE_BASE + appWidgetId)
                )
                views.setOnClickPendingIntent(
                    R.id.widget_btn_details,
                    openBookDetailsPendingIntent(appContext, DETAILS_REQUEST_CODE_BASE + appWidgetId, currentBook.bookId)
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun cycleWidgetIndex(context: Context, appWidgetId: Int, delta: Int) {
        val appContext = context.applicationContext
        runWidgetUpdate(TAG) {
            val entryPoint = appContext.widgetDataEntryPoint()
            val count = entryPoint.bookDao().getRecentCoverFlowBooks(MAX_DISPLAY_BOOKS).size
            if (count > 0) {
                val prefs = getWidgetPrefs(appContext)
                val current = prefs.getInt(keyForIndex(appWidgetId), 0)
                val next = (current + delta + count) % count
                prefs.edit().putInt(keyForIndex(appWidgetId), next).apply()
                updateInternal(appContext, intArrayOf(appWidgetId))
            }
        }
    }

    private fun setWidgetIndex(context: Context, appWidgetId: Int, targetIndex: Int) {
        val appContext = context.applicationContext
        val prefs = getWidgetPrefs(appContext)
        prefs.edit().putInt(keyForIndex(appWidgetId), targetIndex).apply()
        runWidgetUpdate(TAG) {
            updateInternal(appContext, intArrayOf(appWidgetId))
        }
    }

    companion object {
        private const val TAG = "CoverFlowWidget"
        const val ACTION_REFRESH = "com.huangder.lumibooks.widget.action.REFRESH_COVER_FLOW"
        const val ACTION_PREV = "com.huangder.lumibooks.widget.action.COVER_FLOW_PREV"
        const val ACTION_NEXT = "com.huangder.lumibooks.widget.action.COVER_FLOW_NEXT"
        const val ACTION_SELECT = "com.huangder.lumibooks.widget.action.COVER_FLOW_SELECT"
        const val EXTRA_TARGET_INDEX = "extra_target_index"

        private const val MAX_DISPLAY_BOOKS = 5
        private const val PREFS_NAME = "cover_flow_widget_prefs"
        private fun keyForIndex(widgetId: Int) = "index_$widgetId"

        private const val EMPTY_REQUEST_CODE_BASE = 60_000
        private const val HEADER_REQUEST_CODE_BASE = 70_000
        private const val HISTORY_REQUEST_CODE_BASE = 80_000
        private const val READ_TOUCH_REQUEST_CODE_BASE = 90_000
        private const val CONTINUE_REQUEST_CODE_BASE = 100_000
        private const val BOOKSHELF_REQUEST_CODE_BASE = 110_000
        private const val DETAILS_REQUEST_CODE_BASE = 120_000

        private fun getWidgetPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, CoverFlowWidgetProvider::class.java)
            if (manager.getAppWidgetIds(component).isEmpty()) return
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setComponent(component)
            )
        }

        private fun resolveQuote(latestNote: String?, bookTitle: String): String {
            val clean = latestNote?.trim()
                ?.removePrefix("“")?.removeSuffix("”")
                ?.removePrefix(""")?.removeSuffix(""")
            if (!clean.isNullOrBlank()) {
                val trimmed = clean.take(48)
                return "“$trimmed${if (clean.length > 48) "…" else ""}”"
            }
            val curated = listOf(
                "生活不能等待别人来安排，要自己去争取和奋斗。",
                "读一本好书，就是在和许多高尚的人谈话。",
                "世间万物皆有灵性，唯有读书使心灵开阔。",
                "行万里路，读万卷书。",
                "愿每一页文字，都能抚平浮躁与疲惫。",
                "在书页之间，与未知的自我重逢。"
            )
            val index = abs(bookTitle.hashCode()) % curated.size
            return "“${curated[index]}”"
        }

        private fun extractChapterInfo(locatorJson: String?, readingProgress: Float): String {
            if (!locatorJson.isNullOrBlank()) {
                try {
                    val json = JSONObject(locatorJson)
                    val title = json.optString("title").trim()
                    if (title.isNotEmpty()) {
                        return if (title.length > 14) title.take(13) + "…" else title
                    }
                    val loc = json.optJSONObject("locations")
                    if (loc != null) {
                        val pos = loc.optInt("position", -1)
                        val total = loc.optInt("total", -1)
                        if (pos > 0 && total > 0) return "第 $pos / $total 章"
                        if (pos > 0) return "第 $pos 章"
                    }
                } catch (_: Throwable) {}
            }
            return "在读精选"
        }

        private fun createPillBitmap(widthPx: Int, heightPx: Int, color: Int, radiusPx: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
            }
            canvas.drawRoundRect(
                RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()),
                radiusPx,
                radiusPx,
                paint
            )
            return bitmap
        }

        private fun createCardBackgroundBitmap(
            widthPx: Int,
            heightPx: Int,
            opacityPercent: Int,
            isDark: Boolean,
            density: Float
        ): Bitmap {
            val w = widthPx.coerceAtLeast(20)
            val h = heightPx.coerceAtLeast(20)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            if (opacityPercent <= 0) return bitmap
            val canvas = Canvas(bitmap)
            val baseAlpha = (opacityPercent.coerceIn(0, 100) * 255f / 100f)
            // 严格对齐 HyperOS / MIUI 4x2 官方大文件夹的标准大圆角（28dp）
            val radius = 28f * density
            val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())

            // 1. 液态玻璃基底（Liquid Glass Substrate）：55%~65% 灵动通透，透出桌面壁纸
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = if (isDark) {
                    android.graphics.LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        intArrayOf(
                            Color.argb((baseAlpha * 0.68f).roundToInt(), 36, 40, 50),
                            Color.argb((baseAlpha * 0.58f).roundToInt(), 22, 26, 34),
                            Color.argb((baseAlpha * 0.50f).roundToInt(), 16, 18, 26)
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                } else {
                    android.graphics.LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        intArrayOf(
                            Color.argb((baseAlpha * 0.65f).roundToInt(), 255, 255, 255),
                            Color.argb((baseAlpha * 0.48f).roundToInt(), 245, 248, 254),
                            Color.argb((baseAlpha * 0.38f).roundToInt(), 232, 238, 248)
                        ),
                        floatArrayOf(0f, 0.50f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            // 2. 顶部微光晶体高光线（Top Specular Ridge Highlight）——透光液态玻璃标志性微光
            val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.0f * density
                shader = android.graphics.LinearGradient(
                    0f, 0f, w.toFloat(), 0f,
                    intArrayOf(
                        Color.TRANSPARENT,
                        if (isDark) Color.argb((baseAlpha * 0.45f).roundToInt(), 255, 255, 255)
                        else Color.argb((baseAlpha * 0.65f).roundToInt(), 255, 255, 255),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0.12f, 0.5f, 0.88f),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            val highlightRect = RectF(rect).apply { inset(0.8f * density, 0.8f * density) }
            canvas.drawRoundRect(highlightRect, radius - 0.8f * density, radius - 0.8f * density, highlightPaint)

            // 3. 全局柔和微光双色晶体微边框（Dual-Tone Crystal Rim）
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.75f * density
                shader = if (isDark) {
                    android.graphics.LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        intArrayOf(
                            Color.argb((baseAlpha * 0.42f).roundToInt(), 255, 255, 255),
                            Color.argb((baseAlpha * 0.16f).roundToInt(), 180, 200, 220),
                            Color.argb((baseAlpha * 0.28f).roundToInt(), 210, 225, 255)
                        ),
                        floatArrayOf(0f, 0.6f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                } else {
                    android.graphics.LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        intArrayOf(
                            Color.argb((baseAlpha * 0.75f).roundToInt(), 255, 255, 255),
                            Color.argb((baseAlpha * 0.25f).roundToInt(), 210, 220, 235),
                            Color.argb((baseAlpha * 0.45f).roundToInt(), 255, 255, 255)
                        ),
                        floatArrayOf(0f, 0.6f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
            }
            val borderRect = RectF(rect).apply { inset(0.4f * density, 0.4f * density) }
            canvas.drawRoundRect(borderRect, radius - 0.4f * density, radius - 0.4f * density, strokePaint)
            return bitmap
        }
    }
}
