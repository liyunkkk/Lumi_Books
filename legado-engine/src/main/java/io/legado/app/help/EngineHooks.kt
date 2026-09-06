package io.legado.app.help

import io.legado.app.data.entities.BaseSource

/**
 * 引擎与宿主 UI 的可插拔桥接点。
 *
 * 引擎自身不依赖任何 Activity / Fragment / Compose 组件，凡是上游通过
 * 启动 Activity 完成的交互（图形验证码、内置浏览器、链接确认、在线导入）
 * 都在这里留出回调，由宿主在初始化时安装实现；未安装时行为退化为安全失败。
 */
object EngineHooks {

    /**
     * 打开图形验证码界面。
     * 宿主完成交互后需调用 [io.legado.app.help.source.SourceVerificationHelp.setResult]
     * 与 [io.legado.app.help.source.SourceVerificationHelp.checkResult] 唤醒引擎线程。
     */
    var verificationCodeHandler: ((source: BaseSource, imageUrl: String) -> Unit)? = null

    /**
     * 打开内置 WebView 浏览器。
     * @param saveResult 是否把网页源码回写为验证结果
     */
    var browserHandler: ((
        source: BaseSource,
        url: String,
        title: String,
        saveResult: Boolean,
        refetchAfterSuccess: Boolean,
        html: String?,
    ) -> Unit)? = null

    /** 书源 JS 调用 openUrl 打开外部链接，宿主可弹确认框 */
    var openUrlHandler: ((source: BaseSource?, url: String, mimeType: String?) -> Boolean)? = null

    /** 书源 JS 调用 legado:// / yuedu:// 在线导入 */
    var onlineImportHandler: ((url: String) -> Unit)? = null

    /** 主题模式，供书源 JS 读取：light / dark / auto */
    var themeModeProvider: (() -> String)? = null

    /** 主题配置快照，供书源 JS 读取 */
    var themeConfigProvider: (() -> Map<String, Any?>)? = null
}