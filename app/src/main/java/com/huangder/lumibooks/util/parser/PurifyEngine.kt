package com.huangder.lumibooks.util.parser

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.help.book.ContentProcessor
import java.util.regex.Pattern

/**
 * 正文净化规则引擎：集成落霞读书等网站专属广告清洗与 Legado 净化规则管道
 */
object PurifyEngine {
    private var initialized = false

    /** 预置落霞读书及主流小说站广告净化规则至 legado-engine 数据库 */
    fun ensureDefaultRules() {
        if (initialized) return
        initialized = true
        try {
            val allRules = appDb.replaceRuleDao.all()
            if (allRules.none { it.name.contains("落霞") }) {
                val luoxiaRule = ReplaceRule(
                    id = 10001L,
                    name = "落霞读书去广告水印",
                    group = "内置净化",
                    pattern = """(?i)([\x{1F415}\x{1F436}]?\s*落[\s·*_\-=]*霞[\s·*_\-=]*读[\s·*_\-=]*书[\-=]*[a-zA-Z0-9\.\s]*[\-=]*|#\$\-\-[a-zA-Z0-9\.\s]+\-=|luoxiadushu\.com|l\s*u\s*o\s*x\s*i\s*a\s*d\s*u\s*s\s*h\s*u|落[\s·*_\-=]*霞[\s·*_\-=]*读[\s·*_\-=]*书)""",
                    replacement = "",
                    isRegex = true,
                    scopeContent = true,
                    scopeTitle = false,
                    isEnabled = true,
                    order = 0
                )
                appDb.replaceRuleDao.insert(luoxiaRule)
            }
            if (allRules.none { it.name.contains("通用防盗水印") }) {
                val watermarkRule = ReplaceRule(
                    id = 10002L,
                    name = "通用防盗水印清理",
                    group = "内置净化",
                    pattern = """(?i)(最新章节请访问|请记住本书首发域名|本章未完，请翻页|手机用户请浏览.*阅读|纯文字在线阅读)""",
                    replacement = "",
                    isRegex = true,
                    scopeContent = true,
                    scopeTitle = false,
                    isEnabled = true,
                    order = 1
                )
                appDb.replaceRuleDao.insert(watermarkRule)
            }
        } catch (_: Exception) {}
    }

    /**
     * 针对章节正文执行深度多重净化处理
     */
    fun purify(book: Book, chapter: BookChapter, rawContent: String): String {
        if (rawContent.isBlank()) return rawContent
        ensureDefaultRules()

        // 1. 本地特征级正则过滤：彻底滤除落霞读书打散域名、狗头 Emoji、特殊包装符号
        val luoxiaLinePattern = Pattern.compile(
            """(?i)([\x{1F415}\x{1F436}]?\s*落[\s·*_\-=]*霞[\s·*_\-=]*读[\s·*_\-=]*书[\-=\s]*[a-zA-Z0-9\.\s]*[\-=\s]*|#\$\-\-[a-zA-Z0-9\.\s]+\-=|l\s*u\s*o\s*x\s*i\s*a\s*d\s*u\s*s\s*h\s*u(\s*\.\s*c\s*o\s*m)?|luoxiadushu(\.com)?)"""
        )
        val symbolDecoratorPattern = Pattern.compile("""(?i)(^[\-=#\$\s]{2,}|[\-=#\$\s]{2,}$)""")

        val lines = rawContent.lines()
        val cleanedLines = mutableListOf<String>()

        for (line in lines) {
            var l = line.trim()
            if (l.isEmpty()) continue

            // 若匹配到落霞广告特征则剥离广告内容
            val matcher = luoxiaLinePattern.matcher(l)
            if (matcher.find()) {
                l = matcher.replaceAll("").trim()
            }
            l = symbolDecoratorPattern.matcher(l).replaceAll("").trim()

            // 剔除后若为正常内容则保留，若只剩极短垃圾字符则丢弃
            if (l.isNotBlank() && l.length > 1) {
                cleanedLines.add(l)
            }
        }

        val preCleaned = cleanedLines.joinToString("\n")

        // 2. 串联 Legado 引擎 ContentProcessor 净化流水线（执行 ReplaceRule、分段重排、重复标题消除等）
        return try {
            val contentObj = ContentProcessor.get(book).getContent(
                book = book,
                chapter = chapter,
                content = preCleaned,
                includeTitle = false,
                useReplace = true
            )
            if (contentObj.textList.isNotEmpty()) {
                contentObj.textList.joinToString("\n")
            } else {
                preCleaned
            }
        } catch (e: Exception) {
            preCleaned
        }
    }
}
