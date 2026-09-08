package com.huangder.lumibooks.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.Debug
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class SourceFilter {
    ALL,
    ENABLED_ONLY,
    DISABLED_ONLY,
    EXPLORE_ONLY
}

class BookSourcesViewModel : ViewModel() {
    val allSources: StateFlow<List<BookSource>> = BookSourceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedGroup = MutableStateFlow<String?>(null)
    val filterStatus = MutableStateFlow(SourceFilter.ALL)

    // 刷新版本计数器：用于强制通知 StateFlow 与 Compose 响应式刷新
    val refreshTick = MutableStateFlow(0L)

    // 动态提取分组列表
    val groups: StateFlow<List<String>> = combine(allSources, refreshTick) { sources, _ ->
        sources.flatMap { it.bookSourceGroup?.split(",", ";", "，", "；")?.map(String::trim) ?: emptyList() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 组合过滤后的书源列表
    val filteredSources: StateFlow<List<BookSource>> = combine(
        allSources,
        searchQuery,
        selectedGroup,
        filterStatus,
        refreshTick
    ) { sources, query, group, filter, _ ->
        sources.filter { source ->
            // 1. 搜索过滤
            val matchesQuery = query.isBlank() ||
                source.bookSourceName.contains(query, ignoreCase = true) ||
                source.bookSourceUrl.contains(query, ignoreCase = true)

            // 2. 分组过滤
            val matchesGroup = group == null || source.bookSourceGroup?.split(",", ";", "，", "；")
                ?.map(String::trim)?.contains(group) == true

            // 3. 状态过滤
            val matchesStatus = when (filter) {
                SourceFilter.ALL -> true
                SourceFilter.ENABLED_ONLY -> source.enabled
                SourceFilter.DISABLED_ONLY -> !source.enabled
                SourceFilter.EXPLORE_ONLY -> source.enabledExplore && !source.exploreUrl.isNullOrBlank()
            }

            matchesQuery && matchesGroup && matchesStatus
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // ─── 调试状态 ──────────────────────────────────────────────────
    private val _debuggingSource = MutableStateFlow<BookSource?>(null)
    val debuggingSource: StateFlow<BookSource?> = _debuggingSource

    private val _isDebugging = MutableStateFlow(false)
    val isDebugging: StateFlow<Boolean> = _isDebugging

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs

    // ─── 编辑状态 ──────────────────────────────────────────────────
    private val _editingSource = MutableStateFlow<BookSource?>(null)
    val editingSource: StateFlow<BookSource?> = _editingSource

    // ─── 待确认导入状态 ──────────────────────────────────────────────
    private val _pendingImportSources = MutableStateFlow<List<BookSource>?>(null)
    val pendingImportSources: StateFlow<List<BookSource>?> = _pendingImportSources

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedGroup(group: String?) {
        selectedGroup.value = group
    }

    fun setFilterStatus(filter: SourceFilter) {
        filterStatus.value = filter
    }

    /** 解析 JSON 书源数据并放入待导入确认状态 */
    fun prepareImportFromJson(json: String) {
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
            _pendingImportSources.value = sources
        }.onFailure {
            _operationMessage.value = "解析书源失败：${it.message}"
        }
    }

    /** 从网络下载书源并放入待导入确认状态 */
    fun prepareImportFromUrl(url: String) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val cleanUrl = url.trim()
                if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
                    !cleanUrl.startsWith("https://", ignoreCase = true)
                ) {
                    error("请输入合法的 HTTP/HTTPS 网络链接")
                }
                val body = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(cleanUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Lumi/1.0")
                        .build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        error("网络请求失败: HTTP ${response.code}")
                    }
                    response.body?.string() ?: error("书源响应内容为空")
                }
                prepareImportFromJson(body)
            } catch (e: Exception) {
                _operationMessage.value = "拉取网络书源失败：${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun cancelPendingImport() {
        _pendingImportSources.value = null
    }

    /** 确认导入：支持自定义分组与覆盖更新选项 */
    fun confirmImport(
        sources: List<BookSource>,
        targetGroup: String?,
        overrideExisting: Boolean
    ) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val currentSources = allSources.value
                val existingUrls = currentSources.map { it.bookSourceUrl }.toSet()

                val finalSources = sources.map { src ->
                    val groupStr = targetGroup?.trim()
                    val finalGroup = if (!groupStr.isNullOrBlank()) {
                        val existingGroups = src.bookSourceGroup
                            ?.split(",", ";", "，", "；")
                            ?.map(String::trim)
                            ?.filter(String::isNotBlank)
                            ?.toMutableSet() ?: mutableSetOf()
                        existingGroups.add(groupStr)
                        existingGroups.joinToString(",")
                    } else {
                        src.bookSourceGroup
                    }
                    src.copy(bookSourceGroup = finalGroup)
                }

                val toSave = if (overrideExisting) {
                    finalSources
                } else {
                    finalSources.filter { it.bookSourceUrl !in existingUrls }
                }

                if (toSave.isEmpty()) {
                    _operationMessage.value = "未导入任何书源（目标书源已全部存在且未选择覆盖）"
                } else {
                    BookSourceRepository.saveAll(toSave)
                    refreshTick.value++
                    _operationMessage.value = "成功导入 ${toSave.size} 个书源"
                }
            } catch (e: Exception) {
                _operationMessage.value = "导入失败：${e.message}"
            } finally {
                _pendingImportSources.value = null
                _loading.value = false
            }
        }
    }

    fun toggleEnabled(source: BookSource) {
        viewModelScope.launch {
            val newStatus = !source.enabled
            BookSourceRepository.setEnabled(source.bookSourceUrl, newStatus)
            refreshTick.value++
        }
    }

    fun toggleExplore(source: BookSource) {
        viewModelScope.launch {
            val newStatus = !source.enabledExplore
            BookSourceRepository.setExploreEnabled(source.bookSourceUrl, newStatus)
            refreshTick.value++
        }
    }

    fun delete(source: BookSource) {
        viewModelScope.launch {
            BookSourceRepository.delete(source.bookSourceUrl)
            refreshTick.value++
            _operationMessage.value = "已删除书源: ${source.bookSourceName.ifBlank { source.bookSourceUrl }}"
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            BookSourceRepository.deleteAll()
            refreshTick.value++
            _operationMessage.value = "已清空所有书源"
        }
    }

    fun saveSource(source: BookSource) {
        viewModelScope.launch {
            BookSourceRepository.save(source)
                .onSuccess {
                    refreshTick.value++
                    _operationMessage.value = "书源「${source.bookSourceName}」已保存"
                    _editingSource.value = null
                }
                .onFailure {
                    _operationMessage.value = "保存失败: ${it.message}"
                }
        }
    }

    fun openEdit(source: BookSource) {
        _editingSource.value = source
    }

    fun closeEdit() {
        _editingSource.value = null
    }

    // ─── 调试控制 ──────────────────────────────────────────────────
    fun openDebug(source: BookSource) {
        _debuggingSource.value = source
        _debugLogs.value = listOf("⇒ 就绪，点击「开始调试」测试书源链路")
        _isDebugging.value = false
    }

    fun closeDebug() {
        stopDebug()
        _debuggingSource.value = null
        _debugLogs.value = emptyList()
    }

    fun startDebug(source: BookSource, key: String) {
        stopDebug()
        _isDebugging.value = true
        _debugLogs.value = listOf("⇒ 启动测试: $key", "⇒ 书源: ${source.bookSourceName} (${source.bookSourceUrl})")
        Debug.callback = object : Debug.Callback {
            override fun printLog(state: Int, msg: String) {
                val current = _debugLogs.value.toMutableList()
                current.add(msg)
                if (current.size > 500) {
                    current.removeAt(0)
                }
                _debugLogs.value = current
                if (state == 1000 || state == -1) {
                    _isDebugging.value = false
                }
            }
        }

        val testKey = key.ifBlank { "完美世界" }
        Debug.startDebug(viewModelScope, source, testKey)
    }

    fun stopDebug() {
        if (_isDebugging.value) {
            Debug.cancelDebug()
            _isDebugging.value = false
            val current = _debugLogs.value.toMutableList()
            current.add("⇒ 调试已中止")
            _debugLogs.value = current
        }
    }

    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }

    fun consumeMessage() {
        _operationMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        Debug.cancelDebug(destroy = true)
    }
}
