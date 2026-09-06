package io.legado.app.data.entities.rule


data class FlexChildStyle(
    val layout_flexGrow: Float = 0F,
    val layout_flexShrink: Float = 1F,
    val layout_alignSelf: String = "auto",
    val layout_flexBasisPercent: Float = -1F,
    val layout_wrapBefore: Boolean = false,
    /** 自定义的内部水平对齐属性 **/
    val layout_justifySelf: String = "auto"
) {

    fun alignSelf(): Int {
        return when (layout_alignSelf) {
            "auto" -> -1
            "flex_start" -> 0
            "flex_end" -> 1
            "center" -> 2
            "baseline" -> 3
            "stretch" -> 4
            else -> -1
        }
    }
    /**
     * Compose/宿主 UI 可读取这些字段自行应用布局；引擎不再持有 View/Flexbox 类型。
     */


    companion object {
        val defaultStyle = FlexChildStyle()
    }

}
