package com.huangder.lumibooks.util.parser

import android.content.Context
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.util.diagnostics.DiagnosticLevel
import com.huangder.lumibooks.util.diagnostics.DiagnosticLoggerRegistry

object BookParserFactory {
    fun createParser(format: BookFormat, context: Context, filePath: String? = null): BookParser {
        DiagnosticLoggerRegistry.logger?.log(
            category = "import",
            event = "parser_created",
            level = DiagnosticLevel.DEBUG,
            bookFormat = format.name
        )
        // 在线书源解析器优先分流（协议：legado-online://<bookUrl>）
        if (filePath != null && filePath.startsWith(OnlineBookParser.SCHEME)) {
            val engineBookUrl = filePath.removePrefix(OnlineBookParser.SCHEME)
            return OnlineBookParser(engineBookUrl)
        }

        return when (format) {
            BookFormat.EPUB -> EpubParser(context)
            BookFormat.PDF -> PdfParser(context)
            BookFormat.TXT -> TxtParser(context)
            BookFormat.MOBI -> MobiParser(context)
        }
    }
}
