package com.huangder.lumibooks.ui.discover

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.util.parser.OnlineBookParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 在线书籍入库桥接：SearchBook → 引擎 Book + Lumi 本地 Book 记录。
 * 无论哪个书源加入书库，同名小说只保留一部，已存在时无缝更新替换为当前书源。
 */
object OnlineBookRepository {
    const val SCHEME = OnlineBookParser.SCHEME

    fun lumiBookId(bookUrl: String): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(bookUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "online-" + md5.take(20)
    }

    /**
     * 确保书籍存在于引擎库与 Lumi 书库，返回 Lumi Book id。
     * 若书架已存在同名在线书籍，直接无缝更新书源地址并复用现有记录，不重复添加。
     */
    suspend fun ensureInLibrary(
        bookRepository: BookRepository,
        searchBook: SearchBook
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val searchTitle = searchBook.name.trim()
            val searchAuthor = searchBook.author.trim()
            val candidateLumiId = lumiBookId(searchBook.bookUrl)

            // 1. 优先查重：按 bookUrl 的 MD5 或 按 (同书名 + 作者匹配) 查找书库中现有的在线书籍
            val allBooks = bookRepository.getAllBooks().first()
            val existing = allBooks.firstOrNull { book ->
                isOnlineBook(book.filePath) && (
                    book.id == candidateLumiId ||
                    (book.title.trim() == searchTitle &&
                     (searchAuthor.isBlank() || book.author.isBlank() || book.author.trim() == searchAuthor))
                )
            }

            // 2. 引擎库：确保目标 searchBook 已持久化至 engineBook
            val engineBook = appDb.bookDao.getBook(searchBook.bookUrl)
                ?: searchBook.toBook().also { appDb.bookDao.insert(it) }

            if (existing != null) {
                // 已存在同名同作者小说：无缝更新书源至当前选中的书源，不重复入库
                val updated = existing.copy(
                    filePath = SCHEME + engineBook.bookUrl,
                    coverPath = engineBook.coverUrl?.takeIf { it.isNotBlank() } ?: existing.coverPath,
                    lastReadTime = System.currentTimeMillis()
                )
                bookRepository.updateBook(updated)
                existing.id
            } else {
                // 首次入库：新建条目
                val lumiBook = Book(
                    id = candidateLumiId,
                    title = engineBook.name.ifBlank { searchBook.name },
                    author = engineBook.author.ifBlank { searchBook.author },
                    filePath = SCHEME + engineBook.bookUrl,
                    coverPath = engineBook.coverUrl ?: searchBook.coverUrl,
                    format = BookFormat.TXT,
                    lastReadTime = System.currentTimeMillis(),
                    readingProgress = 0f,
                    createdAt = System.currentTimeMillis()
                )
                bookRepository.insertBook(lumiBook)
                candidateLumiId
            }
        }
    }

    /** 判断某 Lumi Book 是否为在线书源书 */
    fun isOnlineBook(filePath: String): Boolean = filePath.startsWith(SCHEME)
}
