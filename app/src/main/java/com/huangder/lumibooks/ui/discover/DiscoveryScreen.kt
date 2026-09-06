package com.huangder.lumibooks.ui.discover

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.AppColors
import io.legado.app.data.entities.SearchBook

/**
 * 发现页：书源选择（液态玻璃菜单）+ 布局切换（标准网格/紧凑网格/列表）+ 跨源搜索 + 图书详情操作弹窗与在线阅读打通
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onOpenBook: (String) -> Unit = {},
    viewModel: DiscoveryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
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
    val layoutMode by viewModel.layoutMode.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val isInLibrary by viewModel.isInLibrary.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sourceAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    var layoutAnchorBounds by remember { mutableStateOf(Rect.Zero) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.WindowBg)
                .padding(top = statusBarTop)
        ) {
            // 顶栏：标题 + 状态/刷新/布局切换 + 书源胶囊（液态玻璃菜单）
            TopAppBar(
                title = {
                    Text(
                        text = "发现",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                },
                actions = {
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

                    // 书源选择胶囊（统一液态玻璃弹窗）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppColors.CardBg)
                            .clickable {
                                if (menuHost != null && sourceAnchorBounds != Rect.Zero && exploreSources.isNotEmpty()) {
                                    menuHost.show(
                                        LiquidGlassMenuSpec(
                                            anchorBounds = sourceAnchorBounds,
                                            width = 220.dp,
                                            maxVisibleItems = 8,
                                            items = exploreSources.map { source ->
                                                LiquidGlassMenuItem(
                                                    label = source.bookSourceName,
                                                    selected = source.bookSourceUrl == selectedSource?.bookSourceUrl,
                                                    onClick = {
                                                        menuHost.dismiss()
                                                        viewModel.selectSource(source)
                                                    }
                                                )
                                            }
                                        )
                                    )
                                }
                            }
                            .onGloballyPositioned { sourceAnchorBounds = it.boundsInRoot() }
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
            )

            // 跨源搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isBlank()) viewModel.clearSearch()
                    else viewModel.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("跨书源搜索书名或作者", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AppColors.TextSecondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清除", tint = AppColors.TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            val displayBooks = if (searchResults.isNotEmpty()) searchResults else books

            if (searchResults.isNotEmpty()) {
                Text(
                    text = "搜索结果（${searchResults.size}）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else if (exploreKinds.isNotEmpty()) {
                // 分类标签栏
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exploreKinds, key = { it.title + (it.url ?: "") }) {
                        val selected = selectedKind == it
                        Text(
                            text = it.title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else AppColors.TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (selected) AppColors.Accent else AppColors.CardBg)
                                .clickable { viewModel.selectKind(it) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 内容展示区：按 layoutMode 分流
            when {
                loading && displayBooks.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CircularProgressIndicator(color = AppColors.Accent)
                }
                displayBooks.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = if (exploreSources.isEmpty()) "暂无启用发现的书源\n请在书源管理中导入并启用" else "暂无书籍内容",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )
                }
                layoutMode == 1 -> {
                    // 列表布局
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(displayBooks, key = { it.bookUrl }) {
                            DiscoveryBookListItem(book = it, onClick = { viewModel.showBookActions(it) })
                        }
                    }
                }
                layoutMode == 2 -> {
                    // 标准网格布局（2列，对齐书库标准网格）
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottomPadding),
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
                    }
                }
                else -> {
                    // 紧凑网格布局（3列，对齐书库紧凑网格）
                    LazyVerticalGrid(
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
                    }
                }
            }
        }

        // 图书操作与详情浮层（需求 3：对齐书库操作体验）
        activeBook?.let { book ->
            DiscoveryBookActionDialog(
                book = book,
                isInLibrary = isInLibrary,
                onDismiss = { viewModel.dismissBookActions() },
                onReadNow = {
                    viewModel.prepareReadBook(book) { lumiBookId ->
                        onOpenBook(lumiBookId)
                    }
                },
                onAddToBookshelf = { viewModel.addToBookshelf(book) }
            )
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
        AsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE8E8EC))
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
        AsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(if (compact) 8.dp else 12.dp))
                .background(Color(0xFFE8E8EC))
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

/** 图书操作与详情对话框（需求 3：对齐书库图 3 操作浮层） */
@Composable
private fun DiscoveryBookActionDialog(
    book: SearchBook,
    isInLibrary: Boolean,
    onDismiss: () -> Unit,
    onReadNow: () -> Unit,
    onAddToBookshelf: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                    onDismiss()
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(AppColors.CardBg)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(20.dp)
            ) {
                // 顶部：封面与元数据
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 68.dp, height = 92.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE8E8EC))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = book.name,
                            fontSize = 17.sp,
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
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "书源：${book.originName}",
                            fontSize = 12.sp,
                            color = AppColors.Accent,
                            fontWeight = FontWeight.Medium
                        )
                        if (!book.latestChapterTitle.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "最新：${book.latestChapterTitle}",
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = AppColors.TextSecondary)
                    }
                }

                // 简介（如有）
                val intro = book.intro?.trim()
                if (!intro.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = intro,
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 18.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.BgGray)
                            .padding(10.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // 动作按钮组：立即阅读 + 加入书架 + 复制信息
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 立即阅读（主按钮）
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

                    // 加入书架（次按钮）
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
    }
}
