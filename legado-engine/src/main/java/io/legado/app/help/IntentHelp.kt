package io.legado.app.help

import android.content.Intent
import androidx.core.net.toUri
import splitties.init.appCtx

/**
 * 引擎精简版：仅保留书源引擎真实需要的浏览器 Intent 构造。
 * 上游的 TTS 设置跳转、未知来源安装等宿主 UI 行为不在引擎职责内。
 */
@Suppress("unused")
object IntentHelp {

    fun getBrowserIntent(url: String): Intent {
        return getBrowserIntent(url.toUri())
    }

    fun getBrowserIntent(uri: android.net.Uri): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = uri
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(appCtx.packageManager) == null) {
            return Intent.createChooser(intent, "请选择浏览器")
        }
        return intent
    }
}
