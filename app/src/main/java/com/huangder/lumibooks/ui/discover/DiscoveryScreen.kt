package com.huangder.lumibooks.ui.discover

import io.legado.app.data.entities.rule.ExploreKind
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.request.ImageRequest
import coil.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook

/**
 * 发现页：移除 Material TopAppBar 消除色差 + 数据持久化 + 键盘搜索优化 + 封面网络加载与平滑阅读进入
 */
@Composable
fun DiscoveryScreen(
    initialSearchQuery: String? = null,
    onInitialSearchQueryConsumed: () -> Unit = {},
    onSearchInBookshelf: ((String) -> Unit)? = null,
    onOpenBook: (bookId: String, coverUrl: String?, title: String) -> Unit = { _, _, _ -> },
    onNavigateToSearch: (String?) -> Unit = {},
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuHost = LocalLiquidGlassMenuHost.current

    val exploreSources by viewModel.exploreSources.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val exploreKinds by viewModel.exploreKinds.collectAsState()
    val selectedKind by viewModel.selectedKind.collectAsState()
    val books by viewModel.books.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val message by viewModel.message.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val layoutMode by viewModel.layoutMode.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val isInLibrary by viewModel.isInLibrary.collectAsState()
    val loadingDetail by viewModel.loadingDetail.collectAsState()

    // 流媒体分类流状态
    val categorySections by viewModel.categorySections.collectAsState()
    val isLoadingSections by viewModel.isLoadingSections.collectAsState()
    val activeCategoryDetail by viewModel.activeCategoryDetail.collectAsState()
    val categoryDetailBooks by viewModel.categoryDetailBooks.collectAsState()
    val isLoadingCategoryDetail by viewModel.isLoadingCategoryDetail.collectAsState()
    val hasMoreCategoryDetail by viewModel.hasMoreCategoryDetail.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSourcePickerSheet by remember { mutableStateOf(false) }
    LaunchedEffect(initialSearchQuery) {
        if (!initialSearchQuery.isNullOrBlank()) {
            searchQuery = initialSearchQuery
            viewModel.search(initialSearchQuery)
            onInitialSearchQueryConsumed()
        }
    }

    var sourceAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    var layoutAnchorBounds by remember { mutableStateOf(Rect.Zero) }

    val listState = rememberLazyListState()
    val gridState2 = rememberLazyGridState()
    val gridState3 = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val (total, last) = when (layoutMode) {
                1 -> {
                    val info = listState.layoutInfo
                    info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                }
                2 -> {
                    val info = gridState2.layoutInfo
                    info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                }
                else -> {
                    val info = gridState3.layoutInfo
                    info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: 0)
                }
            }
            total > 0 && last >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMore && !isLoadingMore && !loading) {
            viewModel.loadMore()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.pauseAllLoading()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    LaunchedEffect(exploreSources) {
        viewModel.maybeAutoSelect(exploreSources)
    }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarTop)
        ) {
            // 顶栏：自绘 Row 彻底消除状态栏与页面色差（对齐主页与书库）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpace.lg, end = AppSpace.sm, top = AppSpace.sm, bottom = AppSpace.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "发现",
                    fontSize = AppType.Display,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    letterSpacing = (-0.02).sp,
                    color = AppColors.TextPrimary
                )

                Spacer(Modifier.weight(1f))

                if (loading || searching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Accent
                    )
                }

                // 刷新按钮
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        tint = AppColors.TextPrimary
                    )
                }

                // 布局切换按钮（液态玻璃菜单）
                IconButton(
                    onClick = {
                        if (menuHost != null && layoutAnchorBounds != Rect.Zero) {
                            menuHost.show(
                                LiquidGlassMenuSpec(
                                    anchorBounds = layoutAnchorBounds,
                                    width = 180.dp,
                                    items = listOf(
                                        LiquidGlassMenuItem(
                                            label = context.getString(R.string.bookshelf_standard_grid),
                                            icon = Icons.Outlined.GridView,
                                            selected = layoutMode == 2,
                                            onClick = {
                                                menuHost.dismiss()
                                                viewModel.setLayoutMode(2)
                                            }
                                        ),
                                        LiquidGlassMenuItem(
                                            label = context.getString(R.string.bookshelf_compact_grid),
                                            icon = Icons.Outlined.ViewModule,
                                            selected = layoutMode == 3,
                                            onClick = {
                                                menuHost.dismiss()
                                                viewModel.setLayoutMode(3)
                                            }
                                        ),
                                        LiquidGlassMenuItem(
                                            label = context.getString(R.string.bookshelf_list_layout),
                                            icon = Icons.Outlined.ViewList,
                                            selected = layoutMode == 1,
                                            onClick = {
                                                menuHost.dismiss()
                                                viewModel.setLayoutMode(1)
                                            }
                                        )
                                    )
                                )
                            )
                        }
                    },
                    modifier = Modifier.onGloballyPositioned { layoutAnchorBounds = it.boundsInRoot() }
                ) {
                    Icon(
                        imageVector = when (layoutMode) {
                            1 -> Icons.Outlined.ViewList
                            3 -> Icons.Outlined.ViewModule
                            else -> Icons.Outlined.GridView
                        },
                        contentDescription = "切换布局",
                        tint = AppColors.TextPrimary
                    )
                }

                // 书源选择胶囊（液态玻璃半透明面板）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.CardBg)
                        .clickable { showSourcePickerSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = selectedSource?.bookSourceName ?: "选择书源",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 110.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AppColors.TextSecondary
                    )
                }
            }

            // 跨源搜索入口（点击直接导航至统一二级搜索界面）
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppColors.CardBg,
                border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.lg, vertical = 4.dp)
                    .clickable { onNavigateToSearch(null) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "搜索书名、作者",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }

            val displayBooks = if (searchResults.isNotEmpty()) searchResults else books

            if (searchResults.isNotEmpty()) {
                Text(
                    text = "搜索结果（${searchResults.size}）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = 6.dp)
                )
            }
            // 内容展示区：流媒体一级多分类横滑流优先，搜索模式或单分类按 layoutMode 分流
            when {
                // 1. 流媒体模式：非搜索状态且多分类流已准备
                searchResults.isEmpty() && (categorySections.isNotEmpty() || isLoadingSections) -> {
                    if (isLoadingSections && categorySections.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 120.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CircularProgressIndicator(color = AppColors.Accent)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
                        ) {
                            items(categorySections, key = { it.kind.title + (it.kind.url ?: "") }) { section ->
                                LaunchedEffect(section.kind.url) {
                                    viewModel.ensureCategoryLoaded(section.kind)
                                }
                                MediaCategorySectionRow(
                                    section = section,
                                    onBookClick = { viewModel.showBookActions(it) },
                                    onMoreClick = { viewModel.openCategoryDetail(section.kind) }
                                )
                            }
                        }
                    }
                }
                loading && displayBooks.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CircularProgressIndicator(color = AppColors.Accent)
                }
                displayBooks.isEmpty() -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp, start = 20.dp, end = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (exploreSources.isEmpty()) "暂无启用发现的书源\n请在书源管理中导入并启用" else if (searchQuery.isNotBlank()) "未搜索到相关网络书籍\n请更换关键词或切换书源" else "暂无书籍内容",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    if (onSearchInBookshelf != null && searchQuery.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.CardBg)
                                .clickable { onSearchInBookshelf(searchQuery) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoStories,
                                contentDescription = null,
                                tint = AppColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "在书库本地藏书中搜索「$searchQuery」",
                                color = AppColors.Accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                layoutMode == 1 -> {
                    // 列表布局
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = AppSpace.lg, end = AppSpace.lg, top = 6.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayBooks, key = { it.bookUrl }) {
                            DiscoveryBookListItem(book = it, onClick = { viewModel.showBookActions(it) })
                        }
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                                }
                            }
                        }
                    }
                }
                layoutMode == 2 -> {
                    // 标准网格布局（2列）
                    LazyVerticalGrid(
                        state = gridState2,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = AppSpace.lg, end = AppSpace.lg, top = 6.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayBooks, key = { it.bookUrl }) {
                            DiscoveryBookGridItem(
                                book = it,
                                compact = false,
                                onClick = { viewModel.showBookActions(it) }
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // 紧凑网格布局（3列）
                    LazyVerticalGrid(
                        state = gridState3,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = bottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayBooks, key = { it.bookUrl }) {
                            DiscoveryBookGridItem(
                                book = it,
                                compact = true,
                                onClick = { viewModel.showBookActions(it) }
                            )
                        }
                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppColors.Accent)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 流媒体二级分类详情页浮层（参考图 4：返回箭头 + 分类名 + 3 列瀑布流海报墙）
        activeCategoryDetail?.let { kind ->
            MediaCategoryDetailView(
                kind = kind,
                books = categoryDetailBooks,
                isLoading = isLoadingCategoryDetail,
                hasMore = hasMoreCategoryDetail,
                bottomPadding = bottomPadding,
                onBack = { viewModel.closeCategoryDetail() },
                onLoadMore = { viewModel.loadMoreCategoryDetail() },
                onBookClick = { viewModel.showBookActions(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarTop)
            )
        }

        // 图书操作与详情浮层（对齐书库图 3 操作浮层）
        activeBook?.let { book ->
            DiscoveryBookActionDialog(
                book = book,
                isInLibrary = isInLibrary,
                isLoadingDetail = loadingDetail,
                onDismiss = { viewModel.dismissBookActions() },
                onReadNow = {
                    viewModel.prepareReadBook(book) { lumiBookId, coverUrl, title ->
                        onOpenBook(lumiBookId, coverUrl, title)
                    }
                },
                onAddToBookshelf = { viewModel.addToBookshelf(book) }
            )
        }

        if (showSourcePickerSheet) {
            DiscoverySourcePickerSheet(
                sources = exploreSources,
                selectedSource = selectedSource,
                onSelectSource = { source ->
                    viewModel.selectSource(source)
                    showSourcePickerSheet = false
                },
                onDismiss = { showSourcePickerSheet = false }
            )
        }
    }
}

/** 统一图书封面组件：支持高清网络图加载与失败/空白时的书名纸张拟真卡片兜底，彻底杜绝灰块 */
@Composable
private fun DiscoveryBookCover(
    coverUrl: String?,
    name: String,
    author: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    var loadFailed by remember(coverUrl) { mutableStateOf(coverUrl.isNullOrBlank()) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFFEAEAF0)),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank() && !loadFailed) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = remember(coverUrl) {
                    coil.request.ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                },
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = { loadFailed = true }
            )
        }
        if (loadFailed || coverUrl.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE2E2EC))
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (author.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = author,
                        fontSize = 9.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 列表视图单项 */
@Composable
private fun DiscoveryBookListItem(
    book: SearchBook,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiscoveryBookCover(
            coverUrl = book.coverUrl,
            name = book.name,
            author = book.author,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(width = 56.dp, height = 76.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = book.author.ifBlank { "佚名" },
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            if (!book.kind.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.kind.orEmpty(),
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!book.latestChapterTitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "最新：${book.latestChapterTitle}",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = book.originName,
            fontSize = 11.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/** 网格视图单项（标准 2 列 / 紧凑 3 列） */
@Composable
private fun DiscoveryBookGridItem(
    book: SearchBook,
    compact: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        DiscoveryBookCover(
            coverUrl = book.coverUrl,
            name = book.name,
            author = book.author,
            shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(if (compact) 8.dp else 12.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = book.name,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = book.author.ifBlank { "佚名" },
            fontSize = if (compact) 10.sp else 12.sp,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 图书操作与详情对话框（玻璃化统一设计与丰富元数据） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryBookActionDialog(
    book: SearchBook,
    isInLibrary: Boolean,
    isLoadingDetail: Boolean = false,
    onDismiss: () -> Unit,
    onReadNow: () -> Unit,
    onAddToBookshelf: () -> Unit
) {
    val context = LocalContext.current
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var animState by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animState = true
    }
    var bookIntro by remember(book.bookUrl) { mutableStateOf<String?>(book.intro?.trim()) }
    var isIntroLoading by remember(book.bookUrl) { mutableStateOf(false) }

    LaunchedEffect(book.bookUrl) {
        if (!bookIntro.isNullOrBlank()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val cached = runCatching { io.legado.app.data.appDb.bookDao.getBook(book.bookUrl) }.getOrNull()
            val cachedIntro = cached?.getDisplayIntro()?.trim() ?: cached?.intro?.trim()
            if (!cachedIntro.isNullOrBlank()) {
                bookIntro = cachedIntro
                return@withContext
            }
            val source = runCatching { io.legado.app.data.appDb.bookSourceDao.getBookSource(book.origin) }.getOrNull()
            if (source != null) {
                isIntroLoading = true
                runCatching {
                    val targetBook = book.toBook().apply { infoHtml = null }
                    val detailed = io.legado.app.model.webBook.WebBook.getBookInfoAwait(source, targetBook)
                    val rawIntro = detailed.getDisplayIntro().orEmpty().ifBlank { detailed.intro.orEmpty() }.trim()
                    if (rawIntro.isNotBlank()) {
                        runCatching { io.legado.app.data.appDb.bookDao.insert(detailed) }
                        bookIntro = rawIntro
                    }
                }
                isIntroLoading = false
            }
        }
    }
    val contentScale by animateFloatAsState(
        targetValue = if (animState) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 280f),
        label = "discoveryDialogScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (animState) 1f else 0f,
        animationSpec = tween(180),
        label = "discoveryDialogAlpha"
    )

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    alpha = contentAlpha
                }
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            // 顶部：大封面 + 完整元数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                DiscoveryBookCover(
                    coverUrl = book.coverUrl,
                    name = book.name,
                    author = book.author,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(width = 80.dp, height = 108.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "作者：${book.author.ifBlank { "佚名" }}",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.Accent.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "书源：${book.originName}",
                                fontSize = 11.sp,
                                color = AppColors.Accent,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    val kinds = remember(book.kind) {
                        book.kind.orEmpty()
                            .split(",", " ", "/", "、")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(4)
                    }
                    if (kinds.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            kinds.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AppColors.BgGray)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 10.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    if (!book.latestChapterTitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "最新：${book.latestChapterTitle}",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = AppColors.TextSecondary)
                }
            }

            // 简介全文（可滚动，消除截断）
            val intro = bookIntro ?: book.intro?.trim()
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "书籍简介",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                if ((isLoadingDetail || isIntroLoading) && intro.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = AppColors.Accent
                        )
                        Text(
                            text = "正在同步详情...",
                            fontSize = 11.sp,
                            color = AppColors.Accent
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.BgGray.copy(alpha = 0.5f))
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = when {
                        !intro.isNullOrBlank() -> intro
                        isLoadingDetail || isIntroLoading -> "正在从书源拉取书籍简介与章节信息..."
                        else -> "暂无该书籍简介信息"
                    },
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 18.sp
                )
            }

            Spacer(Modifier.height(16.dp))

            // 动作按钮组：立即阅读 + 加入书架
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onReadNow,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("立即阅读", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onAddToBookshelf,
                    enabled = !isInLibrary,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (isInLibrary) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isInLibrary) "已在书架" else "加入书架")
                }
            }

            Spacer(Modifier.height(8.dp))

            // 辅助动作：复制书名与详情
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val info = "${book.name}\n作者：${book.author}\n书源：${book.originName}\n${book.bookUrl}"
                        val clip = ClipData.newPlainText("Book Info", info)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "已复制书籍信息", Toast.LENGTH_SHORT).show()
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = AppColors.TextSecondary
                )
                Spacer(Modifier.width(6.dp))
                Text("复制书名与链接", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.Center,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.50f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.25f)
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.90f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(28.dp),
                    color = AppColors.CardBg,
                    border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.3f)),
                    tonalElevation = 8.dp
                ) {
                    content()
                }
            }
        }
    }
}

/** 统一风格的书源切换面板（半透明液态玻璃设计，支持快速搜索过滤） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoverySourcePickerSheet(
    sources: List<BookSource>,
    selectedSource: BookSource?,
    onSelectSource: (BookSource) -> Unit,
    onDismiss: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var filterText by remember { mutableStateOf("") }
    val filteredSources = remember(sources, filterText) {
        if (filterText.isBlank()) sources
        else sources.filter { it.bookSourceName.contains(filterText.trim(), ignoreCase = true) }
    }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择发现书源",
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "当前共 ${sources.size} 个支持发现的书源",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (sources.size > 5) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("搜索书源名称...", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = AppColors.Divider
                    )
                )
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredSources, key = { it.bookSourceUrl }) { source ->
                    val isSelected = source.bookSourceUrl == selectedSource?.bookSourceUrl
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) AppColors.Accent.copy(alpha = 0.12f)
                                else AppColors.BgGray.copy(alpha = 0.42f)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) AppColors.Accent.copy(alpha = 0.6f) else AppColors.Divider.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onSelectSource(source)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.bookSourceName,
                                fontSize = AppType.BodySmall,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) AppColors.Accent else AppColors.TextPrimary
                            )
                            if (!source.bookSourceGroup.isNullOrBlank()) {
                                Text(
                                    text = source.bookSourceGroup.orEmpty(),
                                    fontSize = 10.sp,
                                    color = AppColors.TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmark,
                                contentDescription = "已选择",
                                tint = AppColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            alignment = Alignment.BottomCenter,
            shape = RoundedCornerShape(28.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.24f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.12f)
        ) {
            content()
        }
    } else {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = AppColors.CardBg,
            contentColor = AppColors.TextPrimary,
            scrimColor = Color.Black.copy(alpha = 0.12f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            content()
        }
    }
}

/**
 * 流媒体风格海报卡片（竖直海报封面 + 书名 + 作者）
 */
@Composable
private fun MediaBookCard(
    book: SearchBook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: androidx.compose.ui.unit.Dp = 108.dp
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
    ) {
        DiscoveryBookCover(
            coverUrl = book.coverUrl,
            name = book.name,
            author = book.author,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .shadow(3.dp, RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author,
                fontSize = 11.sp,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 流媒体一级分类行（分类名 + 右侧箭头 > + 横滑海报流）
 */
@Composable
private fun MediaCategorySectionRow(
    section: CategorySection,
    onBookClick: (SearchBook) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpace.lg, end = AppSpace.md, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = section.kind.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = (-0.2).sp
            )
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = "查看更多",
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (section.isLoading && section.books.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.Accent
                )
            }
        } else if (section.books.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(section.books, key = { it.bookUrl }) { book ->
                    MediaBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        cardWidth = 106.dp
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * 流媒体二级分类页面（参考图 4：返回箭头 + 分类标题 + 3 列瀑布流网格）
 */
@Composable
private fun MediaCategoryDetailView(
    kind: ExploreKind,
    books: List<SearchBook>,
    isLoading: Boolean,
    hasMore: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onBookClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && hasMore && !isLoading) {
            onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.CardBg)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = kind.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                letterSpacing = (-0.3).sp
            )
        }

        if (books.isEmpty() && isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                CircularProgressIndicator(color = AppColors.Accent)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 6.dp,
                    bottom = bottomPadding
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(books, key = { it.bookUrl }) { book ->
                    MediaBookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isLoading) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = AppColors.Accent
                            )
                        }
                    }
                }
            }
        }
    }
}
