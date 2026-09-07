package com.huangder.lumibooks.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import io.legado.app.model.Debug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // 动态提取分组列表
    val groups: StateFlow<List<String>> = allSources.combine(MutableStateFlow(Unit)) { sources, _ ->
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
        filterStatus
    ) { sources, query, group, filter ->
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

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setSelectedGroup(group: String?) {
        selectedGroup.value = group
    }

    fun setFilterStatus(filter: SourceFilter) {
        filterStatus.value = filter
    }

    fun importFromJson(json: String) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            BookSourceRepository.importFromJson(json)
                .onSuccess { _operationMessage.value = "成功导入 $it 个书源" }
                .onFailure { _operationMessage.value = "导入失败：${it.message}" }
            _loading.value = false
        }
    }

    fun importFromUrl(url: String) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            BookSourceRepository.importFromUrl(url)
                .onSuccess { _operationMessage.value = "成功从网络导入 $it 个书源" }
                .onFailure { _operationMessage.value = "网络导入失败：${it.message}" }
            _loading.value = false
        }
    }

    fun toggleEnabled(source: BookSource) {
        viewModelScope.launch {
            val newStatus = !source.enabled
            BookSourceRepository.setEnabled(source.bookSourceUrl, newStatus)
        }
    }

    fun toggleExplore(source: BookSource) {
        viewModelScope.launch {
            val newStatus = !source.enabledExplore
            BookSourceRepository.setExploreEnabled(source.bookSourceUrl, newStatus)
        }
    }

    fun delete(source: BookSource) {
        viewModelScope.launch {
            BookSourceRepository.delete(source.bookSourceUrl)
            _operationMessage.value = "已删除书源: ${source.bookSourceName.ifBlank { source.bookSourceUrl }}"
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            BookSourceRepository.deleteAll()
            _operationMessage.value = "已清空所有书源"
        }
    }

    fun saveSource(source: BookSource) {
        viewModelScope.launch {
            BookSourceRepository.save(source)
                .onSuccess {
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
