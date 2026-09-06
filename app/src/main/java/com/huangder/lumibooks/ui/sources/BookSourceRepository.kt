package com.huangder.lumibooks.ui.sources

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 书源仓库：直接操作引擎自带的 Room 库（legado_engine.db）。
 */
object BookSourceRepository {

    fun observeAll(): Flow<List<BookSource>> = appDb.bookSourceDao.flowAll()

    fun count(): Int = appDb.bookSourceDao.count()

    suspend fun importFromJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sources = GSON.fromJsonArray<BookSource>(json).getOrThrow()
            if (sources.isEmpty()) {
                error("未解析到任何书源，请检查 JSON 格式")
            }
            appDb.bookSourceDao.insert(*sources.toTypedArray())
            sources.size
        }
    }

    suspend fun setEnabled(sourceUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.enable(sourceUrl, enabled)
    }

    suspend fun delete(sourceUrl: String) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.delete(sourceUrl)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.deleteAll()
    }
}
