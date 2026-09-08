package com.huangder.lumibooks.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import kotlin.math.absoluteValue

/**
 * 首页在读精选模式（3D景深卡片流）
 * 对应杂志化竖排大标题、横向视差堆叠、左右书籍高比例露出与全圆角悬浮液态玻璃极简视窗
 */
@Composable
fun CardFlowHomeScreen(
    books: List<Book>,
    todayReadingTime: Long,
    dailyGoal: Int,
    streakDays: Int,
    avatarUri: String?,
    statusBarTopPadding: androidx.compose.ui.unit.Dp,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onNavigateToReader: (bookId: String, coverPath: String?, title: String, sourceBounds: Rect?) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToBookshelf: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayBooks = remember(books) {
        books.sortedByDescending { it.lastReadTime }
    }
    var selectedBookForDetail by remember { mutableStateOf<Book?>(null) }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { displayBooks.size }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = statusBarTopPadding + 10.dp,
                    bottom = 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 竖排杂志风大标题 Header（右上角统计图标与 Reading 底部水平对齐）
            CardFlowHeader(
                readingBookCount = displayBooks.size,
                onStatisticsClick = onNavigateToStatistics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(12.dp))

            // 2. 中央 3D 景深卡片流（左右书籍大比例露出堆叠）
            if (displayBooks.isEmpty()) {
                EmptyReadingCard(
                    onImportClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val screenWidth = maxWidth
                    val cardWidth = 200.dp
                    val horizontalPadding = ((screenWidth - cardWidth) / 2).coerceAtLeast(32.dp)

                    HorizontalPager(
                        state = pagerState,
                        pageSize = PageSize.Fixed(cardWidth),
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        pageSpacing = 16.dp,
                        beyondViewportPageCount = 2,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val book = displayBooks[page]
                        val pageOffset = (page - pagerState.currentPage) + pagerState.currentPageOffsetFraction
                        val absOffset = pageOffset.absoluteValue.coerceIn(0f, 2f)

                        // 3D 景深变换参数：向内靠拢叠放，使得上一本和下一本能够稳定露出约 40% 封面
                        val scale = 1f - (absOffset * 0.12f).coerceIn(0f, 0.18f)
                        val rotationY = -pageOffset.coerceIn(-1.5f, 1.5f) * 20f
                        val alpha = 1f - (absOffset * 0.18f).coerceIn(0f, 0.30f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.rotationY = rotationY
                                    this.alpha = alpha
                                    this.translationX = -pageOffset * 10.dp.toPx()
                                    cameraDistance = 16 * density
                                }
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    selectedBookForDetail = book
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Book3DCardItem(
                                book = book,
                                isCenter = absOffset < 0.25f,
                                onClick = { selectedBookForDetail = book }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(bottomContentPadding + 10.dp))
        }

        // 3. 卡片平滑展开为悬浮居中、全圆角液态玻璃极简详情视窗
        selectedBookForDetail?.let { book ->
            BookDetailDialog(
                book = book,
                onDismiss = { selectedBookForDetail = null },
                onContinueReading = {
                    val targetBook = book
                    selectedBookForDetail = null
                    onNavigateToReader(targetBook.id, targetBook.coverPath, targetBook.title, null)
                }
            )
        }
    }
}

private val ProductSansBlack = FontFamily(Font(R.font.product_sans_black, FontWeight.Black))

/**
 * 顶部竖排杂志风大标题 Header
 * I'm Now Reading 垂直排版 + 42sp 极粗几何 Product Sans Black + 统计图标严格等比底平齐
 */
@Composable
private fun CardFlowHeader(
    readingBookCount: Int,
    onStatisticsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(verticalArrangement = Arrangement.spacedBy((-4).dp)) {
                Text(
                    text = "I'm",
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontFamily = ProductSansBlack,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Now",
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontFamily = ProductSansBlack,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Reading",
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontFamily = ProductSansBlack,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )
            }
            // 右上角统计图标按钮，等比放大至 52dp（图标 32dp）且底边与 Reading 底部水平严格平齐
            IconButton(
                onClick = onStatisticsClick,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Leaderboard,
                    contentDescription = stringResource(R.string.statistics_title),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (readingBookCount > 0) "共 $readingBookCount 本书籍在读" else "暂无正在阅读的书籍",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 3D 景深单书卡片（精致尺寸 195dp，纯净封面无多余文字与进度条，书名与作者在封面下方居中）
 */
@Composable
private fun Book3DCardItem(
    book: Book,
    isCenter: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .shadow(
                    elevation = if (isCenter) 20.dp else 6.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black.copy(alpha = 0.25f),
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (!book.coverPath.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverPath,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DefaultMagazineCover(book = book)
            }
        }

        Spacer(Modifier.height(12.dp))

        // 封面下方居中：书名（加粗）与作者（灰色）
        Text(
            text = book.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (book.author.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = book.author,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 悬浮居中、上下全圆角的液态玻璃极简详情视窗（参考图 4）
 */
@Composable
private fun BookDetailDialog(
    book: Book,
    onDismiss: () -> Unit,
    onContinueReading: () -> Unit
) {
    val isLiquidGlass = LocalAppTheme.current == "liquid_glass"

    val dialogContent = @Composable {
        BookDetailSheetContent(
            book = book,
            onDismiss = onDismiss,
            onContinueReading = onContinueReading
        )
    }

    if (isLiquidGlass) {
        LiquidGlassDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.fillMaxHeight(0.74f),
            alignment = Alignment.Center,
            shape = RoundedCornerShape(32.dp),
            contentScrimColor = AppColors.CardBg.copy(alpha = 0.45f),
            backgroundScrimColor = Color.Black.copy(alpha = 0.25f)
        ) {
            dialogContent()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .fillMaxHeight(0.74f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    tonalElevation = 8.dp
                ) {
                    dialogContent()
                }
            }
        }
    }
}

@Composable
private fun BookDetailSheetContent(
    book: Book,
    onDismiss: () -> Unit,
    onContinueReading: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var animState by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animState = true
    }
    val contentScale by animateFloatAsState(
        targetValue = if (animState) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 280f),
        label = "sheetContentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (animState) 1f else 0f,
        animationSpec = tween(180),
        label = "sheetContentAlpha"
    )

    // 异步拉取每本书的完整简介（网络书库与本地 EPUB/TXT 等通用）
    var bookIntro by remember(book.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(book.id) {
        withContext(Dispatchers.IO) {
            val pathStr = book.filePath.orEmpty()
            var resolvedUrl: String? = null
            if (pathStr.startsWith("legado-online://")) {
                resolvedUrl = pathStr.removePrefix("legado-online://")
            } else if (pathStr.startsWith("legado://")) {
                resolvedUrl = pathStr.removePrefix("legado://")
            } else if (pathStr.startsWith("online://")) {
                resolvedUrl = pathStr.removePrefix("online://")
            }

            if (!resolvedUrl.isNullOrBlank()) {
                val cached = runCatching { io.legado.app.data.appDb.bookDao.getBook(resolvedUrl) }.getOrNull()
                val cachedIntro = cached?.getDisplayIntro()?.trim() ?: cached?.intro?.trim()
                if (!cachedIntro.isNullOrBlank()) {
                    bookIntro = cachedIntro
                } else if (cached != null) {
                    val source = runCatching { io.legado.app.data.appDb.bookSourceDao.getBookSource(cached.origin) }.getOrNull()
                    if (source != null) {
                        runCatching {
                            // 强制清空 infoHtml 避免跳过真实网页请求
                            val targetBook = cached.apply { infoHtml = null }
                            val detailed = io.legado.app.model.webBook.WebBook.getBookInfoAwait(source, targetBook)
                            val rawIntro = detailed.getDisplayIntro().orEmpty().ifBlank { detailed.intro ?: "" }.trim()
                            if (rawIntro.isNotBlank()) {
                                runCatching { io.legado.app.data.appDb.bookDao.insert(detailed) }
                                bookIntro = rawIntro
                            }
                        }
                    }
                }
            }

            if (bookIntro.isNullOrBlank() && pathStr.endsWith(".epub", ignoreCase = true)) {
                bookIntro = runCatching {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                alpha = contentAlpha
            }
            .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 顶部操作栏：左上角圆形叉号关闭按钮（参考图 4）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 中间主体（可滚动居中内容）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 居中大封面（大圆角，点击可直接开始阅读）
            Card(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(0.68f)
                    .shadow(16.dp, RoundedCornerShape(18.dp))
                    .clickable(onClick = onContinueReading),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!book.coverPath.isNullOrBlank()) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = book.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    DefaultMagazineCover(book = book)
                }
            }

            Spacer(Modifier.height(14.dp))

            // 封面下方居中：书名
            Text(
                text = book.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 封面下方居中：作者
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = book.author,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 书籍简介（居于作者与继续阅读按钮之间，精致限高可滚动卡片）
            if (!bookIntro.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = bookIntro!!,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            // 精致圆角「继续阅读 / 开始阅读」行动按钮
            Button(
                onClick = onContinueReading,
                modifier = Modifier
                    .widthIn(min = 150.dp, max = 200.dp)
                    .height(42.dp),
                shape = RoundedCornerShape(21.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (book.readingProgress > 0) "继续阅读" else "开始阅读",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 底部极简 AI 伴读输入框（圆角胶囊形输入框 + 向上箭头发送按钮）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(start = 18.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (inputText.isBlank()) {
                            Text(
                                text = "Ask about this book...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
            IconButton(
                onClick = { /* 占位预留 */ },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowUpward,
                    contentDescription = "Send",
                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * 默认杂志风封面（无网络或本地封面图时回退）
 */
@Composable
private fun DefaultMagazineCover(book: Book) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2C3E50),
                        Color(0xFF3498DB),
                        Color(0xFF2980B9)
                    )
                )
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = book.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = book.author,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 书架为空时的空状态卡片
 */
@Composable
private fun EmptyReadingCard(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.72f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "暂无在读书籍",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "导入本地书籍或在线书源，享受 3D 景深伴读",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onImportClick,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("导入或添加书籍")
            }
        }
    }
}
