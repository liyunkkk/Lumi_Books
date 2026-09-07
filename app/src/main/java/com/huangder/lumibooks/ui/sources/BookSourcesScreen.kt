package com.huangder.lumibooks.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
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
    viewModel: BookSourcesViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    val allSources by viewModel.allSources.collectAsState()
    val filteredSources by viewModel.filteredSources.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroup by viewModel.selectedGroup.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()
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
                            shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                                unfocusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.04f) else Color.Transparent
                            )
                        )
                    } else {
                        Column {
                            Text("书源管理", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = AppColors.TextPrimary)
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
                            Icon(Icons.Outlined.Search, contentDescription = "搜索书源", tint = AppColors.TextPrimary)
                        }

                        // 筛选按钮
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Outlined.FilterList, contentDescription = "筛选书源", tint = AppColors.TextPrimary)
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
                                Icon(Icons.Outlined.Add, contentDescription = "导入书源", tint = AppColors.TextPrimary)
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, tint = AppColors.Accent) },
                                    text = { Text("网络链接导入 (URL)") },
                                    onClick = {
                                        showImportMenu = false
                                        showUrlImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = AppColors.Accent) },
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
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空书源", tint = AppColors.TextSecondary)
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
                        shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 8.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Accent.copy(alpha = if (isLiquidGlass) 0.20f else 0.16f),
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
                            shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Accent.copy(alpha = if (isLiquidGlass) 0.20f else 0.16f),
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

    // ─── 弹窗部分（三大主题统一规范） ────────────────────────────────────
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

    // 清空确认弹窗（主题感知弹窗）
    if (showDeleteAllConfirm) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = {
                Text(
                    text = "清空全部书源",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除本地保存的所有书源吗？此操作不可逆。",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.deleteAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 10.dp)
                ) {
                    Text("确认清空", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) {
                    Text("取消", color = AppColors.TextSecondary)
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
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    val cardShape = RoundedCornerShape(if (isLiquidGlass) 20.dp else 14.dp)
    val cardBg = if (isLiquidGlass) AppColors.CardBg.copy(alpha = 0.82f) else AppColors.CardBg
    val cardBorder = if (isLiquidGlass) {
        BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f))
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(if (cardBorder != null) Modifier.border(cardBorder, cardShape) else Modifier),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLiquidGlass) 0.dp else 2.dp)
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
                                    .clip(RoundedCornerShape(6.dp))
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

                // 发现/探索独立开关（微晶体胶囊）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleExplore() }
                        .background(
                            if (source.enabledExplore) AppColors.Accent.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Explore,
                        contentDescription = "发现开关",
                        tint = if (source.enabledExplore) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (source.enabledExplore) "发现" else "无发现",
                        fontSize = 11.sp,
                        color = if (source.enabledExplore) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.5f),
                        fontWeight = if (source.enabledExplore) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(Modifier.width(8.dp))

                // 启用总开关（采用三大主题原生自适应的 LiquidGlassSwitch）
                LiquidGlassSwitch(
                    checked = source.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 中部：规则概况指示徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RuleBadge(
                    label = "搜索",
                    hasRule = !source.searchUrl.isNullOrBlank() || source.ruleSearch != null
                )
                RuleBadge(
                    label = "详情",
                    hasRule = source.ruleBookInfo != null && !source.ruleBookInfo?.tocUrl.isNullOrBlank()
                )
                RuleBadge(
                    label = "目录",
                    hasRule = source.ruleToc != null && !source.ruleToc?.chapterList.isNullOrBlank()
                )
                RuleBadge(
                    label = "正文",
                    hasRule = source.ruleContent != null && !source.ruleContent?.content.isNullOrBlank()
                )
                RuleBadge(
                    label = "发现",
                    hasRule = !source.exploreUrl.isNullOrBlank()
                )

                Spacer(Modifier.weight(1f))

                // 权重展示
                Text(
                    text = "权重: ${source.weight}",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // 底部：操作按钮栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 调试按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDebug() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = "调试",
                        tint = AppColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("调试", fontSize = 13.sp, color = AppColors.Accent)
                }

                Spacer(Modifier.width(4.dp))

                // 编辑按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "编辑",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("编辑", fontSize = 13.sp, color = AppColors.TextSecondary)
                }

                Spacer(Modifier.width(4.dp))

                // 删除按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDeleteDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("删除", fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }

    // 单项删除弹窗（主题感知弹窗）
    if (showDeleteDialog) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "确认删除书源",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除「${source.bookSourceName}」吗？",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 10.dp)
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

// ─── 规则概况徽章组件 ──────────────────────────────────────────────
@Composable
private fun RuleBadge(label: String, hasRule: Boolean) {
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    val bg = if (hasRule) {
        AppColors.Accent.copy(alpha = if (isLiquidGlass) 0.16f else 0.12f)
    } else {
        AppColors.Divider.copy(alpha = if (isLiquidGlass) 0.20f else 0.40f)
    }
    val textColor = if (hasRule) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = textColor, fontWeight = if (hasRule) FontWeight.Medium else FontWeight.Normal)
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
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "网络链接导入书源",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "支持直接粘贴在线 Raw JSON 链接（如 GitHub、Gitee 或私有服务器书源地址）：",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = {
                        Text(
                            "https://example.com/sources.json",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = if (isLiquidGlass) Color.White.copy(alpha = 0.2f) else AppColors.Divider,
                        focusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                        unfocusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.04f) else Color.Transparent
                    ),
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    urlText = clip.trim()
                                }
                            }
                        ) {
                            Text("粘贴", fontSize = 12.sp, color = AppColors.Accent)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (urlText.isNotBlank()) onConfirm(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    disabledContainerColor = AppColors.Accent.copy(alpha = 0.35f)
                )
            ) {
                Text("开始导入", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
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
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    LaunchedEffect(debugLogs.size) {
        if (debugLogs.isNotEmpty()) {
            listState.animateScrollToItem(debugLogs.size - 1)
        }
    }

    val dialogShape = RoundedCornerShape(if (isLiquidGlass) 28.dp else 20.dp)
    val dialogBg = if (isLiquidGlass) AppColors.CardBg.copy(alpha = 0.90f) else AppColors.CardBg
    val dialogBorder = if (isLiquidGlass) BorderStroke(0.6.dp, Color.White.copy(alpha = 0.20f)) else null

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(580.dp)
                .clip(dialogShape)
                .background(dialogBg)
                .then(if (dialogBorder != null) Modifier.border(dialogBorder, dialogShape) else Modifier)
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
                            color = AppColors.TextPrimary,
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
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = AppColors.TextSecondary)
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
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Accent,
                            unfocusedBorderColor = if (isLiquidGlass) Color.White.copy(alpha = 0.2f) else AppColors.Divider,
                            focusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                            unfocusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.03f) else Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isDebugging) {
                        Button(
                            onClick = onStopDebug,
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("停止", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = { onStartDebug(testKeyword) },
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("开始", color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 终端日志视窗（黑底极客终端高亮）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141416))
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(14.dp))
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
                            Text(
                                "复制全部日志",
                                color = if (debugLogs.isNotEmpty()) AppColors.Accent else AppColors.TextSecondary.copy(alpha = 0.4f)
                            )
                        }
                        TextButton(
                            onClick = onClearLogs,
                            enabled = debugLogs.isNotEmpty()
                        ) {
                            Text(
                                "清空",
                                color = if (debugLogs.isNotEmpty()) AppColors.TextSecondary else AppColors.TextSecondary.copy(alpha = 0.4f)
                            )
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
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

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

    val dialogShape = RoundedCornerShape(if (isLiquidGlass) 28.dp else 20.dp)
    val dialogBg = if (isLiquidGlass) AppColors.CardBg.copy(alpha = 0.92f) else AppColors.CardBg
    val dialogBorder = if (isLiquidGlass) BorderStroke(0.6.dp, Color.White.copy(alpha = 0.20f)) else null

    LiquidGlassDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(640.dp)
                .clip(dialogShape)
                .background(dialogBg)
                .then(if (dialogBorder != null) Modifier.border(dialogBorder, dialogShape) else Modifier)
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
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = AppColors.TextSecondary)
                        }
                        Spacer(Modifier.width(4.dp))
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
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("保存", color = Color.White)
                        }
                    }
                }

                // 模式切换 Tab
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("书源 URL") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = group,
                                onValueChange = { group = it },
                                label = { Text("书源分组 (逗号分隔)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("排序权重") },
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                            )
                        }

                        // 开关项（使用 LiquidGlassSwitch 自动适配三大主题）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用书源", fontSize = 14.sp, color = AppColors.TextPrimary)
                            LiquidGlassSwitch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("在「发现」页呈现 (enabledExplore)", fontSize = 14.sp, color = AppColors.TextPrimary)
                            LiquidGlassSwitch(checked = enabledExplore, onCheckedChange = { enabledExplore = it })
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("搜索规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = searchUrl,
                            onValueChange = { searchUrl = it },
                            label = { Text("搜索地址 (searchUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleSearchBookList,
                            onValueChange = { ruleSearchBookList = it },
                            label = { Text("列表规则 (bookList)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleSearchName,
                            onValueChange = { ruleSearchName = it },
                            label = { Text("书名规则 (name)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleSearchAuthor,
                            onValueChange = { ruleSearchAuthor = it },
                            label = { Text("作者规则 (author)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleSearchNoteUrl,
                            onValueChange = { ruleSearchNoteUrl = it },
                            label = { Text("详情页地址 (bookUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("详情规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = ruleInfoTocUrl,
                            onValueChange = { ruleInfoTocUrl = it },
                            label = { Text("目录页地址 (tocUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleInfoIntro,
                            onValueChange = { ruleInfoIntro = it },
                            label = { Text("简介规则 (intro)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("目录与正文规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = ruleTocChapterList,
                            onValueChange = { ruleTocChapterList = it },
                            label = { Text("目录列表规则 (chapterList)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleTocChapterName,
                            onValueChange = { ruleTocChapterName = it },
                            label = { Text("章节名规则 (chapterName)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleTocChapterUrl,
                            onValueChange = { ruleTocChapterUrl = it },
                            label = { Text("章节地址规则 (chapterUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )
                        OutlinedTextField(
                            value = ruleContentBody,
                            onValueChange = { ruleContentBody = it },
                            label = { Text("正文内容规则 (content)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
                        )

                        Spacer(Modifier.height(8.dp))
                        Text("发现规则", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.Accent)
                        OutlinedTextField(
                            value = exploreUrl,
                            onValueChange = { exploreUrl = it },
                            label = { Text("发现地址规则 (exploreUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp)
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
                            shape = RoundedCornerShape(if (isLiquidGlass) 14.dp else 10.dp),
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