package com.huangder.lumibooks.ui.sources

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 书源管理页 — 承载引擎书源的导入/启停/删除。
 */
@AndroidEntryPoint
class BookSourcesActivity : ComponentActivity() {

    private var systemDarkMode by mutableStateOf(false)

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        systemDarkMode = resources.configuration.isNightModeEnabled()
        val launchTheme = LaunchThemeController.themeSnapshot(this)
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
            val appAccentColor by dataStoreManager.appAccentColor
                .collectAsState(initial = launchTheme.appAccentColor)
            val eInkMode by dataStoreManager.eInkMode
                .collectAsState(initial = launchTheme.eInkMode)

            EBookReaderTheme(
                darkTheme = isDark,
                appTheme = appTheme,
                appAccentColor = appAccentColor,
                eInkMode = eInkMode
            ) {
                BookSourcesScreen(onNavigateBack = { finish() })
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemDarkMode = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun Configuration.isNightModeEnabled(): Boolean {
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}
