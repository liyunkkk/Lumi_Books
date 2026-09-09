package com.huangder.lumibooks.ui.replace

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.LaunchThemeController
import com.huangder.lumibooks.util.LocaleHelper
import com.huangder.lumibooks.util.isNightModeEnabled
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ReplaceRulesActivity : ComponentActivity() {

    companion object {
        const val EXTRA_BOOK_NAME = "extra_book_name"
        const val EXTRA_PATTERN = "extra_pattern"

        fun start(context: Context, bookName: String? = null, pattern: String? = null) {
            val intent = Intent(context, ReplaceRulesActivity::class.java).apply {
                bookName?.let { putExtra(EXTRA_BOOK_NAME, it) }
                pattern?.let { putExtra(EXTRA_PATTERN, it) }
            }
            context.startActivity(intent)
        }
    }

    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val launchTheme = LaunchThemeController.themeSnapshot(this)

        val bookName = intent.getStringExtra(EXTRA_BOOK_NAME)
        val pattern = intent.getStringExtra(EXTRA_PATTERN)

        setContent {
            val darkMode by dataStoreManager.darkMode
                .collectAsState(initial = launchTheme.darkMode)
            val isDark = when (darkMode) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val appTheme by dataStoreManager.appTheme
                .collectAsState(initial = launchTheme.appTheme)
            val eInkMode by dataStoreManager.eInkMode
                .collectAsState(initial = launchTheme.eInkMode)

            EBookReaderTheme(darkTheme = isDark, appTheme = appTheme, eInkMode = eInkMode) {
                ReplaceRulesScreen(
                    initialBookName = bookName,
                    initialPattern = pattern,
                    onBack = { finish() }
                )
            }
        }
    }
}
