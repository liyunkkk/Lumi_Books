@file:Suppress("unused")

package io.legado.app.utils

import android.content.Context
import android.widget.Toast

/**
 * Shim：上游 ToastUtils 依赖 ViewBinding 自定义 Toast 布局。
 * 引擎侧只需要系统 Toast，去掉 databinding 依赖。
 */
private var toast: Toast? = null

fun Context.toastOnUi(message: Int, duration: Int = Toast.LENGTH_SHORT) {
    toastOnUi(getString(message), duration)
}

fun Context.toastOnUi(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
    runOnUI {
        kotlin.runCatching {
            toast?.cancel()
            toast = Toast.makeText(this, message, duration)
            toast?.show()
        }
    }
}

fun Context.toastOnUiLegacy(message: CharSequence) = toastOnUi(message)

fun Context.longToastOnUi(message: Int) = toastOnUi(message, Toast.LENGTH_LONG)

fun Context.longToastOnUi(message: CharSequence?) = toastOnUi(message, Toast.LENGTH_LONG)

fun Context.longToastOnUiLegacy(message: CharSequence) = toastOnUi(message, Toast.LENGTH_LONG)

/** JS 专用弹窗，引擎内退化为普通 Toast */
fun Context.toastForJs(message: CharSequence?, duration: Int = Toast.LENGTH_SHORT) =
    toastOnUi(message, duration)

fun Context.toastForJs(message: Int, duration: Int = Toast.LENGTH_SHORT) =
    toastOnUi(message, duration)

fun Context.longToastForJs(message: CharSequence?) = toastOnUi(message, Toast.LENGTH_LONG)

fun Context.longToastForJs(message: Int) = toastOnUi(message, Toast.LENGTH_LONG)