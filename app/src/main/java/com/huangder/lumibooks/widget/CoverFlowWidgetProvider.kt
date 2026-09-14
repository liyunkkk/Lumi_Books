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
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.parseAppAccentArgb
import com.huangder.lumibooks.util.LocaleHelper
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.roundToInt

/**
 * 3D 景深在读精选（App 主页同款，等比缩小版）
 *
 * - 视觉：Book3DCoverRenderer 离屏渲染主页同款 3D 卡片流（0.68 宽高比 / ±20° rotationY / scale 0.88 / 露出 40%）
 * - 交互：左半区点击 = 上一本，右半区 = 下一本，中心 = 打开书；底部按钮 = 续读 / 书架 / 详情
 * - 封面：本地文件 + https URL 下载缓存（filesDir/widget_covers/）
 * - 外观：背景不透明度（0-100）与配色（跟随 App / 浅色 / 深色）由 DataStore 驱动
 */
class CoverFlowWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
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
        // 用户拖拽缩放小组件后，按新尺寸重新渲染 3D 舞台
        updateAsync(context, intArrayOf(appWidgetId))
    }

    private fun updateAsync(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        runWidgetUpdate(TAG) {
            val appContext = context.applicationContext
            val localizedContext = LocaleHelper.applyLanguage(appContext)
            val entryPoint = appContext.widgetDataEntryPoint()
            val bookDao = entryPoint.bookDao()
            val dataStoreManager = entryPoint.dataStoreManager()
            val rawBooks = bookDao.getRecentCoverFlowBooks(MAX_DISPLAY_BOOKS)
            val manager = AppWidgetManager.getInstance(appContext)

            // 主题与外观
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

            val density = appContext.resources.displayMetrics.density

            appWidgetIds.forEach { appWidgetId ->
                val views = RemoteViews(localizedContext.packageName, R.layout.widget_cover_flow)
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

                    // 按真实视图尺寸渲染（含 onAppWidgetOptionsChanged 后的新尺寸）
                    val options = manager.getAppWidgetOptions(appWidgetId)
                    var canvasWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    var canvasHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
                    if (canvasWidth <= 0) canvasWidth = (200 * density).roundToInt()
                    if (canvasHeight <= 0) canvasHeight = (140 * density).roundToInt()
                    // dp → px
                    canvasWidth = (canvasWidth * density).roundToInt()
                    canvasHeight = (canvasHeight * density).roundToInt()
                    // 底部操作条约 46dp，从舞台中扣除
                    canvasHeight = (canvasHeight - 46 * density).roundToInt().coerceAtLeast(120)

                    // 渲染主页同款 3D 景深位图（含 URL 封面下载缓存）
                    val coverFlowBitmap = Book3DCoverRenderer.render(
                        context = appContext,
                        books = CoverFlowBookDataCompat.from(rawBooks),
                        selectedIndex = selectedIndex,
                        widthPx = canvasWidth,
                        heightPx = canvasHeight,
                        accentColor = accentColor,
                        isDark = isDark,
                        backgroundOpacity = backgroundOpacity
                    )
                    views.setImageViewBitmap(R.id.widget_coverflow_canvas, coverFlowBitmap)

                    // 底部按钮染色（accent 主按钮）
                    val pillWidthPx = (84 * density).roundToInt().coerceAtLeast(96)
                    val pillHeightPx = (30 * density).roundToInt().coerceAtLeast(32)
                    val pillBitmap = createPillBitmap(pillWidthPx, pillHeightPx, accentColor, 15f * density)
                    views.setImageViewBitmap(R.id.widget_btn_continue_bg, pillBitmap)

                    // 翻书与打开
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

        private fun createPillBitmap(widthPx: Int, heightPx: Int, color: Int, radiusPx: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawRoundRect(rect, radiusPx, radiusPx, paint)
            return bitmap
        }
    }
}