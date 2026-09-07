package com.huangder.lumibooks.ui.sources

import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 书源仓库：直接操作引擎自带的 Room 库（legado_engine.db）。
 */
object BookSourceRepository {
    fun observeAll(): Flow<List<BookSource>> = appDb.bookSourceDao.flowAll()
    fun observeGroups(): Flow<List<String>> = appDb.bookSourceDao.flowGroups()
    fun count(): Int = appDb.bookSourceDao.count()

    suspend fun get(sourceUrl: String): BookSource? = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.getBookSource(sourceUrl)
    }

    suspend fun importFromJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = json.trim()
            val sources: List<BookSource> = if (trimmed.startsWith("[")) {
                GSON.fromJsonArray<BookSource>(trimmed).getOrThrow()
            } else {
                val single = GSON.fromJsonObject<BookSource>(trimmed).getOrThrow()
                listOf(single)
            }
            if (sources.isEmpty()) {
                error("未解析到任何书源，请检查 JSON 格式")
            }
            appDb.bookSourceDao.insert(*sources.toTypedArray())
            sources.size
        }
    }

    suspend fun importFromUrl(url: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
                !cleanUrl.startsWith("https://", ignoreCase = true)
            ) {
                error("请输入合法的 HTTP/HTTPS 网络链接")
            }
            val request = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Lumi/1.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                error("网络请求失败: HTTP ${response.code}")
            }
            val body = response.body?.string() ?: error("书源响应内容为空")
            importFromJson(body).getOrThrow()
        }
    }

    suspend fun save(bookSource: BookSource): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            appDb.bookSourceDao.insert(bookSource)
        }
    }

    suspend fun setEnabled(sourceUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.enable(sourceUrl, enabled)
    }

    suspend fun setExploreEnabled(sourceUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.enableExplore(sourceUrl, enabled)
    }

    suspend fun delete(sourceUrl: String) = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.delete(sourceUrl)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        appDb.bookSourceDao.deleteAll()
    }
}
