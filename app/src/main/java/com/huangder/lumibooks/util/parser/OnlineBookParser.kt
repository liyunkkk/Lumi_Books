package com.huangder.lumibooks.util.parser

import io.legado.app.data.appDb
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 在线书源解析器：实现 Lumi BookParser 接口，正文按章懒加载。
 *
 * filePath 约定："legado-online://<engineBookUrl>"。
 * parse() 拉取目录（getChapterListAwait），getChapterContent() 拉取正文（getContentAwait）。
 */
class OnlineBookParser(
    private val engineBookUrl: String
) : BookParser {

    companion object {
        const val SCHEME = "legado-online://"
    }

    private var chapters: List<io.legado.app.data.entities.BookChapter> = emptyList()
    private val contentCache = HashMap<Int, String>()

    private fun requireBookSource() = appDb.bookSourceDao.getBookSource(
        appDb.bookDao.getBook(engineBookUrl)?.origin ?: ""
    ) ?: throw IllegalStateException("书源不存在或已删除")

    private fun requireEngineBook() = appDb.bookDao.getBook(engineBookUrl)
        ?: throw IllegalStateException("在线书记录不存在，请重新加入书架")

    override fun parse(filePath: String): BookContent {
        val book = requireEngineBook()
        val source = requireBookSource()
        val list = runBlocking {
            withContext(Dispatchers.IO) {
                WebBook.getChapterListAwait(source, book).getOrThrow()
            }
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
        val book = requireEngineBook()
        val source = requireBookSource()
        val chapter = chapters.getOrNull(chapterIndex)
            ?: throw IllegalStateException("章节索引越界: $chapterIndex")
        val nextUrl = chapters.getOrNull(chapterIndex + 1)?.url
        val content = runBlocking {
            withContext(Dispatchers.IO) {
                WebBook.getContentAwait(source, book, chapter, nextUrl, needSave = true)
            }
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
            source.bookSourceType == 2 || book.isType(io.legado.app.constant.BookType.image)
        } catch (_: Exception) {
            false
        }
    }

    fun releaseCache() {
        contentCache.clear()
    }
}
