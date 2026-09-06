package com.huangder.lumibooks.ui.discover

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
     */
    suspend fun searchStreaming(
        keyword: String,
        sources: List<BookSource>,
        maxConcurrency: Int = 6,
        sourceTimeoutMs: Long = 8000L,
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
                        val toEmit = synchronized(accumulated) {
                            val newBooks = books.filter { b ->
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

    suspend fun explore(source: BookSource, url: String): List<SearchBook> = withContext(Dispatchers.IO) {
        WebBook.exploreBookAwait(source, url)
    }

    suspend fun exploreKinds(source: BookSource): List<ExploreKind> = withContext(Dispatchers.IO) {
        source.exploreKinds()
    }
}
