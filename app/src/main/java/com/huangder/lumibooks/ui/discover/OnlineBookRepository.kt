package com.huangder.lumibooks.ui.discover

import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.util.parser.OnlineBookParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * 在线书籍入库桥接：SearchBook → 引擎 Book + Lumi 本地 Book 记录。
 * 幂等：同一 bookUrl 只入库一次。
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
     * 已存在时直接返回，不重复拉取网络。
     */
    suspend fun ensureInLibrary(
        bookRepository: BookRepository,
        searchBook: SearchBook
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val lumiId = lumiBookId(searchBook.bookUrl)
            // 幂等：Lumi 已有记录直接返回
            bookRepository.getBookById(lumiId)?.let { return@runCatching lumiId }

            // 引擎库：不存在则插入（保留 SearchBook 解析出的元数据）
            val engineBook = appDb.bookDao.getBook(searchBook.bookUrl)
                ?: searchBook.toBook().also { appDb.bookDao.insert(it) }

            val lumiBook = Book(
                id = lumiId,
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
            lumiId
        }
    }

    /** 判断某 Lumi Book 是否为在线书源书 */
    fun isOnlineBook(filePath: String): Boolean = filePath.startsWith(SCHEME)
}
