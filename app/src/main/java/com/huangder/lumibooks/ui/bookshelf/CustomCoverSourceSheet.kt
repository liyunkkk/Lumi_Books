package com.huangder.lumibooks.ui.bookshelf

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.discover.BookDiscoveryRepository
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.util.FileUtils
import com.huangder.lumibooks.widget.widgetDataEntryPoint
import io.legado.app.data.appDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CoverSourceStep {
    OPTIONS,
    SOURCES_SEARCH
}

private data class SourceCoverItem(
    val url: String,
    val sourceName: String,
    val author: String
)

/**
 * 「自定义封面」来源选择容器：选择图片 / 网络搜索 / 搜索其它书源封面
 * 液态玻璃主题下为悬浮玻璃面板，普通主题下为 ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCoverSourceSheet(
    book: Book,
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onWebSearch: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    var currentStep by remember { mutableStateOf(CoverSourceStep.OPTIONS) }

    val sheetContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cover_source_step_anim"
        ) { step ->
            when (step) {
                CoverSourceStep.OPTIONS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 24.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cover_source_title),
                            fontSize = AppType.Section,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = book.title,
                            fontSize = AppType.Caption,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = AppSpace.xs)
                        )
                        Spacer(Modifier.height(AppSpace.lg))
                        CoverSourceOptionRow(
                            icon = Icons.Outlined.AutoStories,
                            title = stringResource(R.string.cover_source_sources_search),
                            description = stringResource(R.string.cover_source_sources_search_desc),
                            onClick = { currentStep = CoverSourceStep.SOURCES_SEARCH }
                        )
                        Spacer(Modifier.height(AppSpace.sm + AppSpace.xs))
                        CoverSourceOptionRow(
                            icon = Icons.Outlined.Image,
                            title = stringResource(R.string.cover_source_pick_image),
                            description = stringResource(R.string.cover_source_pick_image_desc),
                            onClick = onPickImage
                        )
                        Spacer(Modifier.height(AppSpace.sm + AppSpace.xs))
                        CoverSourceOptionRow(
                            icon = Icons.Outlined.Public,
                            title = stringResource(R.string.cover_source_web_search),
                            description = stringResource(R.string.cover_source_web_search_desc),
                            onClick = onWebSearch
                        )
                    }
                }
                CoverSourceStep.SOURCES_SEARCH -> {
                    SourceCoversPickerView(
                        book = book,
                        onBack = { currentStep = CoverSourceStep.OPTIONS },
                        onCoverApplied = onDismiss
                    )
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
            sheetContent()
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
            sheetContent()
        }
    }
}

@Composable
private fun SourceCoversPickerView(
    book: Book,
    onBack: () -> Unit,
    onCoverApplied: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var foundCovers by remember { mutableStateOf<List<SourceCoverItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var applyingCoverUrl by remember { mutableStateOf<String?>(null) }
    var searchTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(book.title, searchTrigger) {
        isSearching = true
        foundCovers = emptyList()
        val sources = withContext(Dispatchers.IO) {
            runCatching { appDb.bookSourceDao.allEnabled() }.getOrDefault(emptyList())
        }
        if (sources.isNotEmpty()) {
            BookDiscoveryRepository.searchStreaming(
                keyword = book.title,
                sources = sources,
                exactMatch = false
            ) { batch ->
                val items = batch.mapNotNull { sb ->
                    val cUrl = sb.coverUrl?.trim()
                    if (!cUrl.isNullOrBlank()) {
                        SourceCoverItem(
                            url = cUrl,
                            sourceName = sb.originName.ifBlank { "在线书源" },
                            author = sb.author
                        )
                    } else null
                }
                foundCovers = (foundCovers + items).distinctBy { it.url }
            }
        }
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 顶部标题栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = AppColors.TextPrimary
                )
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "书源封面库",
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = "正在聚合多源《${book.title}》高画质封面",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AppColors.Accent
                )
            } else {
                IconButton(
                    onClick = { searchTrigger++ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "重新检索",
                        tint = AppColors.TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 封面网格展示区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 380.dp),
            contentAlignment = Alignment.Center
        ) {
            if (foundCovers.isEmpty()) {
                if (isSearching) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AppColors.Accent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "正在并发检索各大在线书源...",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "未在已启用的书源中找到匹配的封面",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "可尝试使用“网络搜索”获取高清封面",
                            fontSize = 11.sp,
                            color = AppColors.Accent
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(foundCovers, key = { it.url }) { item ->
                        val isCurrentApplying = applyingCoverUrl == item.url
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.BgGray)
                                .border(1.dp, AppColors.Divider.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable(enabled = applyingCoverUrl == null) {
                                    applyingCoverUrl = item.url
                                    coroutineScope.launch {
                                        try {
                                            val loader = ImageLoader(context)
                                            val request = ImageRequest.Builder(context)
                                                .data(item.url)
                                                .allowHardware(false)
                                                .build()
                                            val drawable = (loader.execute(request) as? SuccessResult)?.drawable
                                            val bitmap = (drawable as? BitmapDrawable)?.bitmap
                                            if (bitmap != null) {
                                                val saved = withContext(Dispatchers.IO) {
                                                    FileUtils.saveCoverBitmap(context, bitmap, book.id)
                                                }
                                                if (saved != null) {
                                                    withContext(Dispatchers.IO) {
                                                        FileUtils.deleteOtherCustomCovers(context, book.id, saved)
                                                        val repo = context.widgetDataEntryPoint().bookRepository()
                                                        repo.updateBookMetadata(book.copy(coverPath = saved))
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "已成功应用书源封面", Toast.LENGTH_SHORT).show()
                                                        onCoverApplied()
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(context, "封面保存失败", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "图片下载失败", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "设置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } finally {
                                            applyingCoverUrl = null
                                        }
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = item.url,
                                contentDescription = item.sourceName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // 底部书源名小标签
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                        )
                                    )
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = item.sourceName,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrentApplying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverSourceOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpace.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppColors.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AppColors.Accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = AppSpace.md)
        ) {
            Text(
                text = title,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = description,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
