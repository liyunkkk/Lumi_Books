package com.huangder.lumibooks.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.theme.AppColors

/**
 * 发现页：书源选择 + 分类标签 + 书籍列表 + 顶部跨源搜索
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel = viewModel()
) {
    val exploreSources by viewModel.exploreSources.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val exploreKinds by viewModel.exploreKinds.collectAsState()
    val selectedKind by viewModel.selectedKind.collectAsState()
    val books by viewModel.books.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val message by viewModel.message.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var sourceMenuVisible by remember { mutableStateOf(false) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    LaunchedEffect(exploreSources) {
        viewModel.maybeAutoSelect(exploreSources)
    }
    LaunchedEffect(message) {
        if (message != null) viewModel.consumeMessage()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .padding(top = statusBarTop)
    ) {
        TopAppBar(
            title = { Text("发现", fontWeight = FontWeight.Bold) },
            actions = {
                if (loading || searching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
                }
                Box {
                    Text(
                        text = selectedSource?.bookSourceName ?: "选择书源",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { sourceMenuVisible = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .background(AppColors.CardBg)
                    )
                    DropdownMenu(
                        expanded = sourceMenuVisible,
                        onDismissRequest = { sourceMenuVisible = false }
                    ) {
                        exploreSources.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.bookSourceName, maxLines = 1) },
                                onClick = {
                                    sourceMenuVisible = false
                                    viewModel.selectSource(source)
                                }
                            )
                        }
                    }
                }
            }
        )

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("跨书源搜索书名") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        viewModel.clearSearch()
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        if (searchResults.isNotEmpty()) {
            Text(
                text = "搜索结果（${searchResults.size}）",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults, key = { it.bookUrl }) { book ->
                    SearchBookItem(book)
                }
            }
        } else {
            // 分类标签
            if (exploreKinds.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exploreKinds, key = { it.title + (it.url ?: "") }) { kind ->
                        val selected = selectedKind == kind
                        Text(
                            text = kind.title,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else AppColors.TextPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { viewModel.selectKind(kind) }
                                .background(
                                    if (selected) AppColors.Accent else AppColors.CardBg
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            when {
                loading -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CircularProgressIndicator()
                }
                books.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (exploreSources.isEmpty()) "暂无启用发现的书源\n请先在书源管理中导入并启用"
                            else "暂无内容",
                            fontSize = 14.sp,
                            color = AppColors.TextSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 4.dp, bottom = bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(books, key = { it.bookUrl }) { book ->
                        SearchBookItem(book)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBookItem(book: io.legado.app.data.entities.SearchBook) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.CardBg)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 52.dp, height = 72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE8E8EC))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author.ifBlank { "佚名" },
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            if (!book.kind.isNullOrBlank()) {
                Text(
                    text = book.kind.orEmpty(),
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!book.latestChapterTitle.isNullOrBlank()) {
                Text(
                    text = book.latestChapterTitle.orEmpty(),
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
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
