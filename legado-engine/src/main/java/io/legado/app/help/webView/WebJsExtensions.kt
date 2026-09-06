package io.legado.app.help.webView

import android.webkit.WebView
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.JsExtensions
import java.lang.ref.WeakReference
import java.util.UUID

/**
 * Shim：上游 WebJsExtensions 继承 RssJsExtensions，牵连整套 RSS UI。
 *
 * 引擎侧只需要 BackstageWebView 注入 JS 桥所依赖的部分：
 * - 随机化的桥对象名（防止书源脚本探测固定名字）
 * - getInjectionString 注入串
 * - JsExtensions 提供的规则内置函数
 */
class WebJsExtensions(
    private val source: BaseSource,
    @Suppress("UNUSED_PARAMETER") activity: Any? = null,
    webView: WebView? = null,
) : JsExtensions {

    private val webViewRef: WeakReference<WebView?> = WeakReference(webView)

    override fun getSource(): BaseSource = source

    override fun getTag(): String? = source.getTag()

    @Suppress("unused")
    fun getWebView(): WebView? = webViewRef.get()

    companion object {
        private fun getRandomLetter(): Char = "abcdefghijklmnopqrstuvwxyz".random()

        val uuid: List<String> by lazy {
            UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6)
        }
        val uuid2: List<String> by lazy {
            UUID.randomUUID().toString().replace('-', getRandomLetter()).chunked(6)
        }

        val nameUrl by lazy { "https://" + uuid[0] + ".com/" + uuid2[0] + ".js" }
        val nameJava by lazy { getRandomLetter() + uuid[1] + uuid2[1] }
        val nameCache by lazy { getRandomLetter() + uuid[2] + uuid2[2] }
        val nameSource by lazy { getRandomLetter() + uuid[3] + uuid2[3] }
        val nameBasic by lazy { getRandomLetter() + uuid[4] + uuid2[4] }

        val getInjectionString =
            "try{var cache=$nameCache,source=$nameSource,java=$nameJava;}catch(e){}"
    }
}