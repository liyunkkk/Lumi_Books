package com.huangder.lumibooks.util.parser

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.ContentProcessor

/**
 * 正文净化规则引擎：负责章节正文与标题的广告清洗与 Legado 净化规则管道
 */
object PurifyEngine {

    /** 获取默认预置推荐规则列表（可供用户一键导入或在首次使用时初始化） */
    fun getPresetRules(): List<ReplaceRule> {
        val luoxiaPattern = "(?i)([\s·*_=-]*落[\s·*_=-]*霞[\s·*_=-]*读[\s·*_=-]*书[-=]*[a-zA-Z0-9.\s]*[-=]*|#\$--[a-zA-Z0-9.\s]+-=|luoxiadushu\.com|l\s+u\s+o\s+x\s+i\s+a\s+d\s+u\s+s\s+h\s+u|落[\s·*_=-]*霞[\s·*_=-]*读[\s·*_=-]*书)"
        val watermarkPattern = "(?i)(最新章节请访问|请记住本书首发域名|本章未完，请翻页|手机用户请浏览.*阅读|纯文字在线阅读)"
        return listOf(
            ReplaceRule(
                id = 10001L,
                name = "落霞读书去广告水印",
                group = "推荐预置",
                pattern = luoxiaPattern,
                replacement = "",
                isRegex = true,
                scopeContent = true,
                scopeTitle = false,
                isEnabled = true,
                order = 0
            ),
            ReplaceRule(
                id = 10002L,
                name = "通用防盗水印清理",
                group = "推荐预置",
                pattern = watermarkPattern,
                replacement = "",
                isRegex = true,
                scopeContent = true,
                scopeTitle = false,
                isEnabled = true,
                order = 1
            )
        )
    }

    /** 仅在数据库完全为空时初始化推荐规则 */
    fun ensureDefaultRulesIfEmpty() {
        try {
            val allRules = appDb.replaceRuleDao.all()
            if (allRules.isEmpty()) {
                getPresetRules().forEach {
                    appDb.replaceRuleDao.insert(it)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 纯文本净化：根据书名和域名执行已启用的 ReplaceRule
     */
    fun purifyText(rawContent: String, bookName: String = "", bookOrigin: String = ""): String {
        if (rawContent.isBlank()) return rawContent
        return try {
            val rules = appDb.replaceRuleDao.findEnabledByContentScope(bookName, bookOrigin)
            if (rules.isEmpty()) return rawContent
            var result = rawContent
            for (rule in rules) {
                if (!rule.isEnabled || rule.pattern.isEmpty()) continue
                result = if (rule.isRegex) {
                    try {
                        rule.regex.replace(result, rule.replacement)
                    } catch (_: Exception) {
                        result
                    }
                } else {
                    result.replace(rule.pattern, rule.replacement)
                }
            }
            result
        } catch (_: Exception) {
            rawContent
        }
    }

    /**
     * 标题净化：根据书名和域名执行标题替换规则
     */
    fun purifyTitle(rawTitle: String, bookName: String = "", bookOrigin: String = ""): String {
        if (rawTitle.isBlank()) return rawTitle
        return try {
            val rules = appDb.replaceRuleDao.findEnabledByTitleScope(bookName, bookOrigin)
            if (rules.isEmpty()) return rawTitle
            var result = rawTitle
            for (rule in rules) {
                if (!rule.isEnabled || rule.pattern.isEmpty()) continue
                result = if (rule.isRegex) {
                    try {
                        rule.regex.replace(result, rule.replacement)
                    } catch (_: Exception) {
                        result
                    }
                } else {
                    result.replace(rule.pattern, rule.replacement)
                }
            }
            result
        } catch (_: Exception) {
            rawTitle
        }
    }

    /**
     * 针对章节正文执行净化流水线（供 OnlineBookParser 与 ContentProcessor 调用）
     */
    fun purify(book: Book, chapter: BookChapter, rawContent: String): String {
        if (rawContent.isBlank()) return rawContent
        return try {
            val contentObj = ContentProcessor.get(book).getContent(
                book = book,
                chapter = chapter,
                content = rawContent,
                includeTitle = false,
                useReplace = true
            )
            if (contentObj.textList.isNotEmpty()) {
                contentObj.textList.joinToString("\n")
            } else {
                purifyText(rawContent, book.name, book.origin)
            }
        } catch (e: Exception) {
            purifyText(rawContent, book.name, book.origin)
        }
    }
}
