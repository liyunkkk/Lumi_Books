package io.legado.app.data.entities

/**
 * 高亮标签分组结果。
 *
 * 上游定义在 ui/book/info 的 Contract 里（属于 UI 层数据），
 * 引擎的 parseHighlightedTags() 需要它作为返回类型，故下沉到 entities 包，
 * 保持字段完全一致，宿主 UI 可直接消费。
 */
data class HighlightedTag(
    val matchedLabels: List<String>,
    val title: String?,
)