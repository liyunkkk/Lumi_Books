package com.huangder.lumibooks.ui.discover

import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Lumi 在线书源桥接层：所有解析委托给 Legado 引擎。 */
object BookDiscoveryRepository {

    fun observeEnabledSources() = appDb.bookSourceDao.flowEnabled()
    fun observeExploreSources() = appDb.bookSourceDao.flowExplore()

    /**
     * 流式多源并发搜索：限制并发度并带超时，一旦发现结果立即回调 onBatchFound
     * 支持精确匹配、搜索维度（书名/作者）以及书籍类型过滤
     */
    suspend fun searchStreaming(
        keyword: String,
        sources: List<BookSource>,
        maxConcurrency: Int = 6,
        sourceTimeoutMs: Long = 8000L,
        exactMatch: Boolean = false,
        searchMode: Int = 0, // 0: 综合, 1: 仅书名, 2: 仅作者
        typeFilter: Int = 0, // 0: 全部, 1: 文本, 2: 漫画, 3: 音频
        onBatchFound: suspend (List<SearchBook>) -> Unit
    ) = supervisorScope {
        val validSources = sources.filter { !it.searchUrl.isNullOrBlank() }
        if (validSources.isEmpty()) return@supervisorScope

        val semaphore = Semaphore(maxConcurrency)
        val accumulated = mutableListOf<SearchBook>()

        val jobs = validSources.map { source ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    val books = runCatching {
                        withTimeoutOrNull(sourceTimeoutMs) {
                            WebBook.searchBookAwait(source, keyword)
                        }
                    }.getOrNull() ?: emptyList()

                    if (books.isNotEmpty()) {
                        val filtered = books.filter { b ->
                            val matchType = when (typeFilter) {
                                1 -> (b.type and BookType.text) != 0 || b.type == 0
                                2 -> (b.type and BookType.image) != 0
                                3 -> (b.type and BookType.audio) != 0
                                else -> true
                            }
                            if (!matchType) return@filter false

                            val key = keyword.trim()
                            if (exactMatch) {
                                when (searchMode) {
                                    1 -> b.name.trim().equals(key, ignoreCase = true)
                                    2 -> b.author.trim().equals(key, ignoreCase = true)
                                    else -> b.name.trim().equals(key, ignoreCase = true) || b.author.trim().equals(key, ignoreCase = true)
                                }
                            } else {
                                when (searchMode) {
                                    1 -> b.name.contains(key, ignoreCase = true)
                                    2 -> b.author.contains(key, ignoreCase = true)
                                    else -> b.name.contains(key, ignoreCase = true) || b.author.contains(key, ignoreCase = true) || key.contains(b.name.trim(), ignoreCase = true)
                                }
                            }
                        }

                        if (filtered.isNotEmpty()) {
                            val toEmit = synchronized(accumulated) {
                                val newBooks = filtered.filter { b ->
                                    accumulated.none { it.bookUrl == b.bookUrl }
                                }
                                accumulated.addAll(newBooks)
                                accumulated.sortedWith(
                                    compareBy<SearchBook> { it.name }.thenBy { it.originName }
                                ).toList()
                            }
                            onBatchFound(toEmit)
                        }
                    }
                }
            }
        }
        jobs.forEach { it.join() }
    }

    suspend fun search(keyword: String, sources: List<BookSource>): List<SearchBook> {
        val result = mutableListOf<SearchBook>()
        searchStreaming(keyword, sources) { current ->
            result.clear()
            result.addAll(current)
        }
        return result
    }

    suspend fun explore(source: BookSource, url: String, page: Int = 1): List<SearchBook> = withContext(Dispatchers.IO) {
        WebBook.exploreBookAwait(source, url, page)
    }

    suspend fun exploreKinds(source: BookSource): List<ExploreKind> = withContext(Dispatchers.IO) {
        source.exploreKinds()
    }
}
