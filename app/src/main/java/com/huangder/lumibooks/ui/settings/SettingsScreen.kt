package com.huangder.lumibooks.ui.settings
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import com.huangder.lumibooks.ui.components.lumiRowClickable
import com.huangder.lumibooks.ui.icons.AppIcons

import com.huangder.lumibooks.ui.components.lumiCardSurface
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.huangder.lumibooks.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.compose.AsyncImage
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.huangder.lumibooks.ui.components.DisclosureChevron
import com.huangder.lumibooks.ui.components.GroupedCard
import com.huangder.lumibooks.ui.theme.cardOutline
import com.huangder.lumibooks.ui.components.GroupedRowDivider
import com.huangder.lumibooks.ui.components.SettingsIconTile
import com.huangder.lumibooks.ui.components.SettingsIconTileGap
import com.huangder.lumibooks.ui.components.SettingsIconTileSize
import com.huangder.lumibooks.ui.components.SettingsTileColors

// ─── 设置主页（分类列表）────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 昵称编辑对话框状态
    var showNicknameDialog by remember { mutableStateOf(false) }

    // 待裁剪的头像原始图片 Uri
    var pendingCropImageUri by remember { mutableStateOf<Uri?>(null) }
    // 头像选择
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            pendingCropImageUri = selectedUri
        }
    }

    AdaptiveSettingsOverview(
        onBack = onNavigateBack,
        actions = {
            LiquidGlassIconButton(
                imageVector = AppIcons.ChatCircle,
                contentDescription = stringResource(R.string.notice_history_title),
                onClick = { context.startActivity(DetailActivity.createIntent(context, NOTICE_HISTORY_CATEGORY)) }
            )
        },
        overview = {
            Spacer(Modifier.height(AppSpace.sm))

                // 头像快捷入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpace.page)
                        .padding(bottom = AppSpace.lg)
                        .shadow(8.dp, AppRoundedCornerShape(AppRadius.lg), ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow)
                        .clip(AppRoundedCornerShape(AppRadius.lg))
                        .lumiCardSurface(shape = AppRoundedCornerShape(AppRadius.lg))
                        .padding(horizontal = AppSpace.md, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // 头像——点击打开相册
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AppColors.BgGray)
                        .border(1.dp, AppColors.Divider, CircleShape)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                            runCatching {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }.onFailure {
                                Toast.makeText(context, R.string.epub_image_load_failed, Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.avatarUri != null) {
                        val avatarFile = File(uiState.avatarUri)
                        AsyncImage(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(avatarFile)
                                .memoryCacheKey("avatar_${uiState.avatarUri}_${avatarFile.lastModified()}")
                                .diskCacheKey("avatar_${uiState.avatarUri}_${avatarFile.lastModified()}")
                                .build(),
                            contentDescription = stringResource(R.string.avatar),
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            AppIcons.UserCircle,
                            stringResource(R.string.default_avatar),
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
                Spacer(Modifier.width(AppSpace.md))
                // 昵称——点击编辑昵称
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { showNicknameDialog = true }
                        )
                ) {
                    Text(uiState.nickname, fontSize = AppType.Section, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(R.string.tap_to_change_nickname), fontSize = AppType.Footnote, color = AppColors.TextSecondary)
                }
                DisclosureChevron()
            }

                com.huangder.lumibooks.edition.EditionMembershipEntry()

                CategoryItem(AppIcons.Tile.Folders, stringResource(R.string.authorized_folders_title), tileColor = SettingsTileColors.Blue) {
                    context.startActivity(Intent(context, AuthorizedFoldersActivity::class.java))
                }
                Spacer(Modifier.height(AppSpace.lg))

                com.huangder.lumibooks.edition.EditionTagManagementSettingsEntry()

        },
        preferences = {
                // 分类列表：按设置域分组，组内使用细分隔线保持扫描节奏。
                SettingsCategoryGroup {
                    CategoryItem(AppIcons.Tile.Reading, stringResource(R.string.title_reading_settings), grouped = true, tileColor = SettingsTileColors.Orange) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "reading"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Display, stringResource(R.string.category_display), grouped = true, tileColor = SettingsTileColors.Blue) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "display"))
                    }
                    SettingsTileDivider()
                    // iOS Settings shows the current choice before the chevron.
                    CategoryItem(AppIcons.Tile.Language, stringResource(R.string.category_language), grouped = true, tileColor = SettingsTileColors.Gray,
                        value = com.huangder.lumibooks.util.LocaleHelper.LANGUAGE_DISPLAY_NAMES[uiState.appLanguage]) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "language"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Privacy, stringResource(R.string.category_privacy), grouped = true, tileColor = SettingsTileColors.Indigo) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "privacy"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Storage, stringResource(R.string.category_storage), grouped = true, tileColor = SettingsTileColors.Green) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "storage"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Backup, stringResource(R.string.category_backup), grouped = true, tileColor = SettingsTileColors.Teal) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "backup"))
                    }
                }
                Spacer(Modifier.height(AppSpace.lg))
                SettingsCategoryGroup {
                    CategoryItem(AppIcons.Tile.Services, stringResource(R.string.category_third_party_services), grouped = true, tileColor = SettingsTileColors.Cyan) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "third_party_services"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.About, stringResource(R.string.category_about), grouped = true, tileColor = SettingsTileColors.Gray) {
                        context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "about"))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Sponsor, stringResource(R.string.category_sponsor), grouped = true, tileColor = SettingsTileColors.Pink) {
                        context.startActivity(Intent(context, SponsorActivity::class.java))
                    }
                    SettingsTileDivider()
                    CategoryItem(AppIcons.Tile.Feedback, stringResource(R.string.category_feedback), grouped = true, tileColor = SettingsTileColors.Red) {
                        context.startActivity(Intent(context, FeedbackActivity::class.java))
                    }
                }
        }
    )


    // ── 昵称编辑对话框（卡片风格） ──
    if (showNicknameDialog) {
        LiquidGlassDialog(
            onDismissRequest = { showNicknameDialog = false },
            modifier = Modifier.imePadding(),
            backgroundScrimColor = Color.Black.copy(alpha = 0.20f),
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            com.huangder.lumibooks.ui.components.EditInputDialog(
                        title = stringResource(R.string.edit_nickname),
                        fields = listOf(
                            Triple(stringResource(R.string.nickname_label), "", uiState.nickname)
                        ),
                        onBack = { showNicknameDialog = false },
                        onConfirm = { values ->
                            viewModel.saveNickname(values[0])
                            showNicknameDialog = false
                        }
                    )
        }
    }
    // ── 头像 1:1 裁剪浮层 ──
    pendingCropImageUri?.let { uri ->
        AvatarCropDialog(
            imageUri = uri,
            onDismiss = { pendingCropImageUri = null },
            onCropped = { savedPath ->
                pendingCropImageUri = null
                viewModel.saveAvatar(savedPath)
            }
        )
    }
}
@Composable
fun ThirdPartyServicesDetail(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    SettingsSectionTitle(stringResource(R.string.settings_section_services_reading), "services_reading")
    SettingsCategoryGroup {
        CategoryItem(
            icon = Icons.Outlined.AutoStories,
            label = "书源管理",
            supportingText = "导入 Legado 书源 JSON",
            grouped = true
        ) {
            context.startActivity(Intent(context, com.huangder.lumibooks.ui.sources.BookSourcesActivity::class.java))
        }
        SettingsGroupDivider()
        CategoryItem(
            label = stringResource(R.string.dictionary_title),
            supportingText = stringResource(R.string.dictionary_management_intro),
            grouped = true
        ) {
            com.huangder.lumibooks.dictionary.openDictionarySettings(context)
        }
        SettingsGroupDivider()
        CategoryItem(
            label = stringResource(R.string.translation_title),
            supportingText = stringResource(R.string.translation_description),
            grouped = true
        ) {
            com.huangder.lumibooks.translation.openTranslationSettings(context)
        }
        SettingsGroupDivider()
        CategoryItem(
            label = stringResource(R.string.category_mineru),
            supportingText = when (uiState.mineruMode) {
                "agent" -> stringResource(R.string.mineru_mode_agent_short)
                "precise" -> stringResource(R.string.mineru_mode_precise_short)
                else -> stringResource(R.string.mineru_not_configured)
            },
            grouped = true
        ) {
            context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "mineru"))
        }
    }

    Spacer(Modifier.height(AppSpace.sm))
    SettingsSectionTitle(stringResource(R.string.settings_section_services_listening), "services_listening")
    SettingsCategoryGroup {
        CategoryItem(
            label = stringResource(R.string.category_external_tts),
            supportingText = when {
                uiState.externalTtsSettings.enabled &&
                    uiState.ttsProviderSelection == com.huangder.lumibooks.tts.TtsProviderSelection.AiModel ->
                    stringResource(R.string.external_tts_selected_status)
                uiState.externalTtsSettings.enabled ->
                    stringResource(R.string.external_tts_configured_status)
                else -> stringResource(R.string.external_tts_not_configured)
            },
            grouped = true
        ) {
            context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "external_tts"))
        }
    }

    Spacer(Modifier.height(AppSpace.sm))
    SettingsSectionTitle(stringResource(R.string.settings_section_services_sync), "services_sync")
    SettingsCategoryGroup {
        CategoryItem(
            label = stringResource(R.string.category_webdav),
            supportingText = when {
                uiState.webdavConfig.enabled -> stringResource(R.string.webdav_enabled_status)
                else -> stringResource(R.string.webdav_not_configured)
            },
            grouped = true
        ) {
            context.startActivity(Intent(context, DetailActivity::class.java).putExtra("category", "webdav"))
        }
        SettingsGroupDivider()
        CategoryItem(
            icon = AppIcons.Globe,
            label = stringResource(R.string.remote_library_opds_title),
            supportingText = stringResource(R.string.remote_library_settings_opds_summary),
            grouped = true
        ) {
            context.startActivity(com.huangder.lumibooks.ui.home.remote.RemoteLibraryActivity.createIntent(
                context, com.huangder.lumibooks.data.remote.RemoteSourceType.OPDS, null
            ))
        }
        SettingsGroupDivider()
        CategoryItem(
            icon = AppIcons.Cloud,
            label = stringResource(R.string.remote_library_webdav_title),
            supportingText = stringResource(R.string.remote_library_settings_webdav_summary),
            grouped = true
        ) {
            context.startActivity(com.huangder.lumibooks.ui.home.remote.RemoteLibraryActivity.createIntent(
                context, com.huangder.lumibooks.data.remote.RemoteSourceType.WEBDAV, null
            ))
        }
    }
}

// ─── 分类条目 ──────────────────────────────────────────────────

@Composable
internal fun CategoryItem(
    icon: ImageVector? = null,
    label: String,
    supportingText: String? = null,
    grouped: Boolean = false,
    tileColor: Color? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    val shape = AppRoundedCornerShape(AppRadius.lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (grouped) Modifier else Modifier.padding(horizontal = AppSpace.page, vertical = 1.dp))
            .then(if (grouped) Modifier else Modifier.shadow(8.dp, shape, ambientColor = AppColors.CardShadow, spotColor = AppColors.CardShadow))
            .then(if (grouped) Modifier else Modifier.cardOutline(shape).clip(shape).lumiCardSurface(shape = shape))
            .lumiRowClickable(onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(
                horizontal = AppSpace.md,
                vertical = if (icon != null && tileColor != null) 11.dp else AppSpace.md
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null && tileColor != null) {
            SettingsIconTile(icon, tileColor)
            Spacer(Modifier.width(SettingsIconTileGap))
        } else if (icon != null) {
            Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(AppSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = AppType.Body, color = AppColors.TextPrimary)
            if (supportingText != null) {
                Spacer(Modifier.height(2.dp))
                Text(supportingText, fontSize = AppType.Caption, color = AppColors.TextSecondary)
            }
        }
        if (value != null) {
            Text(value, fontSize = AppType.BodySmall, color = AppColors.TextSecondary, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AppSpace.sm, end = AppSpace.xs).widthIn(max = 160.dp))
        }
        DisclosureChevron()
    }
}

@Composable
private fun SettingsCategoryGroup(content: @Composable () -> Unit) {
    GroupedCard { content() }
}

@Composable
private fun SettingsGroupDivider() {
    GroupedRowDivider(startInset = 54.dp)
}

/** Divider of tiled rows, aligned with the label after the tile. */
@Composable
private fun SettingsTileDivider() {
    GroupedRowDivider(startInset = AppSpace.md + SettingsIconTileSize + SettingsIconTileGap)
}
