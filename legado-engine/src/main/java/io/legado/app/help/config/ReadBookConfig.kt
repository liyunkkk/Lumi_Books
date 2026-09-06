@file:Suppress("unused")

package io.legado.app.help.config

import androidx.annotation.Keep
import io.legado.app.constant.PageAnim
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 阅读排版配置（Shim）。
 *
 * 上游 ReadBookConfig 依赖 Koin 仓库、Compose State、主题图片与 20+ 个 UI 设置项；
 * 引擎只需要「段落缩进」「翻页动画」以及供书源 JS 读取的 durConfig 快照，
 * 因此这里只保留可序列化的纯数据配置，宿主 Compose 阅读器另有自己的排版状态。
 */
@Keep
object ReadBookConfig {

    /** 段落缩进，ContentProcessor 拼接正文时使用 */
    var paragraphIndent: String
        get() = appCtx.getPrefString("paragraphIndent") ?: "　　"
        set(value) = appCtx.putPrefString("paragraphIndent", value)

    /** 翻页动画，Book.getPageAnim() 的兜底值 */
    var pageAnim: Int
        get() = appCtx.getPrefInt("pageAnim", PageAnim.coverPageAnim)
        set(value) = appCtx.putPrefInt("pageAnim", value)

    var textSize: Int
        get() = appCtx.getPrefInt("textSize", 20).coerceIn(5, 320)
        set(value) = appCtx.putPrefInt("textSize", value)

    var lineSpacingExtra: Int
        get() = appCtx.getPrefInt("lineSpacingExtra", 12)
        set(value) = appCtx.putPrefInt("lineSpacingExtra", value)

    var paragraphSpacing: Int
        get() = appCtx.getPrefInt("paragraphSpacing", 2)
        set(value) = appCtx.putPrefInt("paragraphSpacing", value)

    /** 供书源 JS 读取的配置快照 */
    val durConfig: Config
        get() = Config(
            paragraphIndent = paragraphIndent,
            pageAnim = pageAnim,
            textSize = textSize,
            lineSpacingExtra = lineSpacingExtra,
            paragraphSpacing = paragraphSpacing,
        )

    @Keep
    data class Config(
        val paragraphIndent: String,
        val pageAnim: Int,
        val textSize: Int,
        val lineSpacingExtra: Int,
        val paragraphSpacing: Int,
    ) {
        fun toMap(): Map<String, Any> = mapOf(
            "paragraphIndent" to paragraphIndent,
            "pageAnim" to pageAnim,
            "textSize" to textSize,
            "lineSpacingExtra" to lineSpacingExtra,
            "paragraphSpacing" to paragraphSpacing,
        )
    }
}
