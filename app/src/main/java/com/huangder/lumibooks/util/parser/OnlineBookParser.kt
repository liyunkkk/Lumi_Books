package com.huangder.lumibooks.util.parser

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookHelp
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * 在线书源解析器：实现 Lumi BookParser 接口，正文按章懒加载。
 *
 * filePath 约定："legado-online://<engineBookUrl>"。
 * parse() 拉取目录（本地缓存优先秒开，缺失时带超时熔断网络拉取并持久化），getChapterContent() 拉取正文。
 */
class OnlineBookParser(
    private val engineBookUrl: String
) : BookParser {
    companion object {
        const val SCHEME = "legado-online://"
        private const val NETWORK_TIMEOUT_MS = 15_000L
    }

    private var chapters: List<BookChapter> = emptyList()
    private val contentCache = ConcurrentHashMap<Int, String>()

    private fun requireBookSource() = appDb.bookSourceDao.getBookSource(
        appDb.bookDao.getBook(engineBookUrl)?.origin ?: ""
    ) ?: throw IllegalStateException("书源不存在或已删除")

    private fun requireEngineBook() = appDb.bookDao.getBook(engineBookUrl)
        ?: throw IllegalStateException("在线书记录不存在，请重新加入书架")

    override fun parse(filePath: String): BookContent {
        val book = requireEngineBook()
        if (book.tocUrl.isNullOrBlank()) {
            book.tocUrl = book.bookUrl
            try { appDb.bookDao.update(book) } catch (_: Exception) {}
        }

        // 1. 优先读取本地数据库章节缓存（实现秒开，避免重复同步请求网络）
        var cachedChapters = appDb.bookChapterDao.getChapterList(engineBookUrl)
        if (cachedChapters.isEmpty() && book.bookUrl != engineBookUrl) {
            cachedChapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
        }

        val list = if (cachedChapters.isNotEmpty()) {
            cachedChapters
        } else {
            // 2. 本地无缓存时，发起网络请求拉取目录，带 15 秒超时熔断
            val source = requireBookSource()
            val fetchedList = runBlocking {
                withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    WebBook.getChapterListAwait(source, book).getOrNull()
                }
            } ?: throw IllegalStateException("加载目录超时或失败，请检查网络连接或更换书源")

            if (fetchedList.isEmpty()) {
                throw IllegalStateException("书源未返回任何章节内容")
            }

            // 3. 将拉取到的目录存入本地数据库，下次打开立即可读
            try {
                fetchedList.forEach { it.bookUrl = engineBookUrl }
                appDb.bookChapterDao.delByBook(engineBookUrl)
                appDb.bookChapterDao.insert(fetchedList)
                book.totalChapterNum = fetchedList.size
                appDb.bookDao.update(book)
            } catch (e: Exception) {
                android.util.Log.e("OnlineBookParser", "Failed to cache chapters to db", e)
            }
            fetchedList
        }

        chapters = list
        val chapterList = list.map {
            Chapter(index = it.index, title = it.title, content = "")
        }
        val tocEntries = list.map {
            TocEntry(title = it.title, level = if (it.isVolume) 1 else 1, chapterIndex = it.index)
        }
        return BookContent(
            title = book.name,
            author = book.author,
            chapters = chapterList,
            coverPath = book.coverUrl,
            tocEntries = tocEntries
        )
    }

    override fun getChapterContent(chapterIndex: Int): CharSequence {
        contentCache[chapterIndex]?.let { return it }
        val chapter = chapters.getOrNull(chapterIndex)
            ?: return "【章节索引越界: $chapterIndex】"

        val book = try {
            requireEngineBook()
        } catch (e: Exception) {
            return "【书籍信息加载失败: ${e.message}】"
        }
        val source = try {
            requireBookSource()
        } catch (e: Exception) {
            return "【书源不存在或已失效】"
        }

        if (book.tocUrl.isNullOrBlank()) {
            book.tocUrl = book.bookUrl
        }

        val nextUrl = chapters.getOrNull(chapterIndex + 1)?.url
        // 1. 文件级缓存优先：读取已落盘的 .nb 章节文件，命中则跳过网络请求直接秒开
        val cachedContent = try {
            BookHelp.getContent(book, chapter)
        } catch (_: Exception) {
            null
        }
        val rawContent = if (!cachedContent.isNullOrBlank()) {
            cachedContent
        } else {
            // 2. 缓存未命中才发起网络请求，成功后由 Legado 引擎落盘 .nb 文件
            try {
                runBlocking {
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                        WebBook.getContentAwait(source, book, chapter, nextUrl, needSave = true)
                    }
                } ?: "【正文加载超时】\n网络请求超时，请检查网络或点击重新加载。"
            } catch (e: Exception) {
                "【正文加载失败】\n${e.message ?: "未知网络错误"}，请检查网络或切换书源。"
            }
        }
        val content = if (rawContent.startsWith("【正文加载")) {
            rawContent
        } else {
            PurifyEngine.purify(book, chapter, rawContent)
        }
        contentCache[chapterIndex] = content
        return content
    }

    override fun getChapterHtml(chapterIndex: Int, optimizeLayout: Boolean): String {
        val content = getChapterContent(chapterIndex)
        // 与 TxtParser 一致：按段落生成基础 HTML，交由阅读引擎排版
        return buildString {
            append("<html><body>")
            content.split('\n').forEach { line ->
                val escaped = line.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                append("<p>").append(escaped).append("</p>")
            }
            append("</body></html>")
        }
    }

    override fun getChapterCount(): Int = chapters.size

    override var paragraphSpacingDp: Float = 0f
    override var firstLineIndentChars: Float = 0f
    override var contentWidth: Int = 0
    override var useEpubCss: Boolean
        get() = false
        set(_) {}

    fun isImageBook(): Boolean {
        return try {
            val book = requireEngineBook()
            val source = requireBookSource()
            if (source.bookSourceType == 2 || ((book.type and io.legado.app.constant.BookType.image) > 0)) {
                return true
            }
            val keywords = listOf("漫画", "韩漫", "日漫", "美漫", "comic", "manga", "條漫", "条漫")
            keywords.any { kw ->
                book.name.contains(kw, ignoreCase = true) ||
                source.bookSourceName.contains(kw, ignoreCase = true) ||
                (book.kind ?: "").contains(kw, ignoreCase = true) ||
                (book.intro ?: "").contains(kw, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun releaseCache() {
        contentCache.clear()
    }
}
