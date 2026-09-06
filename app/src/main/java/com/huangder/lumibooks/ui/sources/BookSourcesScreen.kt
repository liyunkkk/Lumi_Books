package com.huangder.lumibooks.ui.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import io.legado.app.data.entities.BookSource

@Composable
fun BookSourcesScreen(
    onNavigateBack: () -> Unit,
    viewModel: BookSourcesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val sources by viewModel.sources.collectAsState()
    val importing by viewModel.importing.collectAsState()
    val result by viewModel.importResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { input ->
                val json = input.readBytes().decodeToString()
                viewModel.importFromJson(json)
            }
        }
    }

    LaunchedEffect(result) {
        result?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeResult()
        }
    }

    Scaffold(
        containerColor = AppColors.WindowBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("书源管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.TextSecondary
                        )
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { filePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "导入书源")
                        }
                    }
                    IconButton(onClick = { viewModel.deleteAll() }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "清空书源")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无书源",
                        fontSize = AppType.Body,
                        color = AppColors.TextSecondary
                    )
                    Spacer(Modifier.height(AppSpace.sm))
                    Text(
                        "点击右上角 + 选择书源 JSON 文件导入",
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppSpace.md,
                    vertical = AppSpace.sm
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpace.xs)
            ) {
                items(sources, key = { it.bookSourceUrl }) { source ->
                    SourceItem(
                        source = source,
                        onToggle = { viewModel.setEnabled(source, it) },
                        onDelete = { viewModel.delete(source) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: BookSource,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius_md))
            .background(AppColors.CardBg)
            .padding(horizontal = AppSpace.md, vertical = AppSpace.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                source.bookSourceName.ifBlank { source.bookSourceUrl },
                fontSize = AppType.Body,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                source.bookSourceUrl,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
        }
        TextButton(onClick = onDelete) {
            Text("删除", color = AppColors.TextSecondary)
        }
        Switch(
            checked = source.enabled,
            onCheckedChange = onToggle
        )
    }
}

private val AppRadius_md = 14.dp
