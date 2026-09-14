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
        val pillHeightPx = (30 * density).roundToInt().coerceAtLeast(32)
        val pillBitmap = createPillBitmap(pillWidthPx, pillHeightPx, accentColor, 15f * density)

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
                val progressStr = if (progressVal > 0f && progressVal < 1f) {
                    String.format(Locale.getDefault(), "已读 %.1f%%", progressVal)
                } else if (progressVal >= 1f && progressVal % 1f != 0f && progressVal < 100f) {
                    String.format(Locale.getDefault(), "已读 %.1f%%", progressVal)
                } else {
                    "已读 ${progressVal.roundToInt()}%出"
                }
                val cleanProgressStr = progressStr.replace("\出", "")

                views.setTextViewText(R.id.widget_book_title, currentBook.title)
                views.setTextViewText(
                    R.id.widget_book_author,
                    if (currentBook.author.isNotBlank()) "${currentBook.author} 著" else ""
                )
                views.setTextViewText(R.id.widget_book_quote, quote)
                views.setTextViewText(R.id.widget_progress_text, cleanProgressStr)
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
            val percent = (readingProgress.coerceIn(0f, 1f) * 100f).roundToInt()
            return if (percent > 0) "已读 $percent%" else "未开始阅读"
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
            val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(10), heightPx.coerceAtLeast(10), Bitmap.Config.ARGB_8888)
            if (opacityPercent <= 0) return bitmap
            val canvas = Canvas(bitmap)
            val alpha = (opacityPercent.coerceIn(0, 100) * 255f / 100f).roundToInt()
            val radius = 24f * density
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isDark) {
                    Color.argb(alpha, 28, 32, 40)
                } else {
                    Color.argb(alpha, 248, 249, 251)
                }
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                color = if (isDark) {
                    Color.argb((alpha * 0.4f).roundToInt(), 255, 255, 255)
                } else {
                    Color.argb((alpha * 0.35f).roundToInt(), 215, 220, 226)
                }
            }
            val insetRect = RectF(rect).apply { inset(0.5f * density, 0.5f * density) }
            canvas.drawRoundRect(insetRect, radius, radius, strokePaint)
            return bitmap
        }
    }
}
