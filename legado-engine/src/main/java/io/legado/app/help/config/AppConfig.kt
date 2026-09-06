@file:Suppress("unused")

package io.legado.app.help.config

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 引擎侧配置门面（Shim）。
 *
 * 上游 AppConfig 依赖 20+ 个 UI 设置页对象与 Koin/DataStore 仓库；
 * 引擎只需要其中 15 个字段，这里全部落到 SharedPreferences，
 * 宿主 App 可通过同名 key 读写，避免拖入任何 UI 依赖。
 */
object AppConfig {

    var userAgent: String
        get() = appCtx.getPrefString(PreferKey.userAgent)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_USER_AGENT
        set(value) = appCtx.putPrefString(PreferKey.userAgent, value)

    /** 并发线程数，供 mapAsync 使用 */
    var threadCount: Int
        get() = appCtx.getPrefInt(PreferKey.threadCount, 16).coerceIn(1, 64)
        set(value) = appCtx.putPrefInt(PreferKey.threadCount, value)

    var recordLog: Boolean
        get() = appCtx.getPrefBoolean("recordLog", false)
        set(value) = appCtx.putPrefBoolean("recordLog", value)

    var recordHeapDump: Boolean
        get() = appCtx.getPrefBoolean("recordHeapDump", false)
        set(value) = appCtx.putPrefBoolean("recordHeapDump", value)

    /** 引擎不集成 Cronet，固定关闭 */
    val isCronet: Boolean get() = false

    var replaceEnableDefault: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.replaceEnableDefault, true)
        set(value) = appCtx.putPrefBoolean(PreferKey.replaceEnableDefault, value)

    var chineseConverterType: Int
        get() = appCtx.getPrefInt("chineseConverterType", 0)
        set(value) = appCtx.putPrefInt("chineseConverterType", value)

    var adaptSpecialStyle: Boolean
        get() = appCtx.getPrefBoolean("adaptSpecialStyle", false)
        set(value) = appCtx.putPrefBoolean("adaptSpecialStyle", value)

    var importBookPath: String?
        get() = appCtx.getPrefString("importBookPath")
        set(value) = appCtx.putPrefString("importBookPath", value)

    var backupPath: String?
        get() = appCtx.getPrefString("backupPath")
        set(value) = appCtx.putPrefString("backupPath", value)

    var bookExportFileName: String?
        get() = appCtx.getPrefString("bookExportFileName")
        set(value) = appCtx.putPrefString("bookExportFileName", value)

    var episodeExportFileName: String?
        get() = appCtx.getPrefString("episodeExportFileName")
        set(value) = appCtx.putPrefString("episodeExportFileName", value)

    var bookshelfLayoutModePortrait: Int
        get() = appCtx.getPrefInt("bookshelfLayoutModePortrait", 0)
        set(value) = appCtx.putPrefInt("bookshelfLayoutModePortrait", value)

    var bookshelfLayoutModeLandscape: Int
        get() = appCtx.getPrefInt("bookshelfLayoutModeLandscape", 0)
        set(value) = appCtx.putPrefInt("bookshelfLayoutModeLandscape", value)

    var bookshelfLayoutGridPortrait: Int
        get() = appCtx.getPrefInt("bookshelfLayoutGridPortrait", 3)
        set(value) = appCtx.putPrefInt("bookshelfLayoutGridPortrait", value)

    var bookshelfLayoutGridLandscape: Int
        get() = appCtx.getPrefInt("bookshelfLayoutGridLandscape", 5)
        set(value) = appCtx.putPrefInt("bookshelfLayoutGridLandscape", value)

    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"
}

/**
 * 引擎里被 UI 设置页引用的配置项，统一收敛到 AppConfig 上的薄壳。
 * 保留原类名/字段名，使引擎源文件的调用点无需改动。
 */
object OtherConfig {
    val threadCount: Int get() = AppConfig.threadCount
    val recordLog: Boolean get() = AppConfig.recordLog
    val recordHeapDump: Boolean get() = AppConfig.recordHeapDump
    val replaceEnableDefault: Boolean get() = AppConfig.replaceEnableDefault
    val antiAlias: Boolean get() = true
    val defaultBookTreeUri: String? get() = AppConfig.importBookPath
    val firebaseEnable: Boolean get() = false
    val webServiceAutoStart: Boolean get() = false
    val showAddToShelfAlert: Boolean get() = false
    val sourceEditMaxLine: Int get() = 99
    val webPort: Int get() = 1122
    val updateToVariant: String get() = "default"
}

object DownloadCacheConfig {
    val userAgent: String get() = AppConfig.userAgent
    val threadCount: Int get() = AppConfig.threadCount
    val cronetEnable: Boolean get() = false
    val imageRetainNum: Int get() = 0
    val preDownloadNum: Int get() = 10
    val bitmapCacheSize: Int get() = 50
}

object ReadConfig {
    val chineseConverterType: Int get() = AppConfig.chineseConverterType
    val adaptSpecialStyle: Boolean get() = AppConfig.adaptSpecialStyle
    val useUnderline: Boolean get() = false
    val tocCountWords: Boolean get() = true
    val tocUiUseReplace: Boolean get() = true
    val defaultSourceChangeAll: Boolean get() = false
    val autoChangeSource: Boolean get() = true
}