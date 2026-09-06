package io.legado.app.help.update

/**
 * Shim：上游用于区分安装渠道，引擎只在 AppConst.appInfo 里引用。
 */
enum class AppVariant {
    OFFICIAL,
    BETA_RELEASE,
    ALL,
    UNKNOWN,
}