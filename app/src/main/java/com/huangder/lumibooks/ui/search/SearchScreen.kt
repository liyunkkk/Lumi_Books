package com.huangder.lumibooks.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenBook: (bookId: String, coverUrl: String?, title: String) -> Unit,
    initialQuery: String? = null,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val query by viewModel.query.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val exactSearchEnabled by viewModel.exactSearchEnabled.collectAsState()
    val searchMode by viewModel.searchMode.collectAsState()
    val searchTypeFilter by viewModel.searchTypeFilter.collectAsState()
    val allSources by viewModel.allSources.collectAsState()
    val selectedSourceUrls by viewModel.selectedSourceUrls.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val message by viewModel.message.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var showTypeFilterDialog by remember { mutableStateOf(false) }
    var selectedBookForAction by remember { mutableStateOf<SearchBook?>(null) }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.search(initialQuery)
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = AppColors.TextPrimary
                        )
                    }
                    Text(
                        text = "搜索",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "搜索设置",
                            tint = AppColors.TextPrimary
                        )
                    }
                    IconButton(onClick = { showSourcesDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MyLocation,
                            contentDescription = "搜索书源",
                            tint = if (selectedSourceUrls.size < allSources.size && allSources.isNotEmpty()) AppColors.Accent else AppColors.TextPrimary
                        )
                    }
                    IconButton(onClick = { showTypeFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = "类型过滤",
                            tint = if (searchTypeFilter != 0) AppColors.Accent else AppColors.TextPrimary
                        )
                    }
                }

                // 搜索输入框
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.setQuery(it) },
                    placeholder = {
                        Text(
                            text = "搜索书名、作者",
                            fontSize = 15.sp,
                            color = AppColors.TextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = AppColors.TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清除",
                                    tint = AppColors.TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.search(query)
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.CardBg,
                        unfocusedContainerColor = AppColors.CardBg,
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = AppColors.Divider.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                // 搜索维度切换小标签（综合 / 书名 / 作者）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "全部", 1 to "按书名", 2 to "按作者").forEach { (mode, label) ->
                        val isSelected = searchMode == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AppColors.Accent.copy(alpha = 0.15f) else AppColors.CardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) AppColors.Accent.copy(alpha = 0.6f) else AppColors.Divider.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.clickable {
                                viewModel.setSearchMode(mode)
                                if (query.isNotBlank()) {
                                    viewModel.search(query)
                                }
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (isSelected) AppColors.Accent else AppColors.TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        containerColor = AppColors.WindowBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (searchResults.isEmpty() && !isSearching) {
                // 搜索历史视图（示图 1）
                if (searchHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = AppColors.TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "搜索历史",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = AppColors.TextPrimary
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.clearSearchHistory() }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    text = "清除所有",
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchHistory, key = { it }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppColors.CardBg,
                                    border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.search(item)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 14.sp,
                                            color = AppColors.TextPrimary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeSearchHistory(item) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "删除",
                                                tint = AppColors.TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "输入书名或作者开始全网书源检索",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                // 搜索结果列表
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isSearching) "正在多源搜索中（已找到 ${searchResults.size} 本）" else "共检索到 ${searchResults.size} 本书籍",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AppColors.Accent
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(searchResults, key = { it.bookUrl }) { book ->
                            SearchResultBookCard(
                                book = book,
                                onClick = { selectedBookForAction = book }
                            )
                        }
                    }
                }
            }
        }
    }

    // 搜索设置弹窗
    if (showSettingsDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text("搜索设置", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("精确搜索", fontSize = 15.sp, color = AppColors.TextPrimary)
                        Switch(
                            checked = exactSearchEnabled,
                            onCheckedChange = { viewModel.toggleExactSearch(it) }
                        )
                    }
                    Text(
                        text = "开启精确搜索后，仅完全匹配书名或作者的书籍会被展示。",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            },
            confirmButton = {
                LiquidGlassTextButton(text = "完成", onClick = { showSettingsDialog = false })
            }
        )
    }

    // 勾选书源弹窗
    if (showSourcesDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showSourcesDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("勾选搜索书源", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.selectAllSources() }) {
                            Text("全选", fontSize = 13.sp, color = AppColors.Accent)
                        }
                        TextButton(onClick = { viewModel.invertSources() }) {
                            Text("反选", fontSize = 13.sp, color = AppColors.Accent)
                        }
                    }
                }
            },
            text = {
                if (allSources.isEmpty()) {
                    Text("未发现可用书源，请先导入书源", fontSize = 14.sp, color = AppColors.TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allSources, key = { it.bookSourceUrl }) { source ->
                            val isChecked = selectedSourceUrls.contains(source.bookSourceUrl)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AppColors.CardBg,
                                border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleSource(source.bookSourceUrl) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.bookSourceName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AppColors.TextPrimary
                                        )
                                        Text(
                                            text = source.bookSourceUrl,
                                            fontSize = 11.sp,
                                            color = AppColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { viewModel.toggleSource(source.bookSourceUrl) }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                LiquidGlassTextButton(text = "确认 (${selectedSourceUrls.size}/${allSources.size})", onClick = { showSourcesDialog = false })
            }
        )
    }

    // 搜索类型弹窗
    if (showTypeFilterDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showTypeFilterDialog = false },
            title = {
                Text("搜索类型", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to "全部类型",
                        1 to "文本小说",
                        2 to "漫画图文",
                        3 to "有声音频"
                    ).forEach { (type, label) ->
                        val isSelected = searchTypeFilter == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) AppColors.Accent.copy(alpha = 0.15f) else AppColors.CardBg,
                            border = BorderStroke(1.dp, if (isSelected) AppColors.Accent else AppColors.Divider.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSearchTypeFilter(type)
                                    showTypeFilterDialog = false
                                    if (query.isNotBlank()) {
                                        viewModel.search(query)
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AppColors.Accent else AppColors.TextPrimary
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.setSearchTypeFilter(type)
                                        showTypeFilterDialog = false
                                        if (query.isNotBlank()) {
                                            viewModel.search(query)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                LiquidGlassTextButton(text = "关闭", onClick = { showTypeFilterDialog = false })
            }
        )
    }

    // 书籍点击操作弹窗
    selectedBookForAction?.let { book ->
        LiquidGlassAlertDialog(
            onDismissRequest = { selectedBookForAction = null },
            title = {
                Text(book.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("作者: ${book.author.ifBlank { "未知" }}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    Text("书源: ${book.originName}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    if (!book.latestChapterTitle.isNullOrBlank()) {
                        Text("最新: ${book.latestChapterTitle}", fontSize = 13.sp, color = AppColors.TextSecondary)
                    }
                    if (!book.intro.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = book.intro ?: "",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = "立即阅读",
                    onClick = {
                        selectedBookForAction = null
                        viewModel.addToBookshelf(book) { lumiId ->
                            onOpenBook(lumiId, book.coverUrl, book.name)
                        }
                    }
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = "加入书架",
                    onClick = {
                        selectedBookForAction = null
                        viewModel.addToBookshelf(book) {}
                    }
                )
            }
        )
    }
}

@Composable
private fun SearchResultBookCard(
    book: SearchBook,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AppColors.CardBg,
        border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppColors.Divider.copy(alpha = 0.3f),
                modifier = Modifier.size(width = 54.dp, height = 74.dp)
            ) {
                if (!book.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = book.name.take(1),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 书籍信息
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = book.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.Accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = book.originName.ifBlank { "未知书源" },
                            fontSize = 11.sp,
                            color = AppColors.Accent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = book.author.ifBlank { "佚名" },
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )

                if (!book.latestChapterTitle.isNullOrBlank()) {
                    Text(
                        text = "最新: ${book.latestChapterTitle}",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
