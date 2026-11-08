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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.huangder.lumibooks.ui.components.LiquidGlassButton
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LiquidGlassSwitch
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.components.lumiCardSurface
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.cardOutline
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookSourcesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookSourcesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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
                    viewModel.prepareImportFromJson(json)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = "返回",
                    onClick = {
                        if (isSearchActive) {
                            isSearchActive = false
                            viewModel.setSearchQuery("")
                        } else {
                            onNavigateBack()
                        }
                    },
                    settingsBackButton = true
                )

                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("搜索书源名称或域名", fontSize = AppType.BodySmall, color = AppColors.TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(AppIcons.X, contentDescription = "清除搜索", tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                                }
                            }
                        },
                        shape = AppRoundedCornerShape(AppRadius.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                            unfocusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.04f) else Color.Transparent
                        )
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = AppSpace.sm)
                    ) {
                        Text(
                            text = "书源管理",
                            fontSize = AppType.Section,
                            fontWeight = FontWeight.Bold,
                            fontFamily = resolveAppFontFamily(fangSongFamily()),
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "共 ${allSources.size} 个书源 (当前显示 ${filteredSources.size})",
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.width(AppSpace.sm))
                    } else {
                        // 搜索按钮
                        LiquidGlassIconButton(
                            imageVector = AppIcons.MagnifyingGlass,
                            contentDescription = "搜索书源",
                            onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) viewModel.setSearchQuery("")
                            },
                            size = 40.dp,
                            iconSize = 20.dp,
                            contentColor = if (isSearchActive) AppColors.TextPrimary else AppColors.TextPrimary
                        )

                        // 筛选按钮
                        Box {
                            LiquidGlassIconButton(
                                imageVector = AppIcons.ListBullets,
                                contentDescription = "筛选书源",
                                onClick = { showFilterMenu = true },
                                size = 40.dp,
                                iconSize = 20.dp
                            )
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                                modifier = Modifier
                                    .width(185.dp)
                                    .padding(horizontal = AppSpace.xs),
                                shape = AppRoundedCornerShape(AppRadius.lg),
                                containerColor = if (isLiquidGlass) AppColors.CardBg.copy(alpha = 0.82f) else AppColors.CardBg.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.7f)),
                                shadowElevation = 10.dp
                            ) {
                                GlassDropdownMenuItem(
                                    text = "全部书源",
                                    selected = filterStatus == SourceFilter.ALL,
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.ALL)
                                        showFilterMenu = false
                                    }
                                )
                                GlassDropdownMenuItem(
                                    text = "仅已启用",
                                    selected = filterStatus == SourceFilter.ENABLED_ONLY,
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.ENABLED_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                                GlassDropdownMenuItem(
                                    text = "仅已禁用",
                                    selected = filterStatus == SourceFilter.DISABLED_ONLY,
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.DISABLED_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                                GlassDropdownMenuItem(
                                    text = "仅支持发现/探索",
                                    selected = filterStatus == SourceFilter.EXPLORE_ONLY,
                                    onClick = {
                                        viewModel.setFilterStatus(SourceFilter.EXPLORE_ONLY)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                        // 导入菜单
                        Box {
                            LiquidGlassIconButton(
                                imageVector = AppIcons.Plus,
                                contentDescription = "导入书源",
                                onClick = { showImportMenu = true },
                                size = 40.dp,
                                iconSize = 20.dp
                            )
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                                modifier = Modifier
                                    .width(210.dp)
                                    .padding(horizontal = AppSpace.xs),
                                shape = AppRoundedCornerShape(AppRadius.lg),
                                containerColor = if (isLiquidGlass) AppColors.CardBg.copy(alpha = 0.82f) else AppColors.CardBg.copy(alpha = 0.95f),
                                border = BorderStroke(1.dp, AppColors.Divider.copy(alpha = 0.7f)),
                                shadowElevation = 10.dp
                            ) {
                                GlassDropdownMenuItem(
                                    leadingIcon = AppIcons.Globe,
                                    text = "网络链接导入 (URL)",
                                    onClick = {
                                        showImportMenu = false
                                        showUrlImportDialog = true
                                    }
                                )
                                GlassDropdownMenuItem(
                                    leadingIcon = AppIcons.UploadSimple,
                                    text = "本地文件导入 (JSON)",
                                    onClick = {
                                        showImportMenu = false
                                        filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                                    }
                                )
                            }
                        }
                        // 清空按钮
                        LiquidGlassIconButton(
                            imageVector = AppIcons.Trash,
                            contentDescription = "清空书源",
                            onClick = { showDeleteAllConfirm = true },
                            size = 40.dp,
                            iconSize = 20.dp,
                            contentColor = AppColors.TextSecondary
                        )
                    }
                }
            }
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
                        .padding(horizontal = AppSpace.md, vertical = AppSpace.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { viewModel.setSelectedGroup(null) },
                        label = { Text("全部 (${allSources.size})", fontSize = AppType.Caption) },
                        shape = AppRoundedCornerShape(AppRadius.capsule),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.Divider.copy(alpha = 0.6f),
                            selectedLabelColor = AppColors.TextPrimary
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
                            label = { Text("$group ($count)", fontSize = AppType.Caption) },
                            shape = AppRoundedCornerShape(AppRadius.capsule),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.Divider.copy(alpha = 0.6f),
                                selectedLabelColor = AppColors.TextPrimary
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
                    verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
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
                viewModel.prepareImportFromUrl(url)
            }
        )
    }

    // 导入确认弹窗（支持分组选择与覆盖更新提示）
    val pendingImportSources by viewModel.pendingImportSources.collectAsState()
    pendingImportSources?.let { sources ->
        ImportSourceConfirmDialog(
            sources = sources,
            allSources = allSources,
            availableGroups = groups,
            onDismiss = { viewModel.cancelPendingImport() },
            onConfirm = { group, overrideExisting ->
                viewModel.confirmImport(sources, group, overrideExisting)
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
                    fontSize = AppType.Section,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除本地保存的所有书源吗？此操作不可逆。",
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = "确认清空",
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.deleteAll()
                    },
                    tintedColor = Color(0xFFD92D3A),
                    contentColor = Color.White
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = "取消",
                    onClick = { showDeleteAllConfirm = false },
                    contentColor = AppColors.TextPrimary
                )
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

    SourceDetailCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpace.md)
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
                            fontSize = AppType.Body,
                            fontWeight = FontWeight.Bold,
                            color = if (source.enabled) AppColors.TextPrimary else AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!source.bookSourceGroup.isNullOrBlank()) {
                            Spacer(Modifier.width(AppSpace.xs))
                            Box(
                                modifier = Modifier
                                    .clip(AppRoundedCornerShape(AppRadius.sm))
                                    .background(AppColors.Divider.copy(alpha = 0.5f))
                                    .padding(horizontal = AppSpace.xs, vertical = 2.dp)
                            ) {
                                Text(
                                    text = source.bookSourceGroup ?: "",
                                    fontSize = AppType.Caption,
                                    color = AppColors.TextPrimary,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = source.bookSourceUrl,
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 发现/探索独立开关（微晶体胶囊）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(AppRoundedCornerShape(AppRadius.sm))
                        .clickable { onToggleExplore() }
                        .background(
                            if (source.enabledExplore) AppColors.Divider.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)
                ) {
                    Icon(
                        imageVector = AppIcons.Globe,
                        contentDescription = "发现开关",
                        tint = if (source.enabledExplore) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(AppSpace.xs))
                    Text(
                        text = if (source.enabledExplore) "发现" else "无发现",
                        fontSize = AppType.Caption,
                        color = if (source.enabledExplore) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.5f),
                        fontWeight = if (source.enabledExplore) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(Modifier.width(AppSpace.sm))

                // 启用总开关（采用三大主题原生自适应的 LiquidGlassSwitch）
                LiquidGlassSwitch(
                    checked = source.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(Modifier.height(AppSpace.sm))

            // 中部：规则概况指示徽章
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
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
                    fontSize = AppType.Caption,
                    color = AppColors.TextSecondary.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(AppSpace.sm))

            // 底部：操作按钮栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 调试按钮
                LiquidGlassIconButton(
                    imageVector = AppIcons.Bug,
                    contentDescription = "调试",
                    onClick = onDebug,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextPrimary
                )

                // 编辑按钮
                LiquidGlassIconButton(
                    imageVector = AppIcons.PencilSimple,
                    contentDescription = "编辑",
                    onClick = onEdit,
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextSecondary
                )

                // 删除按钮（危险操作保留红色语义）
                LiquidGlassIconButton(
                    imageVector = AppIcons.Trash,
                    contentDescription = "删除",
                    onClick = { showDeleteDialog = true },
                    size = 36.dp,
                    iconSize = 18.dp,
                    contentColor = SourceDangerColor
                )
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
                    fontSize = AppType.Section,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除「${source.bookSourceName}」吗？",
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                LiquidGlassTextButton(
                    text = "删除",
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    tintedColor = SourceDangerColor,
                    contentColor = Color.White
                )
            },
            dismissButton = {
                LiquidGlassTextButton(
                    text = "取消",
                    onClick = { showDeleteDialog = false },
                    contentColor = AppColors.TextPrimary
                )
            }
        )
    }
}

/** 危险操作统一用色，与书架页 book_file_unavailable 保持一致。 */
private val SourceDangerColor = Color(0xFFD92D3A)

/** 卡片容器：与设置页 DetailCard 同一套描边 / 圆角 / 玻璃底视觉。 */
@Composable
private fun SourceDetailCard(content: @Composable () -> Unit) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color(0x06000000), spotColor = Color(0x06000000))
            .cardOutline(shape)
            .clip(shape)
            .lumiCardSurface(shape = shape)
    ) { content() }
}

// ─── 规则概况徽章组件 ──────────────────────────────────────────────
@Composable
private fun RuleBadge(label: String, hasRule: Boolean) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    val bg = if (hasRule) {
        AppColors.Divider.copy(alpha = if (isLiquidGlass) 0.6f else 0.5f)
    } else {
        AppColors.Divider.copy(alpha = if (isLiquidGlass) 0.20f else 0.40f)
    }
    val textColor = if (hasRule) AppColors.TextPrimary else AppColors.TextSecondary.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .clip(AppRoundedCornerShape(AppRadius.sm))
            .background(bg)
            .padding(horizontal = AppSpace.xs, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = AppType.Caption,
            color = textColor,
            fontWeight = if (hasRule) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1
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
    val appTheme = LocalAppTheme.current
    val isLiquidGlass = appTheme == "liquid_glass"

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "网络链接导入书源",
                fontWeight = FontWeight.Bold,
                fontSize = AppType.Section,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "支持直接粘贴在线 Raw JSON 链接（如 GitHub、Gitee 或私有服务器书源地址）：",
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextSecondary,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(AppSpace.md))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    placeholder = {
                        Text(
                            "https://example.com/sources.json",
                            fontSize = AppType.BodySmall,
                            color = AppColors.TextSecondary.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppRoundedCornerShape(AppRadius.md),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.TextPrimary,
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
                            Text("粘贴", fontSize = AppType.Caption, color = AppColors.TextPrimary)
                        }
                    }
                )
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = "开始导入",
                onClick = { if (urlText.isNotBlank()) onConfirm(urlText.trim()) },
                enabled = urlText.isNotBlank(),
                tintedColor = AppColors.TextPrimary,
                contentColor = AppColors.OnAccent
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = "取消",
                onClick = onDismiss,
                contentColor = AppColors.TextPrimary
            )
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

    val dialogShape = AppRoundedCornerShape(AppRadius.xl)
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
                    .padding(AppSpace.md)
            ) {
                // 顶部标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "调试: ${source.bookSourceName}",
                            fontSize = AppType.Body,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = source.bookSourceUrl,
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    LiquidGlassIconButton(
                        imageVector = AppIcons.X,
                        contentDescription = "关闭",
                        onClick = onDismiss,
                        size = 36.dp,
                        iconSize = 18.dp,
                        contentColor = AppColors.TextSecondary
                    )
                }

                Spacer(Modifier.height(AppSpace.sm))

                // 输入测试关键词与开始/停止按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testKeyword,
                        onValueChange = { testKeyword = it },
                        label = { Text("测试关键词或书籍/目录URL", fontSize = AppType.Caption) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = AppRoundedCornerShape(AppRadius.md),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.TextPrimary,
                            unfocusedBorderColor = if (isLiquidGlass) Color.White.copy(alpha = 0.2f) else AppColors.Divider,
                            focusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                            unfocusedContainerColor = if (isLiquidGlass) Color.White.copy(alpha = 0.03f) else Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(AppSpace.sm))
                    if (isDebugging) {
                        LiquidGlassButton(
                            onClick = onStopDebug,
                            tintedColor = SourceDangerColor,
                            contentColor = Color.White
                        ) {
                            Icon(AppIcons.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpace.xs))
                            Text("停止", color = Color.White, fontSize = AppType.BodySmall)
                        }
                    } else {
                        LiquidGlassButton(
                            onClick = { onStartDebug(testKeyword) },
                            tintedColor = AppColors.TextPrimary,
                            contentColor = AppColors.OnAccent
                        ) {
                            Icon(AppIcons.Play, contentDescription = null, tint = AppColors.OnAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpace.xs))
                            Text("开始", color = AppColors.OnAccent, fontSize = AppType.BodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(AppSpace.sm))

                // 终端日志视窗（等宽字体 + 应用语义色）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(AppRoundedCornerShape(AppRadius.md))
                        .background(AppColors.BgGray)
                        .border(1.dp, AppColors.Divider, AppRoundedCornerShape(AppRadius.md))
                        .padding(AppSpace.sm)
                ) {
                    if (debugLogs.isEmpty()) {
                        Text(
                            text = "输入搜索词（默认「完美世界」），点击开始调试即可观察全链路请求和解析过程。",
                            color = AppColors.TextSecondary,
                            fontSize = AppType.Caption,
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
                                    log.contains("Error") || log.contains("失败") || log.contains("异常") -> SourceDangerColor
                                    log.contains("⇒") || log.contains("︾") || log.contains("︽") -> AppColors.TextPrimary
                                    log.contains("完成") || log.contains("成功") -> AppColors.TextPrimary
                                    else -> AppColors.TextPrimary
                                }
                                Text(
                                    text = log,
                                    fontSize = AppType.Caption,
                                    fontFamily = FontFamily.Monospace,
                                    color = logColor,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(AppSpace.sm))

                // 底部控制：复制日志、清空日志
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isDebugging) "正在测试抓取解析..." else "日志条数: ${debugLogs.size}",
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidGlassButton(
                            onClick = {
                                val fullLog = debugLogs.joinToString("\n")
                                clipboardManager.setText(AnnotatedString(fullLog))
                            },
                            enabled = debugLogs.isNotEmpty(),
                            compact = true,
                            contentColor = AppColors.TextPrimary
                        ) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(AppSpace.xs))
                            Text("复制全部日志", color = AppColors.TextPrimary, fontSize = AppType.Caption)
                        }
                        LiquidGlassButton(
                            onClick = onClearLogs,
                            enabled = debugLogs.isNotEmpty(),
                            compact = true,
                            contentColor = AppColors.TextSecondary
                        ) {
                            Text("清空", color = AppColors.TextSecondary, fontSize = AppType.Caption)
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

    val dialogShape = AppRoundedCornerShape(AppRadius.xl)
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
                        .padding(horizontal = AppSpace.md, vertical = AppSpace.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "编辑书源",
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiquidGlassTextButton(
                            text = "取消",
                            onClick = onDismiss,
                            contentColor = AppColors.TextPrimary
                        )
                        Spacer(Modifier.width(AppSpace.xs))
                        LiquidGlassTextButton(
                            text = "保存",
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
                            tintedColor = AppColors.TextPrimary,
                            contentColor = AppColors.OnAccent
                        )
                    }
                }

                // 模式切换 Tab
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = AppColors.BgGray.copy(alpha = if (isLiquidGlass) 0.05f else 0.4f)
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
                            .padding(AppSpace.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpace.md)
                    ) {
                        Text("基础信息", fontWeight = FontWeight.Bold, fontSize = AppType.Body, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("书源名称") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("书源 URL") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = group,
                                onValueChange = { group = it },
                                label = { Text("书源分组 (逗号分隔)") },
                                modifier = Modifier.weight(1f),
                                shape = AppRoundedCornerShape(AppRadius.md)
                            )
                            Spacer(Modifier.width(AppSpace.sm))
                            OutlinedTextField(
                                value = weight,
                                onValueChange = { weight = it },
                                label = { Text("排序权重") },
                                modifier = Modifier.width(100.dp),
                                shape = AppRoundedCornerShape(AppRadius.md)
                            )
                        }

                        // 开关项（使用 LiquidGlassSwitch 自动适配三大主题）
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("启用书源", fontSize = AppType.BodySmall, color = AppColors.TextPrimary)
                            LiquidGlassSwitch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("在「发现」页呈现 (enabledExplore)", fontSize = AppType.BodySmall, color = AppColors.TextPrimary)
                            LiquidGlassSwitch(checked = enabledExplore, onCheckedChange = { enabledExplore = it })
                        }

                        Spacer(Modifier.height(AppSpace.sm))
                        Text("搜索规则", fontWeight = FontWeight.Bold, fontSize = AppType.Body, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = searchUrl,
                            onValueChange = { searchUrl = it },
                            label = { Text("搜索地址 (searchUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleSearchBookList,
                            onValueChange = { ruleSearchBookList = it },
                            label = { Text("列表规则 (bookList)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleSearchName,
                            onValueChange = { ruleSearchName = it },
                            label = { Text("书名规则 (name)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleSearchAuthor,
                            onValueChange = { ruleSearchAuthor = it },
                            label = { Text("作者规则 (author)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleSearchNoteUrl,
                            onValueChange = { ruleSearchNoteUrl = it },
                            label = { Text("详情页地址 (bookUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )

                        Spacer(Modifier.height(AppSpace.sm))
                        Text("详情规则", fontWeight = FontWeight.Bold, fontSize = AppType.Body, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = ruleInfoTocUrl,
                            onValueChange = { ruleInfoTocUrl = it },
                            label = { Text("目录页地址 (tocUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleInfoIntro,
                            onValueChange = { ruleInfoIntro = it },
                            label = { Text("简介规则 (intro)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )

                        Spacer(Modifier.height(AppSpace.sm))
                        Text("目录与正文规则", fontWeight = FontWeight.Bold, fontSize = AppType.Body, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = ruleTocChapterList,
                            onValueChange = { ruleTocChapterList = it },
                            label = { Text("目录列表规则 (chapterList)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleTocChapterName,
                            onValueChange = { ruleTocChapterName = it },
                            label = { Text("章节名规则 (chapterName)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleTocChapterUrl,
                            onValueChange = { ruleTocChapterUrl = it },
                            label = { Text("章节地址规则 (chapterUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                        OutlinedTextField(
                            value = ruleContentBody,
                            onValueChange = { ruleContentBody = it },
                            label = { Text("正文内容规则 (content)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )

                        Spacer(Modifier.height(AppSpace.sm))
                        Text("发现规则", fontWeight = FontWeight.Bold, fontSize = AppType.Body, color = AppColors.TextPrimary)
                        OutlinedTextField(
                            value = exploreUrl,
                            onValueChange = { exploreUrl = it },
                            label = { Text("发现地址规则 (exploreUrl)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            shape = AppRoundedCornerShape(AppRadius.md)
                        )
                    }
                } else {
                    // JSON 源码编辑视图
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(AppSpace.md)
                    ) {
                        jsonError?.let {
                            Text(it, color = SourceDangerColor, fontSize = AppType.Caption, modifier = Modifier.padding(bottom = AppSpace.xs))
                        }
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = {
                                jsonText = it
                                jsonError = null
                            },
                            modifier = Modifier.fillMaxSize(),
                            shape = AppRoundedCornerShape(AppRadius.md),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = AppType.Caption
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassDropdownMenuItem(
    text: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (selected) AppColors.TextPrimary.copy(alpha = 0.14f) else Color.Transparent
    val textColor = if (selected) AppColors.TextPrimary else AppColors.TextPrimary
    val iconTint = if (selected) AppColors.TextPrimary else AppColors.TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.xs, vertical = 2.dp)
            .clip(AppRoundedCornerShape(AppRadius.sm))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(AppSpace.sm))
        }
        Text(
            text = text,
            fontSize = AppType.BodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = AppColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
// ─── 导入书源确认弹窗（分组选择与覆盖提示） ────────────────────────
@Composable
private fun ImportSourceConfirmDialog(
    sources: List<BookSource>,
    allSources: List<BookSource>,
    availableGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (group: String?, overrideExisting: Boolean) -> Unit
) {
    val existingCount = remember(sources, allSources) {
        val existingUrls = allSources.map { it.bookSourceUrl }.toSet()
        sources.count { it.bookSourceUrl in existingUrls }
    }
    var overrideExisting by remember { mutableStateOf(true) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var customGroupText by remember { mutableStateOf("") }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "添加书源",
                fontWeight = FontWeight.Bold,
                fontSize = AppType.Section,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpace.md)
            ) {
                Text(
                    text = "共解析到 ${sources.size} 个书源",
                    fontSize = AppType.BodySmall,
                    color = AppColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                if (existingCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppRoundedCornerShape(AppRadius.md))
                            .background(AppColors.TextPrimary.copy(alpha = 0.10f))
                            .border(1.dp, AppColors.TextPrimary.copy(alpha = 0.25f), AppRoundedCornerShape(AppRadius.md))
                    ) {
                        Column(
                            modifier = Modifier.padding(AppSpace.md),
                            verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
                        ) {
                            Text(
                                text = "⚠️ 检测到其中 $existingCount 个书源已存在于本地",
                                fontSize = AppType.BodySmall,
                                color = AppColors.TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "覆盖更新已存在书源",
                                    fontSize = AppType.BodySmall,
                                    color = AppColors.TextPrimary
                                )
                                LiquidGlassSwitch(
                                    checked = overrideExisting,
                                    onCheckedChange = { overrideExisting = it }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "归属分组：",
                    fontSize = AppType.BodySmall,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextSecondary
                )

                if (availableGroups.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)
                    ) {
                        availableGroups.take(8).forEach { grp ->
                            val isSelected = selectedGroup == grp
                            Box(
                                modifier = Modifier
                                    .clip(AppRoundedCornerShape(AppRadius.md))
                                    .background(if (isSelected) AppColors.TextPrimary.copy(alpha = 0.18f) else AppColors.CardBg)
                                    .border(
                                        1.dp,
                                        if (isSelected) AppColors.TextPrimary else AppColors.Divider.copy(alpha = 0.5f),
                                        AppRoundedCornerShape(AppRadius.md)
                                    )
                                    .clickable {
                                        if (selectedGroup == grp) {
                                            selectedGroup = null
                                        } else {
                                            selectedGroup = grp
                                            customGroupText = grp
                                        }
                                    }
                            ) {
                                Text(
                                    text = grp,
                                    fontSize = AppType.Caption,
                                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextPrimary,
                                    modifier = Modifier.padding(horizontal = AppSpace.sm, vertical = AppSpace.xs)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = customGroupText,
                    onValueChange = {
                        customGroupText = it
                        selectedGroup = it.ifBlank { null }
                    },
                    placeholder = {
                        Text("自定义新分组（留空则保持书源自带分组）", fontSize = AppType.Caption, color = AppColors.TextSecondary.copy(alpha = 0.6f))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppRoundedCornerShape(AppRadius.md),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            LiquidGlassTextButton(
                text = "立即导入",
                onClick = {
                    val groupToApply = customGroupText.trim().ifBlank { selectedGroup }
                    onConfirm(groupToApply, overrideExisting)
                },
                tintedColor = AppColors.TextPrimary,
                contentColor = AppColors.OnAccent
            )
        },
        dismissButton = {
            LiquidGlassTextButton(
                text = "取消",
                onClick = onDismiss,
                contentColor = AppColors.TextPrimary
            )
        }
    )
}
