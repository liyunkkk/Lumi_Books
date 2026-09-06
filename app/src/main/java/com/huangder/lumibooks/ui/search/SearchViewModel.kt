package com.huangder.lumibooks.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.ui.discover.BookDiscoveryRepository
import com.huangder.lumibooks.ui.discover.OnlineBookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchHistory: StateFlow<List<String>> = dataStoreManager.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exactSearchEnabled: StateFlow<Boolean> = dataStoreManager.exactSearchEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val searchMode: StateFlow<Int> = dataStoreManager.searchMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val searchTypeFilter: StateFlow<Int> = dataStoreManager.searchTypeFilter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _allSources = MutableStateFlow<List<BookSource>>(emptyList())
    val allSources: StateFlow<List<BookSource>> = _allSources.asStateFlow()

    private val _selectedSourceUrls = MutableStateFlow<Set<String>>(emptySet())
    val selectedSourceUrls: StateFlow<Set<String>> = _selectedSourceUrls.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchBook>>(emptyList())
    val searchResults: StateFlow<List<SearchBook>> = _searchResults.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var searchJob: Job?
    init {
        searchJob = null
        loadSources()
    }

    fun setQuery(text: String) {
        _query.value = text
    }

    private fun loadSources() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sources = runCatching { appDb.bookSourceDao.all() }.getOrDefault(emptyList())
                    .filter { !it.searchUrl.isNullOrBlank() }
                _allSources.value = sources
                if (_selectedSourceUrls.value.isEmpty()) {
                    _selectedSourceUrls.value = sources.map { it.bookSourceUrl }.toSet()
                }
            }
        }
    }

    fun toggleExactSearch(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveExactSearchEnabled(enabled)
        }
    }

    fun setSearchMode(mode: Int) {
        viewModelScope.launch {
            dataStoreManager.saveSearchMode(mode)
        }
    }

    fun setSearchTypeFilter(type: Int) {
        viewModelScope.launch {
            dataStoreManager.saveSearchTypeFilter(type)
        }
    }

    fun toggleSource(url: String) {
        val current = _selectedSourceUrls.value.toMutableSet()
        if (current.contains(url)) {
            current.remove(url)
        } else {
            current.add(url)
        }
        _selectedSourceUrls.value = current
    }

    fun selectAllSources() {
        _selectedSourceUrls.value = _allSources.value.map { it.bookSourceUrl }.toSet()
    }

    fun invertSources() {
        val current = _selectedSourceUrls.value
        val all = _allSources.value.map { it.bookSourceUrl }.toSet()
        _selectedSourceUrls.value = all - current
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            dataStoreManager.clearSearchHistory()
        }
    }

    fun removeSearchHistory(key: String) {
        viewModelScope.launch {
            dataStoreManager.removeSearchHistory(key)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _query.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun search(keyword: String) {
        val key = keyword.trim()
        if (key.isBlank()) return
        _query.value = key

        viewModelScope.launch {
            dataStoreManager.addSearchHistory(key)
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = emptyList()
            _message.value = null

            val availableSources = _allSources.value
            val targetUrls = _selectedSourceUrls.value
            val sourcesToSearch = if (targetUrls.isNotEmpty()) {
                availableSources.filter { targetUrls.contains(it.bookSourceUrl) }
            } else {
                availableSources
            }

            if (sourcesToSearch.isEmpty()) {
                _message.value = "未选中任何书源，请先勾选可用书源"
                _isSearching.value = false
                return@launch
            }

            try {
                BookDiscoveryRepository.searchStreaming(
                    keyword = key,
                    sources = sourcesToSearch,
                    maxConcurrency = 6,
                    sourceTimeoutMs = 8000L,
                    exactMatch = exactSearchEnabled.value,
                    searchMode = searchMode.value,
                    typeFilter = searchTypeFilter.value
                ) { batch ->
                    _searchResults.value = batch
                }
                if (_searchResults.value.isEmpty()) {
                    _message.value = "未检索到「$key」相关网络书籍"
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _message.value = "搜索异常: ${e.message}"
                }
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun addToBookshelf(book: SearchBook, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            OnlineBookRepository.ensureInLibrary(bookRepository, book)
                .onSuccess { lumiId ->
                    onSuccess(lumiId)
                }
                .onFailure {
                    _message.value = "加入书架失败: ${it.message}"
                }
        }
    }
}
