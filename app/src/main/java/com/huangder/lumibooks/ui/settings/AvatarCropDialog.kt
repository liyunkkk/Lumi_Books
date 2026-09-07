package com.huangder.lumibooks.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.bookshelf.CoverCropOverlay
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * 带有 1:1 正方形自由裁剪区域的头像裁剪对话框。
 */
@Composable
fun AvatarCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropped: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var cropFrame by remember { mutableStateOf<Rect?>(null) }

    // 异步解码选中的图片（兼容 Android 14/15/16 PhotoPicker 与流式回退）
    LaunchedEffect(imageUri) {
        isLoading = true
        val bmp = withContext(Dispatchers.IO) {
            decodeBitmapSafely(context, imageUri)
        }
        loadedBitmap = bmp
        isLoading = false
    }

    // 初始化 1:1 裁剪框居中
    LaunchedEffect(containerSize, loadedBitmap) {
        val bmp = loadedBitmap
        if (containerSize.width > 0f && containerSize.height > 0f && bmp != null && cropFrame == null) {
            val imgRect = computeImageBounds(bmp.width.toFloat(), bmp.height.toFloat(), containerSize)
            val cropSide = min(imgRect.width, imgRect.height) * 0.85f
            cropFrame = Rect(
                left = imgRect.center.x - cropSide / 2f,
                top = imgRect.center.y - cropSide / 2f,
                right = imgRect.center.x + cropSide / 2f,
                bottom = imgRect.center.y + cropSide / 2f
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.size.width > 0 && coordinates.size.height > 0) {
                        containerSize = Size(
                            coordinates.size.width.toFloat(),
                            coordinates.size.height.toFloat()
                        )
                    }
                }
        ) {
            val bmp = loadedBitmap
            if (bmp != null) {
                // 图片主视区
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    // 1:1 头像裁剪框
                    CoverCropOverlay(
                        visible = true,
                        frame = cropFrame,
                        containerSize = containerSize,
                        onFrameChange = { cropFrame = it },
                        aspectRatio = 1.0f,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (!isLoading) {
                // 解码失败提示
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(AppSpace.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "图片解析失败，请重新选择其他图片",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                    ) {
                        Text("返回", color = Color.White)
                    }
                }
            }

            if (isLoading || isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // 顶栏操作区
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "取消", tint = Color.White)
                }

                Text(
                    text = "裁剪头像",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        val currentBmp = loadedBitmap ?: return@IconButton
                        val frame = cropFrame ?: return@IconButton
                        if (containerSize == Size.Zero || isProcessing) return@IconButton

                        scope.launch {
                            isProcessing = true
                            val savedPath = withContext(Dispatchers.IO) {
                                runCatching {
                                    val imgRect = computeImageBounds(
                                        currentBmp.width.toFloat(),
                                        currentBmp.height.toFloat(),
                                        containerSize
                                    )
                                    // 坐标投影回原始 Bitmap 空间
                                    val scale = currentBmp.width.toFloat() / imgRect.width
                                    val rawX = ((frame.left - imgRect.left) * scale).toInt().coerceIn(0, currentBmp.width - 1)
                                    val rawY = ((frame.top - imgRect.top) * scale).toInt().coerceIn(0, currentBmp.height - 1)
                                    val rawW = (frame.width * scale).toInt().coerceIn(1, currentBmp.width - rawX)
                                    val rawH = (frame.height * scale).toInt().coerceIn(1, currentBmp.height - rawY)

                                    val cropped = Bitmap.createBitmap(currentBmp, rawX, rawY, rawW, rawH)
                                    // 缩放为标准 512x512 高清头像
                                    val finalAvatar = if (cropped.width > 512 || cropped.height > 512) {
                                        Bitmap.createScaledBitmap(cropped, 512, 512, true)
                                    } else {
                                        cropped
                                    }

                                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                                    // 清除历史头像文件
                                    avatarDir.listFiles()?.forEach { file ->
                                        if (file.name.startsWith("avatar")) {
                                            runCatching { file.delete() }
                                        }
                                    }
                                    // 生成带时间戳的全新头像文件以确保缓存绝对失效
                                    val newFile = File(avatarDir, "avatar_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(newFile).use { out ->
                                        finalAvatar.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    newFile.absolutePath
                                }.getOrNull()
                            }
                            isProcessing = false
                            if (savedPath != null) {
                                onCropped(savedPath)
                            } else {
                                Toast.makeText(context, R.string.epub_image_save_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = loadedBitmap != null && !isProcessing,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (loadedBitmap != null) AppColors.Accent else Color.Gray.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "完成", tint = Color.White)
                }
            }

            // 底部提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .align(Alignment.BottomCenter),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "拖动边框或边角可调整头像选取区域",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 计算 ContentScale.Fit 模式下图片在容器内的真实呈现 Rect
 */
private fun computeImageBounds(imgWidth: Float, imgHeight: Float, container: Size): Rect {
    if (imgWidth <= 0f || imgHeight <= 0f || container.width <= 0f || container.height <= 0f) {
        return Rect(0f, 0f, container.width, container.height)
    }
    val scale = min(container.width / imgWidth, container.height / imgHeight)
    val displayW = imgWidth * scale
    val displayH = imgHeight * scale
    val left = (container.width - displayW) / 2f
    val top = (container.height - displayH) / 2f
    return Rect(left, top, left + displayW, top + displayH)
}

/**
 * 安全健壮的 Uri 图片解码：
 * 优先采用 Android 9+ ImageDecoder，若异常则通过单次 InputStream 一次性读取字节数组解码，
 * 彻底规避二次 openInputStream 失效与权限问题。
 */
private fun decodeBitmapSafely(context: Context, uri: Uri): Bitmap? {
    // 1. Android 9+ 优先使用原生 ImageDecoder（支持 HEIC/AVIF/EXIF 自动纠偏等）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = 2048
                val w = info.size.width
                val h = info.size.height
                if (w > maxDim || h > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(w, h)
                    decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
                }
            }
            if (bmp != null) return bmp
        } catch (e: Throwable) {
            Log.w("AvatarCropDialog", "ImageDecoder decode failed, will fallback to byte array", e)
        }
    }

    // 2. 回退：一次性读取全部字节，避免二次 openInputStream 失败
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes != null && bytes.isNotEmpty()) {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            var inSampleSize = 1
            val maxDim = 2048
            if (boundsOptions.outWidth > 0 && boundsOptions.outHeight > 0) {
                while ((boundsOptions.outWidth / inSampleSize) > maxDim || (boundsOptions.outHeight / inSampleSize) > maxDim) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                this.inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            if (bmp != null) return bmp
        }
    } catch (e: Throwable) {
        Log.e("AvatarCropDialog", "decodeByteArray fallback failed", e)
    }

    return null
}