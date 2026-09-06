package io.legado.app.help.book

import io.legado.app.data.entities.ReplaceRule

/**
 * 正文处理结果。
 * 相对上游去掉了 effectiveContentProcesses（依赖 domain 层的 BookContentProcess 引擎）。
 */
data class BookContent(
    val sameTitleRemoved: Boolean,
    val textList: List<String>,
    //起效的替换规则
    val effectiveReplaceRules: List<ReplaceRule>?,
) {
    override fun toString(): String {
        return textList.joinToString("\n")
    }
}