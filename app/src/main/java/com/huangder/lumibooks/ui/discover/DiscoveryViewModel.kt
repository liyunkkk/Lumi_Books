package com.huangder.lumibooks.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    /** 启用发现的书源列表 */
    val exploreSources: StateFlow<List<BookSource>> = BookDiscoveryRepository.observeExploreSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedSource = MutableStateFlow<BookSource?>(null)
    val selectedSource: StateFlow<BookSource?> = _selectedSource

    private val _exploreKinds = MutableStateFlow<List<ExploreKind>>(emptyList())
    val exploreKinds: StateFlow<List<ExploreKind>> = _exploreKinds

    private val _selectedKind = MutableStateFlow<ExploreKind?>(null)
    val selectedKind: StateFlow<ExploreKind?> = _selectedKind

    private val _books = MutableStateFlow<List<SearchBook>>(emptyList())
    val books: StateFlow<List<SearchBook>> = _books

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private val _searchResults = MutableStateFlow<List<SearchBook>>(emptyList())
    val searchResults: StateFlow<List<SearchBook>> = _searchResults

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** 布局模式：1 列表, 2 标准网格, 3 紧凑网格（持久化存储） */
    private val _layoutMode = MutableStateFlow(2)
    val layoutMode: StateFlow<Int> = _layoutMode

    /** 选中要操作/查看详情的书籍 */
    private val _activeBook = MutableStateFlow<SearchBook?>(null)
    val activeBook: StateFlow<SearchBook?> = _activeBook

    private val _isInLibrary = MutableStateFlow(false)
    val isInLibrary: StateFlow<Boolean> = _isInLibrary

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            dataStoreManager.discoverLayoutMode.collectLatest {
                _layoutMode.value = it
            }
        }
    }

    fun setLayoutMode(mode: Int) {
        val normalized = mode.coerceIn(1, 3)
        _layoutMode.value = normalized
        viewModelScope.launch {
            dataStoreManager.saveDiscoverLayoutMode(normalized)
        }
    }

    fun selectSource(source: BookSource?) {
        if (_selectedSource.value?.bookSourceUrl == source?.bookSourceUrl) return
        _selectedSource.value = source
        _exploreKinds.value = emptyList()
        _selectedKind.value = null
        _books.value = emptyList()
        if (source != null) {
            viewModelScope.launch {
                dataStoreManager.saveDiscoverSelectedSourceUrl(source.bookSourceUrl)
                runCatching { BookDiscoveryRepository.exploreKinds(source) }
                    .onSuccess { kinds ->
                        _exploreKinds.value = kinds.filter { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
                        _selectedKind.value = _exploreKinds.value.firstOrNull()
                        _selectedKind.value?.let { loadBooks(source, it) }
                    }
                    .onFailure { _message.value = "解析分类失败: ${it.message}" }
            }
        }
    }

    /** 自动恢复上次选中的书源或选中第一个可用书源 */
    fun maybeAutoSelect(sources: List<BookSource>) {
        if (_selectedSource.value == null && sources.isNotEmpty()) {
            viewModelScope.launch {
                val savedUrl = dataStoreManager.discoverSelectedSourceUrl.first()
                val matched = sources.firstOrNull { it.bookSourceUrl == savedUrl } ?: sources.first()
                selectSource(matched)
            }
        }
    }

    fun selectKind(kind: ExploreKind) {
        val source = _selectedSource.value ?: return
        _selectedKind.value = kind
        loadBooks(source, kind)
    }

    private var currentExplorePage = 1
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private fun loadBooks(source: BookSource, kind: ExploreKind) {
        val url = kind.url ?: return
        loadJob?.cancel()
        currentExplorePage = 1
        _hasMore.value = true
        loadJob = viewModelScope.launch {
            _loading.value = true
            runCatching { BookDiscoveryRepository.explore(source, url, page = 1) }
                .onSuccess { books ->
                    _books.value = books
                    if (books.isEmpty()) _hasMore.value = false
                }
                .onFailure { _message.value = "加载失败: ${it.message}" }
            _loading.value = false
        }
    }

    fun loadMore() {
        val source = _selectedSource.value ?: return
        val kind = _selectedKind.value ?: return
        val url = kind.url ?: return
        if (_loading.value || _isLoadingMore.value || !_hasMore.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = currentExplorePage + 1
            runCatching { BookDiscoveryRepository.explore(source, url, page = nextPage) }
                .onSuccess { newBooks ->
                    if (newBooks.isEmpty()) {
                        _hasMore.value = false
                    } else {
                        val currentList = _books.value
                        val toAdd = newBooks.filter { nb -> currentList.none { it.bookUrl == nb.bookUrl } }
                        if (toAdd.isEmpty()) {
                            _hasMore.value = false
                        } else {
                            currentExplorePage = nextPage
                            _books.value = currentList + toAdd
                        }
                    }
                }
                .onFailure { }
            _isLoadingMore.value = false
        }
    }

    fun refresh() {
        val source = _selectedSource.value ?: return
        val kind = _selectedKind.value ?: return
        loadBooks(source, kind)
    }

    fun search(keyword: String) {
        val key = keyword.trim()
        if (key.isEmpty()) {
            clearSearch()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searching.value = true
            _searchResults.value = emptyList()

            val sources = withContext(Dispatchers.IO) {
                val current = _selectedSource.value
                val enabled = runCatching { io.legado.app.data.appDb.bookSourceDao.allEnabled() }.getOrDefault(emptyList())
                val allSources = runCatching { io.legado.app.data.appDb.bookSourceDao.all() }.getOrDefault(emptyList())

                val candidates = mutableListOf<BookSource>()
                if (current != null && !current.searchUrl.isNullOrBlank()) {
                    candidates.add(current)
                }
                candidates.addAll(enabled)
                if (candidates.none { !it.searchUrl.isNullOrBlank() }) {
                    candidates.addAll(allSources)
                }
                candidates.filter { !it.searchUrl.isNullOrBlank() }.distinctBy { it.bookSourceUrl }
            }

            if (sources.isEmpty()) {
                _message.value = "没有可搜索的书源，请先导入书源"
                _searching.value = false
                return@launch
            }

            try {
                BookDiscoveryRepository.searchStreaming(
                    keyword = key,
                    sources = sources,
                    maxConcurrency = 6,
                    sourceTimeoutMs = 8000L
                ) { batch ->
                    _searchResults.value = batch
                }
                if (_searchResults.value.isEmpty()) {
                    _message.value = "未找到「$key」相关网络书籍"
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _message.value = "搜索异常: ${e.message}"
                }
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        _searching.value = false
    }

    fun showBookActions(book: SearchBook) {
        _activeBook.value = book
        viewModelScope.launch {
            val lumiId = OnlineBookRepository.lumiBookId(book.bookUrl)
            _isInLibrary.value = bookRepository.getBookById(lumiId) != null
        }
    }

    fun dismissBookActions() {
        _activeBook.value = null
    }

    fun addToBookshelf(book: SearchBook) {
        viewModelScope.launch {
            OnlineBookRepository.ensureInLibrary(bookRepository, book)
                .onSuccess {
                    _isInLibrary.value = true
                    _message.value = "已将《${book.name}》加入书架"
                }
                .onFailure {
                    _message.value = "加入书架失败: ${it.message}"
                }
        }
    }

    fun prepareReadBook(book: SearchBook, onReady: (lumiBookId: String, coverUrl: String?, title: String) -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            OnlineBookRepository.ensureInLibrary(bookRepository, book)
                .onSuccess { lumiId ->
                    _loading.value = false
                    dismissBookActions()
                    onReady(lumiId, book.coverUrl, book.name)
                }
                .onFailure { error ->
                    _loading.value = false
                    _message.value = "准备阅读失败: ${error.message}"
                }
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}

/** 同步取启用书源，避免 ViewModel 内额外暴露 Flow。 */
private object BookSourceRepositoryHolder {
    suspend fun enabledSources(): Result<List<BookSource>> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { io.legado.app.data.appDb.bookSourceDao.allEnabled() }
        }
}
