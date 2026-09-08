package com.huangder.lumibooks.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.domain.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.legado.app.data.entities.BookSource
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.model.webBook.WebBook
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
                        val validKinds = kinds.filter { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
                        _exploreKinds.value = validKinds
                        _selectedKind.value = validKinds.firstOrNull()
                        _selectedKind.value?.let { loadBooks(source, it) }
                        loadCategorySections(source, validKinds)
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
        val kind = _selectedKind.value
        if (kind != null) {
            loadBooks(source, kind)
        }
        if (_exploreKinds.value.isNotEmpty()) {
            loadCategorySections(source, _exploreKinds.value)
        }
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

    private var detailJob: Job? = null
    private val _loadingDetail = MutableStateFlow(false)
    val loadingDetail: StateFlow<Boolean> = _loadingDetail

    // 流媒体分类流状态（用于一级界面多分类横滑浏览）
    private val _categorySections = MutableStateFlow<List<CategorySection>>(emptyList())
    val categorySections: StateFlow<List<CategorySection>> = _categorySections
    private val _isLoadingSections = MutableStateFlow(false)
    val isLoadingSections: StateFlow<Boolean> = _isLoadingSections

    // 流媒体二级分类页面状态（用于垂直 3 列瀑布流与触底分页）
    private val _activeCategoryDetail = MutableStateFlow<ExploreKind?>(null)
    val activeCategoryDetail: StateFlow<ExploreKind?> = _activeCategoryDetail
    private val _categoryDetailPage = MutableStateFlow(1)
    private val _categoryDetailBooks = MutableStateFlow<List<SearchBook>>(emptyList())
    val categoryDetailBooks: StateFlow<List<SearchBook>> = _categoryDetailBooks
    private val _isLoadingCategoryDetail = MutableStateFlow(false)
    val isLoadingCategoryDetail: StateFlow<Boolean> = _isLoadingCategoryDetail
    private val _hasMoreCategoryDetail = MutableStateFlow(true)
    val hasMoreCategoryDetail: StateFlow<Boolean> = _hasMoreCategoryDetail

    fun showBookActions(book: SearchBook) {
        _activeBook.value = book
        detailJob?.cancel()
        viewModelScope.launch {
            val lumiId = OnlineBookRepository.lumiBookId(book.bookUrl)
            _isInLibrary.value = bookRepository.getBookById(lumiId) != null
        }
        detailJob = viewModelScope.launch(Dispatchers.IO) {
            val cached = runCatching { appDb.bookDao.getBook(book.bookUrl) }.getOrNull()
            val cachedIntro = cached?.getDisplayIntro()?.trim() ?: cached?.intro?.trim()
            if (!cachedIntro.isNullOrBlank()) {
                if (book.intro.isNullOrBlank()) {
                    val updated = book.copy(
                        intro = cachedIntro,
                        latestChapterTitle = cached?.latestChapterTitle ?: book.latestChapterTitle
                    )
                    if (_activeBook.value?.bookUrl == book.bookUrl) {
                        _activeBook.value = updated
                    }
                }
                return@launch
            }

            if (book.intro.isNullOrBlank()) {
                _loadingDetail.value = true
                val source = _selectedSource.value
                    ?: runCatching { appDb.bookSourceDao.getBookSource(book.origin) }.getOrNull()
                if (source != null) {
                    runCatching {
                        // 核心修复：强制清除 SearchBook 继承来的列表页 infoHtml 缓存，使 WebBook 重新发起网络 HTTP 请求拉取完整书籍详情页
                        val targetBook = book.toBook().apply {
                            infoHtml = null
                        }
                        val detailed = WebBook.getBookInfoAwait(source, targetBook)
                        val rawIntro = detailed.getDisplayIntro().ifBlank { detailed.intro.orEmpty() }.trim()
                        val fetchedIntro = if (rawIntro.isNotBlank()) rawIntro else null
                        if (!fetchedIntro.isNullOrBlank()) {
                            runCatching { appDb.bookDao.insert(detailed) }
                            if (_activeBook.value?.bookUrl == book.bookUrl) {
                                val updated = book.copy(
                                    intro = fetchedIntro,
                                    latestChapterTitle = detailed.latestChapterTitle ?: book.latestChapterTitle
                                )
                                _activeBook.value = updated
                            }
                        }
                    }.onFailure {
                        android.util.Log.e("DiscoveryVM", "拉取详情失败: ${it.message}", it)
                    }
                }
                _loadingDetail.value = false
            }
        }
    }
    fun dismissBookActions() {
        detailJob?.cancel()
        _loadingDetail.value = false
        _activeBook.value = null
    }

    /**
     * 并发拉取流媒体分类流（选取前 8 个分类，每个分类拉取第 1 页书籍）
     */
    private fun loadCategorySections(source: BookSource, kinds: List<ExploreKind>) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingSections.value = true
            val targetKinds = kinds.take(8)
            // 先用占位数据占位
            _categorySections.value = targetKinds.map { CategorySection(kind = it, books = emptyList(), isLoading = true) }

            val loadedSections = coroutineScope {
                targetKinds.map { kind ->
                    async {
                        val url = kind.url ?: return@async CategorySection(kind = kind, books = emptyList(), isLoading = false)
                        val result = runCatching {
                            WebBook.exploreBookAwait(source, url, page = 1)
                        }.getOrNull()
                        CategorySection(
                            kind = kind,
                            books = result ?: emptyList(),
                            isLoading = false
                        )
                    }
                }.awaitAll()
            }

            _categorySections.value = loadedSections.filter { it.books.isNotEmpty() }
            _isLoadingSections.value = false
        }
    }

    /**
     * 进入流媒体二级分类页面（3 列垂直瀑布流）
     */
    fun openCategoryDetail(kind: ExploreKind) {
        _activeCategoryDetail.value = kind
        _categoryDetailPage.value = 1
        _categoryDetailBooks.value = emptyList()
        _hasMoreCategoryDetail.value = true
        // 先复用已有缓存展示
        val preloaded = _categorySections.value.firstOrNull { it.kind == kind }?.books
        if (!preloaded.isNullOrEmpty()) {
            _categoryDetailBooks.value = preloaded
        }
        loadCategoryDetailBooks(page = 1, refresh = preloaded.isNullOrEmpty())
    }

    /**
     * 关闭流媒体二级分类页面
     */
    fun closeCategoryDetail() {
        _activeCategoryDetail.value = null
        _categoryDetailBooks.value = emptyList()
    }

    /**
     * 流媒体二级页面触底加载更多
     */
    fun loadMoreCategoryDetail() {
        if (_isLoadingCategoryDetail.value || !_hasMoreCategoryDetail.value) return
        val nextPage = _categoryDetailPage.value + 1
        loadCategoryDetailBooks(page = nextPage, refresh = false)
    }

    private fun loadCategoryDetailBooks(page: Int, refresh: Boolean) {
        val kind = _activeCategoryDetail.value ?: return
        val source = _selectedSource.value ?: return
        val url = kind.url ?: return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingCategoryDetail.value = true
            val result = runCatching {
                WebBook.exploreBookAwait(source, url, page = page)
            }.getOrNull()

            val newBooks = result ?: emptyList()
            if (refresh) {
                _categoryDetailBooks.value = newBooks
            } else {
                val current = _categoryDetailBooks.value.toMutableList()
                val existingUrls = current.map { it.bookUrl }.toSet()
                current.addAll(newBooks.filter { it.bookUrl !in existingUrls })
                _categoryDetailBooks.value = current
            }

            _categoryDetailPage.value = page
            _hasMoreCategoryDetail.value = newBooks.isNotEmpty()
            _isLoadingCategoryDetail.value = false
        }
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

data class CategorySection(
    val kind: ExploreKind,
    val books: List<SearchBook> = emptyList(),
    val isLoading: Boolean = false
)
