package com.huangder.lumibooks.ui.replace

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import io.legado.app.data.entities.ReplaceRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceRulesScreen(
    viewModel: ReplaceRulesViewModel = viewModel(),
    initialBookName: String? = null,
    initialPattern: String? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by viewModel.filteredRules.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val isLiquidGlass = LocalAppTheme.current == "liquid_glass" && !LocalEInkMode.current

    var isSearching by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    var editingRule by remember { mutableStateOf<ReplaceRule?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var ruleToDelete by remember { mutableStateOf<ReplaceRule?>(null) }

    // 导出文件 Launcher
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val payload = pendingExportJson
        pendingExportJson = null
        if (uri != null && payload != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "规则已成功导出", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导出失败: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 导入文件 Launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val content = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull()
                if (!content.isNullOrBlank()) {
                    val result = viewModel.importRulesJson(content)
                    withContext(Dispatchers.Main) {
                        result.onSuccess {
                            Toast.makeText(context, "成功导入 $it 条规则", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // 如果外部传入了初始 pattern 或 bookName，直接打开新建弹窗
    LaunchedEffect(initialPattern, initialBookName) {
        if (!initialPattern.isNullOrBlank() || !initialBookName.isNullOrBlank()) {
            editingRule = ReplaceRule(
                name = initialBookName?.let { "$it-净化" } ?: "",
                pattern = initialPattern.orEmpty(),
                replacement = "",
                scope = initialBookName,
                isRegex = false,
                scopeContent = true,
                scopeTitle = false,
                isEnabled = true
            )
            isCreating = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("搜索规则名称、内容或范围", fontSize = 14.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                    } else {
                        Text(
                            stringResource(R.string.replace_rules_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            viewModel.searchQuery.value = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearching = !isSearching
                        if (!isSearching) viewModel.searchQuery.value = ""
                    }) {
                        Icon(
                            if (isSearching) Icons.Outlined.Close else Icons.Outlined.Search,
                            contentDescription = "搜索"
                        )
                    }
                    IconButton(onClick = {
                        editingRule = ReplaceRule(
                            name = "",
                            pattern = "",
                            replacement = "",
                            scope = initialBookName,
                            isRegex = false,
                            scopeContent = true,
                            scopeTitle = false,
                            isEnabled = true
                        )
                        isCreating = true
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.replace_rules_add))
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.replace_rules_clipboard_import)) },
                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                onClick = {
                                    showMoreMenu = false
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = cm.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (clip.isNullOrBlank()) {
                                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                    } else {
                                        scope.launch {
                                            val res = viewModel.importRulesJson(clip)
                                            res.onSuccess {
                                                Toast.makeText(context, "成功从剪贴板导入 $it 条规则", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                Toast.makeText(context, "导入失败: ${it.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.replace_rules_file_import)) },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                                onClick = {
                                    showMoreMenu = false
                                    importLauncher.launch(arrayOf("application/json", "text/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.replace_rules_export)) },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                                onClick = {
                                    showMoreMenu = false
                                    scope.launch {
                                        pendingExportJson = viewModel.exportRulesJson()
                                        exportLauncher.launch("lumi-replace-rules.json")
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.replace_rules_load_preset)) },
                                leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null) },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.loadPresets()
                                    Toast.makeText(context, "已加载内置推荐规则", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = AppColors.TextSecondary.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.replace_rules_empty_hint),
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                editingRule = ReplaceRule(
                                    name = "",
                                    pattern = "",
                                    replacement = "",
                                    scope = initialBookName,
                                    isRegex = false,
                                    scopeContent = true,
                                    scopeTitle = false,
                                    isEnabled = true
                                )
                                isCreating = true
                            }
                        ) {
                            Text(stringResource(R.string.replace_rules_add))
                        }
                        OutlinedButton(onClick = { viewModel.loadPresets() }) {
                            Text(stringResource(R.string.replace_rules_load_preset))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rules, key = { it.id }) { rule ->
                        ReplaceRuleCard(
                            rule = rule,
                            isLiquidGlass = isLiquidGlass,
                            onToggle = { viewModel.toggleRule(rule) },
                            onClick = {
                                editingRule = rule
                                isCreating = false
                            },
                            onDelete = { ruleToDelete = rule }
                        )
                    }
                }
            }
        }
    }

    // 编辑/新建 Dialog
    editingRule?.let { rule ->
        ReplaceRuleEditDialog(
            initialRule = rule,
            isNew = isCreating,
            isLiquidGlass = isLiquidGlass,
            onDismiss = {
                editingRule = null
                isCreating = false
            },
            onSave = { saved ->
                viewModel.saveRule(saved)
                editingRule = null
                isCreating = false
                Toast.makeText(context, context.getString(R.string.add_purify_rule_success), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 删除确认 Dialog
    ruleToDelete?.let { rule ->
        LiquidGlassAlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = {
                Text(
                    text = "确认删除规则",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = AppColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = "确定要删除净化规则「${rule.name.ifBlank { rule.pattern }}」吗？",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRule(rule)
                        ruleToDelete = null
                        Toast.makeText(context, "规则已删除", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 10.dp)
                ) {
                    Text("删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ReplaceRuleCard(
    rule: ReplaceRule,
    isLiquidGlass: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val cardShape = RoundedCornerShape(if (isLiquidGlass) 20.dp else 12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text(
                        text = rule.name.ifBlank { "未命名规则" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!rule.group.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = rule.group.orEmpty(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() }
                )
            }

            Spacer(Modifier.height(8.dp))

            // 规则模式与作用范围 Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (rule.isRegex) Color(0xFFE0F2FE) else Color(0xFFF3F4F6)
                ) {
                    Text(
                        text = if (rule.isRegex) "正则" else "纯文本",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (rule.isRegex) Color(0xFF0369A1) else Color(0xFF4B5563),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                val scopeText = if (rule.scope.isNullOrBlank()) "全局生效" else "限定: ${rule.scope}"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (rule.scope.isNullOrBlank()) Color(0xFFECFDF5) else Color(0xFFFEF3C7)
                ) {
                    Text(
                        text = scopeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (rule.scope.isNullOrBlank()) Color(0xFF047857) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (rule.scopeContent) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFEDE9FE)
                    ) {
                        Text(
                            text = "正文",
                            fontSize = 11.sp,
                            color = Color(0xFF6D28D9),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (rule.scopeTitle) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFCE7F3)
                    ) {
                        Text(
                            text = "标题",
                            fontSize = 11.sp,
                            color = Color(0xFFBE185D),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // 匹配表达式与替换内容
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        text = rule.pattern,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (rule.replacement.isEmpty()) "➔ [直接清除]" else "➔ 替换为: ${rule.replacement}",
                        fontSize = 12.sp,
                        color = if (rule.replacement.isEmpty()) Color(0xFFE53935) else AppColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 底部操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ReplaceRuleEditDialog(
    initialRule: ReplaceRule,
    isNew: Boolean,
    isLiquidGlass: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReplaceRule) -> Unit
) {
    var name by remember { mutableStateOf(initialRule.name) }
    var pattern by remember { mutableStateOf(initialRule.pattern) }
    var replacement by remember { mutableStateOf(initialRule.replacement) }
    var scope by remember { mutableStateOf(initialRule.scope.orEmpty()) }
    var isRegex by remember { mutableStateOf(initialRule.isRegex) }
    var scopeContent by remember { mutableStateOf(initialRule.scopeContent) }
    var scopeTitle by remember { mutableStateOf(initialRule.scopeTitle) }
    var isEnabled by remember { mutableStateOf(initialRule.isEnabled) }

    var errorText by remember { mutableStateOf<String?>(null) }

    LiquidGlassAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) "新建净化替换规则" else "编辑净化替换规则",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it
                        errorText = null
                    },
                    label = { Text("匹配内容 / 正则表达式 *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )

                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("替换为 (留空表示直接清除)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = scope,
                    onValueChange = { scope = it },
                    label = { Text("作用范围 (书名或域名，留空全局生效)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("使用正则表达式", fontSize = 14.sp, color = AppColors.TextPrimary)
                    Switch(checked = isRegex, onCheckedChange = { isRegex = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("作用于正文", fontSize = 14.sp, color = AppColors.TextPrimary)
                    Switch(checked = scopeContent, onCheckedChange = { scopeContent = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("作用于标题", fontSize = 14.sp, color = AppColors.TextPrimary)
                    Switch(checked = scopeTitle, onCheckedChange = { scopeTitle = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用此规则", fontSize = 14.sp, color = AppColors.TextPrimary)
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pattern.isBlank()) {
                        errorText = "匹配内容不能为空"
                        return@Button
                    }
                    val targetRule = initialRule.copy(
                        name = name.trim().ifBlank { pattern.trim().take(16) },
                        pattern = pattern,
                        replacement = replacement,
                        scope = scope.trim().ifBlank { null },
                        isRegex = isRegex,
                        scopeContent = scopeContent,
                        scopeTitle = scopeTitle,
                        isEnabled = isEnabled
                    )
                    if (!targetRule.isValid()) {
                        errorText = "正则表达式语法错误，请检查括号或特殊字符"
                        return@Button
                    }
                    onSave(targetRule)
                },
                shape = RoundedCornerShape(if (isLiquidGlass) 16.dp else 10.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}
