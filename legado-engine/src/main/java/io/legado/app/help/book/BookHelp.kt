@file:Suppress("unused")

package io.legado.app.help.book

import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.help.config.ReadConfig
import io.legado.app.utils.FileUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import org.apache.commons.text.similarity.JaccardSimilarity
import splitties.init.appCtx
import java.io.File
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 书籍缓存与章节匹配帮助类（引擎精简版）。
 *
 * 相对上游裁掉的部分：
 * - 图片下载/解码（Glide、Semaphore 并发槽、SvgUtils、ImageUtils）
 * - 漫画缓存清理与 epub/压缩包本地解包
 * - 本地 TXT 回写（依赖 LocalBook / TextFile）
 * - EventBus 广播
 * 保留的是书源抓取链路真正调用的：正文缓存读写、缓存目录迁移、
 * 书名/作者格式化、换源后的章节位置匹配。
 */
object BookHelp {

    private val downloadDir: File = appCtx.externalFiles
    private const val cacheFolderName = "book_cache"

    val cachePath: String = FileUtils.getPath(downloadDir, cacheFolderName)

    fun clearCache() {
        FileUtils.delete(FileUtils.getPath(downloadDir, cacheFolderName))
    }

    fun clearCache(book: Book) {
        FileUtils.delete(FileUtils.getPath(downloadDir, cacheFolderName, book.getFolderName()))
    }

    /**
     * 换源/重新绑定后迁移缓存目录
     */
    fun updateCacheFolder(oldBook: Book, newBook: Book) {
        val oldFolderName = oldBook.getFolderNameNoCache()
        val newFolderName = newBook.getFolderNameNoCache()
        if (oldFolderName == newFolderName) return
        val oldFolderPath = FileUtils.getPath(downloadDir, cacheFolderName, oldFolderName)
        val newFolderPath = FileUtils.getPath(downloadDir, cacheFolderName, newFolderName)
        FileUtils.move(oldFolderPath, newFolderPath)
    }

    fun saveContent(
        bookSource: BookSource,
        book: Book,
        bookChapter: BookChapter,
        content: String
    ) {
        try {
            saveText(book, bookChapter, content)
        } catch (e: Exception) {
            AppLog.put("保存正文失败 ${book.name} ${bookChapter.title}", e)
        }
    }

    fun saveText(book: Book, bookChapter: BookChapter, content: String) {
        if (content.isEmpty()) return
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName(),
        ).writeText(content)
        if (book.isOnLineTxt && ReadConfig.tocCountWords) {
            val wordCount = StringUtils.wordCountFormat(content.length)
            bookChapter.wordCount = wordCount
            appDb.bookChapterDao.update(bookChapter)
        }
    }

    fun hasContent(book: Book, bookChapter: BookChapter): Boolean {
        return downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        ).exists()
    }

    /**
     * 读取已缓存的章节正文，未缓存返回 null
     */
    fun getContent(book: Book, bookChapter: BookChapter): String? {
        val file = downloadDir.getFile(
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        )
        if (!file.exists()) return null
        return file.readText().takeIf { it.isNotEmpty() }
    }

    fun delContent(book: Book, bookChapter: BookChapter) {
        FileUtils.createFileIfNotExist(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName()
        ).delete()
    }

    fun getChapterFiles(book: Book): HashSet<String> {
        val fileNames = hashSetOf<String>()
        if (book.isLocalTxt) return fileNames
        FileUtils.createFolderIfNotExist(
            downloadDir,
            subDirs = arrayOf(cacheFolderName, book.getFolderName())
        ).list()?.let { fileNames.addAll(it) }
        return fileNames
    }

    /**
     * 设置是否禁用正文的去除重复标题，针对单个章节
     */
    fun setRemoveSameTitle(book: Book, bookChapter: BookChapter, removeSameTitle: Boolean) {
        val fileName = bookChapter.getFileName("nr")
        val contentProcessor = ContentProcessor.get(book)
        if (removeSameTitle) {
            val path =
                FileUtils.getPath(downloadDir, cacheFolderName, book.getFolderName(), fileName)
            contentProcessor.removeSameTitleCache.remove(fileName)
            File(path).delete()
        } else {
            FileUtils.createFileIfNotExist(
                downloadDir,
                cacheFolderName,
                book.getFolderName(),
                fileName
            )
            contentProcessor.removeSameTitleCache.add(fileName)
        }
    }

    fun removeSameTitle(book: Book, bookChapter: BookChapter): Boolean {
        val path = FileUtils.getPath(
            downloadDir,
            cacheFolderName,
            book.getFolderName(),
            bookChapter.getFileName("nr")
        )
        return !File(path).exists()
    }

    /**
     * 格式化书名
     */
    fun formatBookName(name: String): String {
        return name.replace(AppPattern.nameRegex, "").trim { it <= ' ' }
    }

    /**
     * 格式化作者
     */
    fun formatBookAuthor(author: String): String {
        return author.replace(AppPattern.authorRegex, "").trim { it <= ' ' }
    }

    private val jaccardSimilarity by lazy { JaccardSimilarity() }

    /**
     * 换源后根据章节名匹配新目录中的当前章节
     */
    fun getDurChapter(
        oldDurChapterIndex: Int,
        oldDurChapterName: String?,
        newChapterList: List<BookChapter>,
        oldChapterListSize: Int = 0
    ): Int {
        if (oldDurChapterIndex <= 0) return 0
        if (newChapterList.isEmpty()) return oldDurChapterIndex
        val oldChapterNum = getChapterNum(oldDurChapterName)
        val oldName = getPureChapterName(oldDurChapterName)
        val newChapterSize = newChapterList.size
        val durIndex =
            if (oldChapterListSize == 0) oldDurChapterIndex
            else oldDurChapterIndex * oldChapterListSize / newChapterSize
        val min = max(0, min(oldDurChapterIndex, durIndex) - 10)
        val max = min(newChapterSize - 1, max(oldDurChapterIndex, durIndex) + 10)
        var nameSim = 0.0
        var newIndex = 0
        var newNum = 0
        if (oldName.isNotEmpty()) {
            for (i in min..max) {
                val newName = getPureChapterName(newChapterList[i].title)
                val temp = jaccardSimilarity.apply(oldName, newName)
                if (temp > nameSim) {
                    nameSim = temp
                    newIndex = i
                }
            }
        }
        if (nameSim < 0.96 && oldChapterNum > 0) {
            for (i in min..max) {
                val temp = getChapterNum(newChapterList[i].title)
                if (temp == oldChapterNum) {
                    newNum = temp
                    newIndex = i
                    break
                } else if (abs(temp - oldChapterNum) < abs(newNum - oldChapterNum)) {
                    newNum = temp
                    newIndex = i
                }
            }
        }
        return if (nameSim > 0.96 || abs(newNum - oldChapterNum) < 1) {
            newIndex
        } else {
            min(max(0, newChapterList.size - 1), oldDurChapterIndex)
        }
    }

    fun getDurChapter(oldBook: Book, newChapterList: List<BookChapter>): Int {
        return oldBook.run {
            getDurChapter(durChapterIndex, durChapterTitle, newChapterList, totalChapterNum)
        }
    }

    private val chapterNamePattern1 by lazy {
        Pattern.compile(
            ".*?第([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话]"
        )
    }

    @Suppress("RegExpSimplifiable")
    private val chapterNamePattern2 by lazy {
        Pattern.compile(
            "^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*([\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、]|\\.[^\\d])"
        )
    }

    private val regexA by lazy { "\\s".toRegex() }

    private fun getChapterNum(chapterName: String?): Int {
        chapterName ?: return -1
        val chapterName1 = StringUtils.fullToHalf(chapterName).replace(regexA, "")
        return StringUtils.stringToInt(
            (
                chapterNamePattern1.matcher(chapterName1).takeIf { it.find() }
                    ?: chapterNamePattern2.matcher(chapterName1).takeIf { it.find() }
                )?.group(1) ?: "-1"
        )
    }

    private val regexOther by lazy {
        // 所有非字母数字中日韩文字 CJK区+扩展A-F区
        @Suppress("RegExpDuplicateCharacterInClass")
        "[^\\w\\u4E00-\\u9FEF〇\\u3400-\\u4DBF\\u20000-\\u2A6DF\\u2A700-\\u2EBEF]".toRegex()
    }

    @Suppress("RegExpUnnecessaryNonCapturingGroup", "RegExpSimplifiable")
    private val regexB by lazy {
        //章节序号，排除处于结尾的状况，避免将章节名替换为空字串
        "^.*?第(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)[章节篇回集话](?!$)|^(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+[,:、])*(?:[\\d零〇一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+)(?:[,:、](?!$)|\\.(?=[^\\d]))".toRegex()
    }

    private val regexC by lazy {
        //前后附加内容，整个章节名都在括号中时只剔除首尾括号，避免将章节名替换为空字串
        "(?!^)(?:[〖【《〔\\[{(][^〖【《〔\\[{()〕》》】〗\\]}]+)?[)〕》》】〗\\]}]$|^[〖【《〔\\[{(](?:[^〖【《〔\\[{()〕》》】〗\\]}]+[〕》》】〗\\]})])?(?!$)".toRegex()
    }

    private fun getPureChapterName(chapterName: String?): String {
        return if (chapterName == null) "" else StringUtils.fullToHalf(chapterName)
            .replace(regexA, "")
            .replace(regexB, "")
            .replace(regexC, "")
            .replace(regexOther, "")
    }
}