package com.huangder.lumibooks.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.model.CoverFlowBookData
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.parseAppAccentArgb
import com.huangder.lumibooks.util.LocaleHelper
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject
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

    private fun updateAsync(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runWidgetUpdate(TAG) {
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

            val darkModeSetting = dataStoreManager.darkMode.firstOrNull().orEmpty()
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val isDark = when (darkModeSetting) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            val manager = AppWidgetManager.getInstance(appContext)
            val density = appContext.resources.displayMetrics.density
            val canvasWidthPx = (240 * density).roundToInt()
            val canvasHeightPx = (170 * density).roundToInt()

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(
                    localizedContext.packageName,
                    R.layout.widget_cover_flow
                )

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

                    // 1. Render 3D Cover Flow Bitmap
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

                    // 2. Resolve Quote & Chapter info
                    val rawQuote = noteDao.getLatestQuoteForBook(currentBook.bookId)
                    val quote = resolveQuote(rawQuote, currentBook.title)
                    val chapter = extractChapterInfo(currentBook.locatorJson, currentBook.readingProgress)
                    val percent = (currentBook.readingProgress.coerceIn(0f, 1f) * 100f).roundToInt()

                    // 3. Set text views
                    views.setTextViewText(R.id.widget_book_title, currentBook.title)
                    views.setTextViewText(R.id.widget_book_author, if (currentBook.author.isNotBlank()) "${currentBook.author} 著" else "")
                    views.setTextViewText(R.id.widget_book_quote, quote)
                    views.setTextViewText(R.id.widget_progress_text, "已读 $percent%")
                    views.setTextViewText(R.id.widget_chapter_text, chapter)

                    // 4. Progress bar
                    views.setProgressBar(
                        R.id.widget_progress_bar,
                        1000,
                        (currentBook.readingProgress.coerceIn(0f, 1f) * 1000f).roundToInt(),
                        false
                    )

                    // 5. Button accent tint
                    views.setInt(R.id.widget_btn_continue_bg, "setColorFilter", accentColor)

                    // 6. Clicks & PendingIntents
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

                    // Touch zones on the 3D cover flow
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

                    // Bottom Action Buttons
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
                updateAsync(appContext, intArrayOf(appWidgetId))
            }
        }
    }

    private fun setWidgetIndex(context: Context, appWidgetId: Int, targetIndex: Int) {
        val appContext = context.applicationContext
        val prefs = getWidgetPrefs(appContext)
        prefs.edit().putInt(keyForIndex(appWidgetId), targetIndex).apply()
        updateAsync(appContext, intArrayOf(appWidgetId))
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
                ?.removePrefix("\"")?.removeSuffix("\"")
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
    }
}