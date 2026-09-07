package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.domain.model.Book as LumiBook
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.discover.OnlineBookRepository
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book as EngineBook
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class LegadoImportResult(
    val bookSourceCount: Int,
    val bookCount: Int,
    val replaceRuleCount: Int
)

@Singleton
class LegadoBackupImporter @Inject constructor(
    private val bookRepository: BookRepository
) {
    /**
     * 判断解压后的目录是否为开源阅读（Legado）导出的备份
     */
    fun isLegadoBackup(extractedDir: File): Boolean {
        val targets = listOf(
            "bookSource.json",
            "bookshelf.json",
            "book.json",
            "books.json",
            "replaceRule.json",
            "myBookSearchHistory.json",
            "bookmark.json"
        )
        return targets.any { File(extractedDir, it).isFile }
    }

    /**
     * 从解压的开源阅读备份目录中提取并导入书源、图书与净化规则
     */
    suspend fun importFromExtracted(extractedDir: File): LegadoImportResult = withContext(Dispatchers.IO) {
        var sourceCount = 0
        var bookCount = 0
        var ruleCount = 0

        // 1. 恢复书源 (bookSource.json)
        val sourceFile = File(extractedDir, "bookSource.json")
        if (sourceFile.isFile) {
            runCatching {
                val json = sourceFile.readText(Charsets.UTF_8)
                val sources = GSON.fromJsonArray<BookSource>(json).getOrDefault(emptyList())
                val validSources = sources.filter { it.bookSourceUrl.isNotBlank() && it.bookSourceName.isNotBlank() }
                if (validSources.isNotEmpty()) {
                    appDb.bookSourceDao.insert(*validSources.toTypedArray())
                    sourceCount = validSources.size
                }
            }
        }

        // 2. 恢复图书 (bookshelf.json / book.json / books.json)
        val bookFile = listOf("bookshelf.json", "book.json", "books.json")
            .map { File(extractedDir, it) }
            .firstOrNull { it.isFile }

        if (bookFile != null) {
            runCatching {
                val json = bookFile.readText(Charsets.UTF_8)
                val engineBooks = GSON.fromJsonArray<EngineBook>(json).getOrDefault(emptyList())
                val validBooks = engineBooks.filter { it.bookUrl.isNotBlank() && it.name.isNotBlank() }
                if (validBooks.isNotEmpty()) {
                    // 插入到引擎 Room 库
                    appDb.bookDao.insert(*validBooks.toTypedArray())

                    // 转换为 Lumi 本地书库并插入
                    val now = System.currentTimeMillis()
                    for (eb in validBooks) {
                        val isLocal = eb.isLocal
                        val filePath = if (isLocal) eb.bookUrl else OnlineBookRepository.SCHEME + eb.bookUrl
                        val lumiId = if (isLocal) {
                            java.util.UUID.randomUUID().toString()
                        } else {
                            OnlineBookRepository.lumiBookId(eb.bookUrl)
                        }
                        val progress = if (eb.totalChapterNum > 0) {
                            (eb.durChapterIndex.toFloat() / eb.totalChapterNum).coerceIn(0f, 1f)
                        } else 0f

                        val lumiBook = LumiBook(
                            id = lumiId,
                            title = eb.name,
                            author = eb.author.ifBlank { "未知作者" },
                            filePath = filePath,
                            coverPath = eb.customCoverUrl?.takeIf { it.isNotBlank() } ?: eb.coverUrl,
                            format = BookFormat.TXT,
                            lastReadTime = if (eb.durChapterTime > 0) eb.durChapterTime else now,
                            readingProgress = progress,
                            createdAt = now
                        )
                        bookRepository.insertBook(lumiBook)
                        bookCount++
                    }
                }
            }
        }

        // 3. 恢复替换规则 (replaceRule.json)
        val ruleFile = File(extractedDir, "replaceRule.json")
        if (ruleFile.isFile) {
            runCatching {
                val json = ruleFile.readText(Charsets.UTF_8)
                val rules = GSON.fromJsonArray<ReplaceRule>(json).getOrDefault(emptyList())
                val validRules = rules.filter { it.pattern.isNotBlank() }
                if (validRules.isNotEmpty()) {
                    appDb.replaceRuleDao.insert(*validRules.toTypedArray())
                    ruleCount = validRules.size
                }
            }
        }

        LegadoImportResult(sourceCount, bookCount, ruleCount)
    }
}
