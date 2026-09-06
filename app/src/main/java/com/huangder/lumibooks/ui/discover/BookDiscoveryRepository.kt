package com.huangder.lumibooks.ui.discover

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

/** Lumi 在线书源桥接层：所有解析委托给 Legado 引擎。 */
object BookDiscoveryRepository {
    fun observeEnabledSources() = appDb.bookSourceDao.flowEnabled()
    fun observeExploreSources() = appDb.bookSourceDao.flowExplore()

    suspend fun search(keyword: String, sources: List<BookSource>): List<SearchBook> = supervisorScope {
        sources.map { source ->
            async(Dispatchers.IO) {
                runCatching { WebBook.searchBookAwait(source, keyword) }.getOrDefault(emptyList())
            }
        }.awaitAll().flatten().sortedWith(compareBy<SearchBook> { it.name }.thenBy { it.originName })
    }

    suspend fun explore(source: BookSource, url: String): List<SearchBook> = withContext(Dispatchers.IO) {
        WebBook.exploreBookAwait(source, url)
    }

    suspend fun exploreKinds(source: BookSource): List<ExploreKind> = withContext(Dispatchers.IO) {
        source.exploreKinds()
    }
}
