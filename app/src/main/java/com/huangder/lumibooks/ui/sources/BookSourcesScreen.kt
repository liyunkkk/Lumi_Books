package com.huangder.lumibooks.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourcesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookSourcesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val allSources by viewModel.allSources.collectAsState()
    val filteredSources by viewModel.filteredSources.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.operationMessage.collectAsState()

    val debuggingSource by viewModel.debuggingSource.collectAsState()
    val editingSource by viewModel.editingSource.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchActive by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showUrlImportDialog by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val json = input.readBytes().decodeToString()
                    viewModel.importFromJson(json)
                }
            }.onFailure {
                // 由 ViewModel 或异常提示
            }
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = AppColors.WindowBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("搜索书源名称或域名", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Outlined.Close, contentDescription = "清除搜索", modifier = Modifier.size(20.dp))
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Column {
                            Text("书源管理", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "共 ${allSources.size} 个书源 (当前显示 ${filteredSources.size})",
                                fontSize = 11.sp,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.Accent
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        // 搜索按钮
                        IconButton(onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索书源")
                        }

                        // 筛选按钮
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "筛选书源")
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("全部书源") },
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.ALL)
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("仅已启用") },
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.ENABLED_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("仅已禁用") },
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.DISABLED_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("仅支持发现/探索") },
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.EXPLORE_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }

                        // 导入菜单
                        Box {
                            IconButton(onClick = { showImportMenu = true }) {
                                Icon(Icons.Outlined.Add, contentDescription = "导入书源")
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
                                    text = { Text("网络链接导入 (URL)") },
                                    onClick = {
                                        showImportMenu = false
                                        showUrlImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null) },
                                    text = { Text("本地文件导入 (JSON)") },
                                    onClick = {
                                        showImportMenu = false
                                        filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                    }
                                )
                            }
                        }

                        // 清空按钮
                        IconButton(onClick = { showDeleteAllConfirm = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空书源")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ─── 分组标签滑动栏 ──────────────────────────────────────────
            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AppSpace.md, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { viewModel.setSelectedGroup(null) },
                        label = { Text("全部 (${allSources.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent.copy(alpha = 0.16f),
                            selectedLabelColor = AppColors.Accent
                        )
                    )
                    groups.forEach { group ->
                        val count = allSources.count {
                            it.bookSourceGroup?.split(",", ";", "，", "；")?.map(String::trim)?.contains(group) == true
                        }
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = {
                                viewModel.setSelectedGroup(if (selectedGroup == group) null else group)
                            },
                            label = { Text("$group ($count)") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Accent.copy(alpha = 0.16f),
                                selectedLabelColor = AppColors.Accent
                            )
                        )
                    }
                }
            }

            // ─── 书源列表 ──────────────────────────────────────────────
            if (filteredSources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (allSources.isEmpty()) "暂无书源" else "没有匹配的书源",
                            fontSize = AppType.Body,
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.height(AppSpace.sm))
                        Text(
                            text = if (allSources.isEmpty()) "点击右上角 + 选择网络或本地 JSON 导入" else "请尝试调整分组或筛选关键词",
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = AppSpace.md, vertical = AppSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSources, key = { it.bookSourceUrl }) { source ->
                        SourceItemCard(
                            source = source,
                            onToggleEnabled = { viewModel.toggleEnabled(source) },
                            onToggleExplore = { viewModel.toggleExplore(source) },
                            onDebug = { viewModel.openDebug(source) },
                            onEdit = { viewModel.openEdit(source) },
                            onDelete = { viewModel.delete(source) }
                        )
                    }
                }
            }
        }
    }

    // ─── 弹窗部分 ──────────────────────────────────────────────────

    // 网络 URL 导入弹窗
    if (showUrlImportDialog) {
        ImportUrlDialog(
            onDismiss = { showUrlImportDialog = false },
            onConfirm = { url ->
                showUrlImportDialog = false
                viewModel.importFromUrl(url)
            }
        )
    }

    // 清空确认弹窗
    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("清空全部书源") },
            text = { Text("确定要删除本地保存的所有书源吗？此操作不可逆。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.deleteAll()
                    }
                ) {
                    Text("确认清空", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 在线调试弹窗
    debuggingSource?.let { source ->
        val isDebugging by viewModel.isDebugging.collectAsState()
        val debugLogs by viewModel.debugLogs.collectAsState()
        SourceDebugDialog(
            source = source,
            isDebugging = isDebugging,
            debugLogs = debugLogs,
            onStartDebug = { key -> viewModel.startDebug(source, key) },
            onStopDebug = { viewModel.stopDebug() },
            onClearLogs = { viewModel.clearDebugLogs() },
            onDismiss = { viewModel.closeDebug() }
        )
    }

    // 书源编辑弹窗
    editingSource?.let { source ->
        EditSourceDialog(
            source = source,
            onDismiss = { viewModel.closeEdit() },
            onSave = { updated -> viewModel.saveSource(updated) }
        )
    }
}

// ─── 单个书源卡片 ──────────────────────────────────────────────────

@Composable
private fun SourceItemCard(
    source: BookSource,
    onToggleEnabled: () -> Unit,
    onToggleExplore: () -> Unit,
    onDebug: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // 顶部：书源名称 + 发现开关 + 总开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = source.bookSourceName.ifBlank { "未命名书源" },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (source.enabled) AppColors.TextPrimary else AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!source.bookSourceGroup.isNullOrBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AppColors.Accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = source.bookSourceGroup ?: "",
                                    fontSize = 10.sp,
                                    color = AppColors.Accent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = source.bookSourceUrl,
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 发现/探索独立开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleExplore() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = "发现开关",
                        tint = if (source.enabledExplore) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = if (source.enabledExplore) "发现" else "无发现",
                        fontSize = 11.sp,
                        color = if (source.enabledExplore) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.5f),
                        fontWeight = if (source.enabledExplore) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 启用总开关
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppColors.Accent
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            // 底部分隔线与操作行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 调试按钮
                TextButton(
                    onClick = onDebug,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("调试", fontSize = 13.sp)
                }

                // 编辑按钮
                TextButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("编辑", fontSize = 13.sp)
                }

                // 删除按钮
                TextButton(
                    onClick = { showDeleteDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("删除", fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除书源") },
            text = { Text("确定要删除「${source.bookSourceName}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ─── 网络 URL 导入弹窗 ───────────────────────────────────────────

@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("网络链接导入书源") },
        text = {
            Column {
                Text(
                    "支持直接粘贴在线 Raw JSON 链接（如 GitHub、Gitee 或私有服务器书源地址）：",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = { Text("https://example.com/sources.json", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        TextButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                urlText = clip.trim()
                            }
                        }) {
                            Text("粘贴", fontSize = 12.sp)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onConfirm(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
            ) {
                Text("开始导入", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─── 在线实时调试终端弹窗 ───────────────────────────────────────────

@Composable
private fun SourceDebugDialog(
    source: BookSource,
    isDebugging: Boolean,
    debugLogs: List<String>,
    onStartDebug: (String) -> Unit,
    onStopDebug: () -> Unit,
    onClearLogs: () -> Unit,
    onDismiss: () -> Unit
) {
    var testKeyword by remember { mutableStateOf("完美世界") }
    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    LaunchedEffect(debugLogs.size) {
        if (debugLogs.isNotEmpty()) {
            listState.animateScrollToItem(debugLogs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(580.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 顶部标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "调试: ${source.bookSourceName}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = source.bookSourceUrl,
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 输入测试关键词与开始/停止按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testKeyword,
                        onValueChange = { testKeyword = it },
                        label = { Text("测试关键词或书籍/目录URL", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isDebugging) {
                        Button(
                            onClick = onStopDebug,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD92D3A))
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("停止", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { onStartDebug(testKeyword) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始", color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 终端日志视窗（黑底终端高亮）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141416))
                        .padding(10.dp)
                ) {
                    if (debugLogs.isEmpty()) {
                        Text(
                            text = "输入搜索词（默认「完美世界」），点击开始调试即可观察全链路请求和解析过程。",
                            color = Color(0xFF7E7E82),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(debugLogs) { log ->
                                val logColor = when {
                                    log.contains("Error") || log.contains("失败") || log.contains("异常") -> Color(0xFFFF6B6B)
                                    log.contains("⇒") || log.contains("︾") || log.contains("︽") -> Color(0xFF4AF626)
                                    log.contains("完成") || log.contains("成功") -> Color(0xFF64D2FF)
                                    else -> Color(0xFFD0D0D2)
                                }
                                Text(
                                    text = log,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = logColor,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 底部控制：复制日志、清空日志
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDebugging) "正在测试抓取解析..." else "日志条数: ${debugLogs.size}",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                    Row {
                        TextButton(
                            onClick = {
                                val fullLog = debugLogs.joinToString("\n")
                                clipboardManager.setText(AnnotatedString(fullLog))
                            },
                            enabled = debugLogs.isNotEmpty()
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("复制全部日志")
                        }
                        TextButton(
                            onClick = onClearLogs,
                            enabled = debugLogs.isNotEmpty()
                        ) {
                            Text("清空")
                        }
                    }
                }
            }
        }
    }
}

// ─── 全量字段编辑器 ───────────────────────────────────────────────

@Composable
private fun EditSourceDialog(
    source: BookSource,
    onDismiss: () -> Unit,
    onSave: (BookSource) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: 表单, 1: JSON

    // 表单状态
    var name by remember { mutableStateOf(source.bookSourceName) }
    var url by remember { mutableStateOf(source.bookSourceUrl) }
    var group by remember { mutableStateOf(source.bookSourceGroup ?: "") }
    var weight by remember { mutableStateOf(source.weight.toString()) }
    var enabled by remember { mutableStateOf(source.enabled) }
    var enabledExplore by remember { mutableStateOf(source.enabledExplore) }

    // 搜索规则
    var searchUrl by remember { mutableStateOf(source.searchUrl ?: "") }
    var ruleSearchBookList by remember { mutableStateOf(source.ruleSearch?.bookList ?: "") }
    var ruleSearchName by remember { mutableStateOf(source.ruleSearch?.name ?: "") }
    var ruleSearchAuthor by remember { mutableStateOf(source.ruleSearch?.author ?: "") }
    var ruleSearchIntro by remember { mutableStateOf(source.ruleSearch?.intro ?: "") }
    var ruleSearchCover by remember { mutableStateOf(source.ruleSearch?.coverUrl ?: "") }
    var ruleSearchNoteUrl by remember { mutableStateOf(source.ruleSearch?.bookUrl ?: "") }

    // 详情规则
    var ruleInfoName by remember { mutableStateOf(source.ruleBookInfo?.name ?: "") }
    var ruleInfoAuthor by remember { mutableStateOf(source.ruleBookInfo?.author ?: "") }
    var ruleInfoIntro by remember { mutableStateOf(source.ruleBookInfo?.intro ?: "") }
    var ruleInfoCover by remember { mutableStateOf(source.ruleBookInfo?.coverUrl ?: "") }
    var ruleInfoTocUrl by remember { mutableStateOf(source.ruleBookInfo?.tocUrl ?: "") }

    // 目录规则
    var ruleTocChapterList by remember { mutableStateOf(source.ruleToc?.chapterList ?: "") }
    var ruleTocChapterName by remember { mutableStateOf(source.ruleToc?.chapterName ?: "") }
    var ruleTocChapterUrl by remember { mutableStateOf(source.ruleToc?.chapterUrl ?: "") }

    // 正文规则
    var ruleContentBody by remember { mutableStateOf(source.ruleContent?.content ?: "") }

    // 发现规则
    var exploreUrl by remember { mutableStateOf(source.exploreUrl ?: "") }

    // JSON 模式状态
    var jsonText by remember { mutableStateOf(GSON.toJson(source)) }
    var jsonError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(640.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部导航与保存
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "编辑书源",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                if (selectedTab == 0) {
                                    // 表单模式保存
                                    val updated = source.copy(
                                        bookSourceName = name.trim(),
                                        bookSourceUrl = url.trim(),
                                        bookSourceGroup = group.trim().takeIf { it.isNotBlank() },
                                        weight = weight.toIntOrNull() ?: source.weight,
                                        enabled = enabled,
                                        enabledExplore = enabledExplore,
                                        searchUrl = searchUrl.trim().takeIf { it.isNotBlank() },
                                        exploreUrl = exploreUrl.trim().takeIf { it.isNotBlank() },
                                        ruleSearch = (source.ruleSearch ?: SearchRule()).apply {
                                            this.bookList = ruleSearchBookList.trim().takeIf { it.isNotBlank() }
                                            this.name = ruleSearchName.trim().takeIf { it.isNotBlank() }
                                            this.author = ruleSearchAuthor.trim().takeIf { it.isNotBlank() }
                                            this.intro = ruleSearchIntro.trim().takeIf { it.isNotBlank() }
                                            this.coverUrl = ruleSearchCover.trim().takeIf { it.isNotBlank() }
                                            this.bookUrl = ruleSearchNoteUrl.trim().takeIf { it.isNotBlank() }
                                        },
                                        ruleBookInfo = (source.ruleBookInfo ?: BookInfoRule()).apply {
                                            this.name = ruleInfoName.trim().takeIf { it.isNotBlank() }
                                            this.author = ruleInfoAuthor.trim().takeIf { it.isNotBlank() }
                                            this.intro = ruleInfoIntro.trim().takeIf { it.isNotBlank() }
                                            this.coverUrl = ruleInfoCover.trim().takeIf { it.isNotBlank() }
                                            this.tocUrl = ruleInfoTocUrl.trim().takeIf { it.isNotBlank() }
                                        },
                                        ruleToc = (source.ruleToc ?: TocRule()).apply {
                                            this.chapterList = ruleTocChapterList.trim().takeIf { it.isNotBlank() }
                                            this.chapterName = ruleTocChapterName.trim().takeIf { it.isNotBlank() }
                                            this.chapterUrl = ruleTocChapterUrl.trim().takeIf { it.isNotBlank() }
                                        },
                                        ruleContent = (source.ruleContent ?: ContentRule()).apply {
                                            this.content = ruleContentBody.trim().takeIf { it.isNotBlank() }
                                        }
                                    )
                                    onSave(updated)
                                } else {
                                    // JSON 模式解析保存
                                    runCatching {
                                        val parsed = GSON.fromJsonObject<BookSource>(jsonText.trim()).getOrThrow()
                                        onSave(parsed)
                                    }.onFailure {
                                        jsonError = "JSON 解析错误: ${it.message}"
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("保存", color = Color.White)
                        }
                    }
                }

                // 模式切换 Tab
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            if (selectedTab == 1) {
                                // 从 JSON 反映回表单
                                runCatching {
                                    val parsed = GSON.fromJsonObject<BookSource>(jsonText.trim()).getOrThrow()
                                    name = parsed.bookSourceName
                                    url = parsed.bookSourceUrl
                                    group = parsed.bookSourceGroup ?: ""
                                    searchUrl = parsed.searchUrl ?: ""
                                    exploreUrl = parsed.exploreUrl ?: ""
                                }
                            }
                            selectedTab = 0
                        },
                        text = { Text("规则表单") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            // 从表单反映到 JSON
                            val updated = source.copy(
                                bookSourceName = name.trim(),
                                bookSourceUrl = url.trim(),
                                bookSourceGroup = group.trim().takeIf { it.isNotBlank() },
                                weight = weight.toIntOrNull() ?: source.weight,
                                enabled = enabled,
                                enabledExplore = enabledExplore,
                                searchUrl = searchUrl.trim().takeIf { it.isNotBlank() },
                                exploreUrl = exploreUrl.trim().takeIf { it.isNotBlank() }
                            )
                            jsonText = GSON.toJson(updated)
                            jsonError = null
                            selectedTab = 1
                        },
                        text = { Text("JSON 源码") }
                    )
                }

                if (selectedTab == 0) {
                    // 表单编辑视图
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("基础信息", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("书源名称") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("书源 URL") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = group,
                                onValueChange = { group = it },
                                label = { Text("书源分组 (逗号分隔)") },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("排序权重") },
                                modifier = Modifier.width(100.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用书源", fontSize = 14.sp)
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("在「发现」页呈现 (enabledExplore)", fontSize = 14.sp)
                            Switch(checked = enabledExplore, onCheckedChange = { enabledExplore = it })
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("搜索规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = searchUrl,
                            onValueChange = { searchUrl = it },
                            label = { Text("搜索地址 (searchUrl)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleSearchBookList,
                            onValueChange = { ruleSearchBookList = it },
                            label = { Text("列表规则 (bookList)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleSearchName,
                            onValueChange = { ruleSearchName = it },
                            label = { Text("书名规则 (name)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleSearchAuthor,
                            onValueChange = { ruleSearchAuthor = it },
                            label = { Text("作者规则 (author)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleSearchNoteUrl,
                            onValueChange = { ruleSearchNoteUrl = it },
                            label = { Text("详情页地址 (bookUrl)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("详情规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = ruleInfoTocUrl,
                            onValueChange = { ruleInfoTocUrl = it },
                            label = { Text("目录页地址 (tocUrl)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleInfoIntro,
                            onValueChange = { ruleInfoIntro = it },
                            label = { Text("简介规则 (intro)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("目录与正文规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = ruleTocChapterList,
                            onValueChange = { ruleTocChapterList = it },
                            label = { Text("目录列表规则 (chapterList)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleTocChapterName,
                            onValueChange = { ruleTocChapterName = it },
                            label = { Text("章节名规则 (chapterName)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleTocChapterUrl,
                            onValueChange = { ruleTocChapterUrl = it },
                            label = { Text("章节地址规则 (chapterUrl)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = ruleContentBody,
                            onValueChange = { ruleContentBody = it },
                            label = { Text("正文内容规则 (content)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("发现规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = exploreUrl,
                            onValueChange = { exploreUrl = it },
                            label = { Text("发现地址规则 (exploreUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                } else {
                    // JSON 源码编辑视图
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        jsonError?.let {
                            Text(it, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = {
                                jsonText = it
                                jsonError = null
                            },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
