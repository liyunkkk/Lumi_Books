package com.huangder.lumibooks.ui.home

import com.huangder.lumibooks.ui.icons.AppIcons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.huangder.lumibooks.ui.components.LocalShowCoverProgress
import io.legado.app.data.appDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.huangder.lumibooks.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.huangder.lumibooks.domain.model.Book
import com.huangder.lumibooks.domain.model.BookDeleteMode
import com.huangder.lumibooks.data.sync.BookDownloadState
import com.huangder.lumibooks.ui.animation.OverscrollBounce
import com.huangder.lumibooks.ui.animation.OverscrollBounceState
import com.huangder.lumibooks.ui.animation.cardPressEffect
import com.huangder.lumibooks.ui.components.StatusGradientOverlay
import com.huangder.lumibooks.ui.components.BookCoverProgressOverlay
import com.huangder.lumibooks.ui.components.FinishedReadingIndicator
import com.huangder.lumibooks.ui.components.CloudAwareBookDeleteDialog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.huangder.lumibooks.ui.components.LiquidGlassMenuItem
import com.huangder.lumibooks.ui.components.LiquidGlassMenuSpec
import com.huangder.lumibooks.ui.components.LocalLiquidGlassMenuHost
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.KaiTi
import com.huangder.lumibooks.ui.theme.cardOutline
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import com.huangder.lumibooks.ui.theme.LocalEInkMode
import com.huangder.lumibooks.ui.theme.LocalUseMaterial3Theme
import com.huangder.lumibooks.ui.theme.SansSerif
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.animation.PageEntranceItem
import com.huangder.lumibooks.ui.animation.BookCoverTitleColor
import com.huangder.lumibooks.ui.animation.BookCoverTitleStyle
import com.huangder.lumibooks.ui.animation.bookCoverMemoryCacheKey
import com.huangder.lumibooks.ui.animation.bookCoverTransitionAnchor
import com.huangder.lumibooks.ui.layout.currentAdaptiveWindowInfo
import com.huangder.lumibooks.util.TimeUtils
import com.huangder.lumibooks.util.ReaderBackgroundBlurTransformation

@Composable
fun HomeScreen(
    playEntranceAnimation: Boolean = false,
    onNavigateToReader: (book: Book, sourceBounds: Rect?) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onImportClick: () -> Unit,
    showImportButton: Boolean = true,
    onTabBarVisibleChange: (Boolean) -> Unit = {},
    showReadingGoalSheet: Boolean = false,
    onReadingGoalSheetVisibleChange: (Boolean) -> Unit = {},
    renderReadingGoalSheet: Boolean = true,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val isMaterial3 = LocalUseMaterial3Theme.current
    val useWideLayout = currentAdaptiveWindowInfo().isMediumWidthOrLarger
    val topBlurBackdrop = rememberLayerBackdrop()
    val statusBarTopPadding = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val bottomContentPadding = navBarBottom + (if (isMaterial3) 84.dp else 76.dp)
    val context = LocalContext.current
    var localShowGoalSheet by remember { mutableStateOf(false) }
    val showGoalSheet = if (renderReadingGoalSheet) localShowGoalSheet else showReadingGoalSheet
    val setShowGoalSheet: (Boolean) -> Unit = { visible ->
        if (renderReadingGoalSheet) {
            localShowGoalSheet = visible
        } else {
            onReadingGoalSheetVisibleChange(visible)
        }
    }
    val activeReadingBooks = remember(
        uiState.books,
        uiState.bookFolderLinks,
        uiState.hiddenBookIds,
        uiState.hiddenFromNowReadingBookIds,
        uiState.hiddenFolderIds,
        uiState.hiddenFromNowReadingFolderIds
    ) {
        val hiddenFolderIdSet = uiState.hiddenFolderIds + uiState.hiddenFromNowReadingFolderIds
        val folderIdsByBookId = uiState.bookFolderLinks.groupBy({ it.bookId }, { it.folderId })
        uiState.books.filter { book ->
            if (book.id in uiState.hiddenBookIds) return@filter false
            if (book.id in uiState.hiddenFromNowReadingBookIds) return@filter false
            val folderIds = folderIdsByBookId[book.id].orEmpty()
            if (folderIds.any { it in hiddenFolderIdSet }) return@filter false
            true
        }
    }
    val booksByLastRead = remember(activeReadingBooks) {
        activeReadingBooks.sortedByDescending { it.lastReadTime }
    }
    val lastReadBook = booksByLastRead.firstOrNull()

    // 顶部下拉「松手继续阅读」：只读取下拉位移，判定与回弹都由 OverscrollBounce 维持原样。
    val bounceState = remember { OverscrollBounceState() }
    val pullToResumeThresholdPx = with(LocalDensity.current) {
        PULL_TO_RESUME_THRESHOLD_DP.dp.toPx()
    }
    val latestNavigateToReader = rememberUpdatedState(onNavigateToReader)
    val latestLastReadBook = rememberUpdatedState(lastReadBook)
    val onTopPullRelease: (Float) -> Unit = { releasedOffsetPx ->
        if (shouldOpenRecentBookOnRelease(releasedOffsetPx, pullToResumeThresholdPx)) {
            latestLastReadBook.value?.let { book ->
                latestNavigateToReader.value(book, null)
            }
        }
    }
    val recentBooks = remember(booksByLastRead, lastReadBook) {
        if (lastReadBook != null) {
            booksByLastRead.filter { it.id != lastReadBook.id }
        } else {
            booksByLastRead
        }
    }
    CompositionLocalProvider(LocalShowCoverProgress provides uiState.showCoverProgress) {
        if (uiState.homeScreenMode == com.huangder.lumibooks.data.local.DataStoreManager.HOME_SCREEN_MODE_CARD_FLOW) {
            CardFlowHomeScreen(
                books = activeReadingBooks,
                todayReadingTime = uiState.todayReadingTime,
                dailyGoal = uiState.dailyGoal,
                streakDays = uiState.streakDays,
                avatarUri = uiState.avatarUri,
                statusBarTopPadding = statusBarTopPadding,
                bottomContentPadding = bottomContentPadding,
                onNavigateToReader = onNavigateToReader,
                onNavigateToStatistics = onNavigateToStatistics,
                onNavigateToBookshelf = onNavigateToBookshelf,
                onImportClick = onImportClick
            )
        } else {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .semantics { testTagsAsResourceId = true }
            .testTag(HOME_SCREEN_TAG)
    ) {
        val screenHeight = maxHeight
        // 计算从顶栏到底栏之间的有效可用高度，用于三大组件对齐
        val availableContentHeight = screenHeight - statusBarTopPadding - bottomContentPadding - 54.dp
        val dynamicSpacing = if (availableContentHeight > 480.dp) {
            ((availableContentHeight - 480.dp) / 3).coerceIn(8.dp, 20.dp)
        } else {
            8.dp
        }
        OverscrollBounce(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isLiquidGlass) Modifier.layerBackdrop(topBlurBackdrop) else Modifier
                ),
            state = bounceState,
            onTopPullRelease = onTopPullRelease
        ) {
            LazyColumn(
                modifier = Modifier
                    .then(if (useWideLayout) Modifier.widthIn(max = 840.dp) else Modifier)
                    .fillMaxWidth()
                    .then(if (useWideLayout) Modifier.align(Alignment.TopCenter) else Modifier)
            ) {
                item(key = "header") {
                    Spacer(Modifier.height(statusBarTopPadding + AppSpace.sm))
                    PageEntranceItem(play = playEntranceAnimation, index = 0) {
                        HomeHeader(onNavigateToStatistics = onNavigateToStatistics)
                    }
                    Spacer(Modifier.height(dynamicSpacing))
                }
                if (uiState.books.isEmpty()) {
                    item(key = "empty_library") {
                        PageEntranceItem(play = playEntranceAnimation, index = 1) {
                            ImportHint(onImportClick = onImportClick)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
                if (lastReadBook != null) {
                    item(key = "continue_reading") {
                        PageEntranceItem(play = playEntranceAnimation, index = 2) {
                            ContinueReadingCard(
                                book = lastReadBook,
                                downloadState = uiState.downloadStates[lastReadBook.id],
                                onClick = { bounds -> onNavigateToReader(lastReadBook, bounds) },
                                onToggleFavorite = { viewModel.updateBook(lastReadBook.copy(isFavorite = !lastReadBook.isFavorite)) },
                                onDelete = { mode -> viewModel.deleteBook(lastReadBook, mode) },
                                modifier = Modifier.padding(horizontal = AppSpace.lg)
                            )
                        }
                        Spacer(Modifier.height(dynamicSpacing))
                    }
                }
                if (recentBooks.isNotEmpty()) {
                    item(key = "recently_read") {
                        PageEntranceItem(play = playEntranceAnimation, index = 3) {
                            Column {
                                SectionHeader(stringResource(R.string.section_recently_read))
                                Spacer(Modifier.height(4.dp))
                                BooksReadGrid(
                                    books = recentBooks,
                                    downloadStates = uiState.downloadStates,
                                    useWideLayout = useWideLayout,
                                    onBookClick = { book, bounds ->
                                        onNavigateToReader(book, bounds)
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(dynamicSpacing))
                    }
                }
                item(key = "reading_goal") {
                    PageEntranceItem(play = playEntranceAnimation, index = 4) {
                        ReadingGoalCard(
                            readingTime = uiState.todayReadingTime,
                            dailyGoal = uiState.dailyGoal,
                            weeklyData = uiState.weeklyData,
                            onCardClick = { setShowGoalSheet(true) }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "bottom_spacing") {
                    Spacer(Modifier.height(bottomContentPadding))
                }
            }
        } // OverscrollBounce 结束

        StatusGradientOverlay(backdrop = null)

        // 提示绘制在顶部渐隐之上，避免文字被窗口色渐变冲淡；无书时整个手势都不可用。
        if (lastReadBook != null) {
            HomePullToResumeHint(
                bounceState = bounceState,
                statusBarTopPadding = statusBarTopPadding
            )
        }

        if (renderReadingGoalSheet) {
            ReadingGoalSheet(
                visible = showGoalSheet,
                todayReadingTime = uiState.todayReadingTime,
                dailyGoal = uiState.dailyGoal,
                currentBook = lastReadBook,
                weeklyData = uiState.weeklyData,
                streakDays = uiState.streakDays,
                onDismiss = { setShowGoalSheet(false) },
                onSaveGoal = { minutes -> viewModel.saveDailyGoal(minutes) }
            )
        }

        if (showImportButton) {
            // 非液态主题沿用原有导入 FAB；液态按钮由导航层与 Tab 同排绘制。
            PageEntranceItem(
                play = playEntranceAnimation,
                index = 5,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .then(
                        if (isMaterial3) {
                            Modifier
                                .navigationBarsPadding()
                                .padding(end = 24.dp, bottom = 96.dp)
                        } else {
                            Modifier.padding(end = 24.dp, bottom = 100.dp)
                        }
                    )
            ) {
                if (isMaterial3) {
                    FloatingActionButton(
                        onClick = onImportClick,
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            AppIcons.Plus,
                            contentDescription = stringResource(R.string.import_books),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .shadow(8.dp, CircleShape, ambientColor = AppColors.Shadow)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .clickable(onClick = onImportClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.Plus, stringResource(R.string.import_books), tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    } // 外层 Box 结束
        }
    } // CompositionLocalProvider 结束
}
// ─── Header ──────────────────────────────────────────────────────

@Composable
private fun HomeHeader(
    onNavigateToStatistics: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppSpace.lg, end = AppSpace.sm, top = AppSpace.sm, bottom = AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.home_page),
            fontSize = AppType.Display,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            letterSpacing = (-0.02).sp,
            color = AppColors.TextPrimary
        )
        if (onNavigateToStatistics != null) {
            IconButton(
                onClick = onNavigateToStatistics,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Leaderboard,
                    contentDescription = stringResource(R.string.statistics_title),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─── 空书库 ────────────────────────────────────────────────────

@Composable
private fun ImportHint(onImportClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.BgGray)
            .clickable(onClick = onImportClick)
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            AppIcons.Book,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            text = stringResource(R.string.home_manage_hint),
            fontSize = AppType.BodySmall,
            color = AppColors.TextSecondary,
            fontFamily = SansSerif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.md))
        Text(
            text = stringResource(R.string.import_books),
            fontSize = AppType.Body,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Accent
        )
    }
}

// ─── 获取书籍简介 ──────────────────────────────────────────────
@Composable
private fun rememberBookIntro(book: Book): String {
    var intro by remember(book.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(book.id) {
        withContext(Dispatchers.IO) {
            if (book.filePath.startsWith("legado://") || book.filePath.startsWith("online://")) {
                val url = book.filePath.removePrefix("legado://").removePrefix("online://")
                intro = runCatching { appDb.bookDao.getBook(url)?.intro?.trim() }.getOrNull()
            }
            if (intro.isNullOrBlank() && book.filePath.endsWith(".epub", ignoreCase = true)) {
                intro = runCatching {
                    java.util.zip.ZipFile(book.filePath).use { zip ->
                        val container = zip.getEntry("META-INF/container.xml") ?: return@use null
                        val cDoc = zip.getInputStream(container).use { javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                        val opf = cDoc.getElementsByTagName("rootfile").item(0)?.attributes?.getNamedItem("full-path")?.nodeValue ?: return@use null
                        val opfEntry = zip.getEntry(opf) ?: return@use null
                        val oDoc = zip.getInputStream(opfEntry).use { javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
                        oDoc.getElementsByTagName("description").item(0)?.textContent?.trim()?.replace(Regex("<[^>]*>"), "")
                    }
                }.getOrNull()
            }
        }
    }
    val fallback = "已读 ${(book.readingProgress * 100).toInt()}% · 点击继续阅读"
    return intro?.takeIf { it.isNotBlank() } ?: fallback
}

// ─── 继续阅读卡片 ──────────────────────────────────────────────
@Composable
private fun ContinueReadingCard(
    book: Book,
    downloadState: BookDownloadState?,
    onClick: (Rect?) -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: (BookDeleteMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuAnchorBounds by remember { mutableStateOf(Rect.Zero) }
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"
    val liquidMenuHost = LocalLiquidGlassMenuHost.current
    val favoriteMenuLabel = if (book.isFavorite) {
        stringResource(R.string.remove_favorite)
    } else {
        stringResource(R.string.add_favorite)
    }
    val deleteMenuLabel = stringResource(R.string.delete_book)
    val coverBounds = remember { java.util.concurrent.atomic.AtomicReference(Rect.Zero) }
    val context = LocalContext.current
    val blurRadiusPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val useCoverBackground = book.coverPath != null && !LocalEInkMode.current
    val primaryContentColor = if (useCoverBackground) Color.White else AppColors.TextPrimary
    val secondaryContentColor = if (useCoverBackground) {
        Color.White.copy(alpha = 0.78f)
    } else {
        AppColors.TextSecondary
    }
    val blurredCoverRequest = remember(book.id, book.coverPath, blurRadiusPx) {
        book.coverPath?.let { coverPath ->
            ImageRequest.Builder(context)
                .data(coverPath)
                .allowHardware(false)
                .memoryCacheKey(
                    "${bookCoverMemoryCacheKey(book.id, coverPath)}:home-card-blur-v1:$blurRadiusPx"
                )
                .transformations(ReaderBackgroundBlurTransformation(blurRadiusPx))
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(AppRadius.lg), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.lg))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                onClick(coverBounds.get().takeUnless { it == Rect.Zero })
            }
    ) {
        if (useCoverBackground && blurredCoverRequest != null) {
            AsyncImage(
                model = blurredCoverRequest,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.30f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // 封面（与书架完全一致的标准网格大封面 108dp x 144dp）
        Box(
            modifier = Modifier
                .width(108.dp)
                .aspectRatio(0.75f)
                .onGloballyPositioned { coverBounds.set(it.boundsInRoot()) }
                .bookCoverTransitionAnchor(
                    bookId = book.id,
                    cornerRadiusDp = 10f,
                    titleStyle = BookCoverTitleStyle(
                        fontSizeSp = AppType.Caption.value,
                        maxLines = 3,
                        paddingDp = AppSpace.sm.value,
                        color = BookCoverTitleColor.Primary,
                        useKaiTi = true,
                        textAlignCenter = true
                    ),
                    showReadingProgress = false
                )
                .shadow(6.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.BgGray)
        ) {
            if (book.coverPath == null) {
                DefaultBookCoverTitle(
                    title = book.title,
                    fontSize = AppType.Caption,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(book.coverPath)
                        .memoryCacheKey(
                            bookCoverMemoryCacheKey(book.id, book.coverPath)
                        )
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            BookCoverProgressOverlay(
                book = book,
                downloadState = downloadState,
                compact = true,
                showReadingProgress = false,
                badgeCornerRadius = AppRadius.sm
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(144.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = resolveAppFontFamily(KaiTi),
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(
                            onClick = {
                                if (isLiquidGlass && liquidMenuHost != null && menuAnchorBounds != Rect.Zero) {
                                    liquidMenuHost.show(
                                        LiquidGlassMenuSpec(
                                            anchorBounds = menuAnchorBounds,
                                            width = 132.dp,
                                            items = listOf(
                                                LiquidGlassMenuItem(
                                                    label = favoriteMenuLabel,
                                                    icon = Icons.Outlined.FavoriteBorder,
                                                    onClick = onToggleFavorite
                                                ),
                                                LiquidGlassMenuItem(
                                                    label = deleteMenuLabel,
                                                    icon = Icons.Outlined.Delete,
                                                    destructive = true,
                                                    onClick = {
                                                        val mode = if (book.remoteFileName != null) BookDeleteMode.LOCAL_AND_CLOUD else BookDeleteMode.LOCAL_ONLY
                                                        onDelete(mode)
                                                    }
                                                )
                                            )
                                        )
                                    )
                                } else {
                                    menuExpanded = true
                                }
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .onGloballyPositioned { menuAnchorBounds = it.boundsInRoot() }
                        ) {
                            Icon(Icons.Outlined.MoreVert, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.width(116.dp),
                            shape = RoundedCornerShape(AppRadius.xl),
                            containerColor = AppColors.WindowBg,
                            border = BorderStroke(1.dp, AppColors.Divider),
                            shadowElevation = 0.dp,
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(AppRadius.lg))
                                    .clickable {
                                        menuExpanded = false
                                        onToggleFavorite()
                                    },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(17.dp), tint = AppColors.TextSecondary)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (book.isFavorite) stringResource(R.string.remove_favorite) else stringResource(R.string.add_favorite),
                                    fontSize = AppType.BodySmall,
                                    color = AppColors.TextPrimary
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(AppRadius.lg))
                                    .clickable {
                                        menuExpanded = false
                                        val mode = if (book.remoteFileName != null) BookDeleteMode.LOCAL_AND_CLOUD else BookDeleteMode.LOCAL_ONLY
                                        onDelete(mode)
                                    },
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(17.dp), tint = AppColors.Accent)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.delete_book), fontSize = AppType.BodySmall, color = AppColors.Accent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.author.ifBlank { "未知作者" },
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.isMissing) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = stringResource(R.string.book_file_unavailable),
                        fontSize = 11.sp,
                        color = Color(0xFFD92D3A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                val progressPercent = (book.readingProgress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = "已读 $progressPercent% · 点击继续阅读",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val progressPercent = (book.readingProgress * 100).toInt().coerceIn(0, 100)
                    Text(
                        text = if (downloadState is BookDownloadState.Downloading) {
                            stringResource(
                                R.string.book_download_progress,
                                (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                            )
                        } else {
                            "章节 · $progressPercent%"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Accent
                    )
                }
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFEEEEEE))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.readingProgress.coerceIn(0f, 1f))
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AppColors.Accent)
                    )
                }
            }
        }
        }
        if (book.isReadingFinished && downloadState !is BookDownloadState.Downloading) {
            FinishedReadingIndicator(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(AppSpace.md),
                compact = true,
                tint = if (useCoverBackground) secondaryContentColor else null
            )
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        CloudAwareBookDeleteDialog(
            bookCount = 1,
            hasRemoteBooks = book.remoteFileName != null,
            onDismiss = { showDeleteConfirm = false },
            onDelete = { mode ->
                showDeleteConfirm = false
                onDelete(mode)
            }
        )
    }
}

// ─── 区块标题 ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, onMoreClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            fontFamily = resolveAppFontFamily(KaiTi),
            color = AppColors.TextPrimary
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = "更多",
            tint = AppColors.TextSecondary,
            modifier = Modifier
                .size(22.dp)
                .clickable(enabled = onMoreClick != null) { onMoreClick?.invoke() }
        )
    }
}

// ─── 最近阅读卡片 ──────────────────────────────────────────────

@Composable
private fun RecentBookCard(
    book: Book,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fixedWidth: Boolean = true
) {
    Row(
        modifier = modifier
            .then(if (fixedWidth) Modifier.width(260.dp) else Modifier)
            .shadow(10.dp, RoundedCornerShape(AppRadius.md), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.md))
            .clip(RoundedCornerShape(AppRadius.md))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(AppSpace.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp, 74.dp)
                .bookCoverTransitionAnchor(
                    bookId = book.id,
                    cornerRadiusDp = 6f,
                    titleStyle = BookCoverTitleStyle(
                        fontSizeSp = 10f,
                        maxLines = 3,
                        paddingDp = AppSpace.sm.value,
                        color = BookCoverTitleColor.Primary,
                        useKaiTi = true,
                        textAlignCenter = true
                    )
                )
                .clip(RoundedCornerShape(6.dp))
                .background(AppColors.BgGray),
            contentAlignment = Alignment.Center
        ) {
            if (book.coverPath == null) {
                DefaultBookCoverTitle(
                    title = book.title,
                    fontSize = 10.sp
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(book.coverPath)
                        .memoryCacheKey(
                            bookCoverMemoryCacheKey(book.id, book.coverPath)
                        )
                        .build(),
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.width(AppSpace.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = AppType.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = book.author,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1
            )
            if (book.isReadingFinished) {
                FinishedReadingIndicator()
            } else {
                Text(
                    text = "${(book.readingProgress * 100).toInt()}%",
                    fontSize = AppType.Caption,
                    color = AppColors.Accent
                )
            }
        }
    }
}

// ─── 阅读目标卡片 ──────────────────────────────────────────────
@Composable
private fun ReadingGoalCard(
    readingTime: Long,
    dailyGoal: Int,
    weeklyData: List<DailyReading> = emptyList(),
    onCardClick: () -> Unit
) {
    val hasGoal = dailyGoal > 0
    val goalMs = if (hasGoal) dailyGoal * 60 * 1000L else 0L
    val progress = if (hasGoal) (readingTime.toFloat() / goalMs).coerceIn(0f, 1f) else 0f
    val hasReadToday = readingTime > 0L
    val todayStatusText = stringResource(
        if (hasReadToday) R.string.today_read_status_done else R.string.today_read_status_empty
    )
    val todayEmoji = if (hasReadToday) "📖 ✨ 🌿" else "🌙 ☕ 📚"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg)
            .shadow(6.dp, RoundedCornerShape(AppRadius.lg), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
            .cardOutline(RoundedCornerShape(AppRadius.lg))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(AppColors.CardBg)
            .cardPressEffect()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onCardClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasGoal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                ArcProgressBar(
                    progress = progress,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = TimeUtils.formatDurationShort(readingTime),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "(目标 $dailyGoal 分钟)",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Text(
                    text = todayEmoji,
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = todayStatusText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = stringResource(
                            if (hasReadToday) R.string.today_read_message_done else R.string.today_read_message_empty
                        ),
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        // 星期打卡（严格对齐设计稿 7 天圆圈布局）
        WeeklyCheckIn(weeklyData = weeklyData, dailyGoal = dailyGoal)
    }
}
// ─── 半圆弧进度条 ──────────────────────────────────────────────

@Composable
private fun ArcProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val accentColor = AppColors.Accent
    Canvas(modifier = modifier) {
        val stroke = 6.5.dp.toPx()
        val maxRadius = minOf((size.width - stroke) / 2f, size.height - stroke)
        val radius = maxOf(0f, maxRadius)
        val diameter = radius * 2f
        val cx = size.width / 2f
        val cy = size.height - stroke / 2f
        val topLeft = Offset(cx - radius, cy - radius)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = Color(0xFFE5E5EA),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = accentColor,
                startAngle = 180f,
                sweepAngle = (180f * progress).coerceIn(0f, 180f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// ─── 星期打卡 ──────────────────────────────────────────────────

@Composable
private fun WeeklyCheckIn(weeklyData: List<DailyReading> = emptyList(), dailyGoal: Int = 30) {
    val todayIndex = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1  // 0=周日
    val accentColor = AppColors.Accent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weeklyData.forEachIndexed { index, data ->
            val isToday = index == todayIndex
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (isToday) {
                            Modifier.background(accentColor)
                        } else {
                            Modifier
                                .background(Color.White)
                                .border(1.dp, Color(0xFFE5E5EA), CircleShape)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = data.dayLabel,
                    fontSize = 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) Color.White else AppColors.TextPrimary
                )
            }
        }
    }
}
// ─── 最近读过的图书 ────────────────────────────────────────────

@Composable
private fun BooksReadGrid(
    books: List<Book>,
    downloadStates: Map<String, BookDownloadState>,
    modifier: Modifier = Modifier,
    useWideLayout: Boolean = false,
    onBookClick: (Book, Rect?) -> Unit
) {
    val coverShape = RoundedCornerShape(
        if (LocalAppTheme.current == "liquid_glass") 16.dp else AppRadius.md
    )
    val coverRadiusDp = if (LocalAppTheme.current == "liquid_glass") {
        16f
    } else {
        AppRadius.md.value
    }
    val bookBounds = remember { mutableMapOf<String, Rect>() }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(books, key = { it.id }) { book ->
            val itemWidth = if (useWideLayout) 120.dp else 104.dp
            Column(
                modifier = Modifier
                    .width(itemWidth)
                    .aspectRatio(0.75f)
                    .bookCoverTransitionAnchor(
                        bookId = book.id,
                        cornerRadiusDp = coverRadiusDp,
                        titleStyle = BookCoverTitleStyle(
                            fontSizeSp = AppType.BodySmall.value,
                            maxLines = 3,
                            paddingDp = AppSpace.sm.value,
                            color = BookCoverTitleColor.Primary,
                            useKaiTi = true,
                            textAlignCenter = true
                        )
                    )
                    .shadow(
                        elevation = 8.dp,
                        shape = coverShape,
                        ambientColor = AppColors.CardShadow,
                        spotColor = AppColors.CardShadow
                    )
                    .clip(coverShape)
                    .background(AppColors.BgGray)
                    .border(1.dp, AppColors.Divider, coverShape)
                    .cardPressEffect()
                    .onGloballyPositioned { coordinates ->
                        bookBounds[book.id] = coordinates.boundsInRoot()
                    }
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onBookClick(book, bookBounds[book.id]) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                        .shadow(
                            elevation = 6.dp,
                            shape = coverShape,
                            ambientColor = AppColors.CardShadow,
                            spotColor = AppColors.CardShadow
                        )
                        .clip(coverShape)
                        .background(AppColors.BgGray)
                        .border(1.dp, AppColors.Divider, coverShape)
                        .cardPressEffect()
                        .onGloballyPositioned { coordinates ->
                            bookBounds[book.id] = coordinates.boundsInRoot()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverPath == null) {
                        DefaultBookCoverTitle(
                            title = book.title,
                            fontSize = AppType.BodySmall
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(book.coverPath)
                                .memoryCacheKey(
                                    bookCoverMemoryCacheKey(book.id, book.coverPath)
                                )
                                .build(),
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    BookCoverProgressOverlay(
                        book = book,
                        downloadState = downloadStates[book.id],
                        compact = true,
                        badgeCornerRadius = coverRadiusDp.dp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = book.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = resolveAppFontFamily(KaiTi),
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = book.author.ifBlank { "未知作者" },
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private const val HOME_SCREEN_TAG = "home_screen"

@Composable
private fun DefaultBookCoverTitle(
    title: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        modifier = modifier.padding(AppSpace.sm),
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = resolveAppFontFamily(KaiTi),
        color = AppColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}
