package com.huangder.lumibooks.ui.replace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.util.parser.PurifyEngine
import io.legado.app.data.appDb
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReplaceRulesViewModel : ViewModel() {

    val searchQuery = MutableStateFlow("")

    private val rawRules = appDb.replaceRuleDao.flowAll()

    val filteredRules: StateFlow<List<ReplaceRule>> = combine(rawRules, searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.name.lowercase().contains(q) ||
                it.pattern.lowercase().contains(q) ||
                (it.scope?.lowercase()?.contains(q) == true) ||
                (it.group?.lowercase()?.contains(q) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleRule(rule: ReplaceRule) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = rule.copy(isEnabled = !rule.isEnabled)
            appDb.replaceRuleDao.update(updated)
        }
    }

    fun saveRule(rule: ReplaceRule) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.replaceRuleDao.insert(rule)
        }
    }

    fun deleteRule(rule: ReplaceRule) {
        viewModelScope.launch(Dispatchers.IO) {
            appDb.replaceRuleDao.delete(rule)
        }
    }

    fun loadPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            val presets = PurifyEngine.getPresetRules()
            presets.forEach {
                appDb.replaceRuleDao.insert(it)
            }
        }
    }

    suspend fun importRulesJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = json.trim()
            val importedList = if (trimmed.startsWith("[")) {
                GSON.fromJsonArray<ReplaceRule>(trimmed).getOrThrow()
            } else {
                listOf(GSON.fromJsonObject<ReplaceRule>(trimmed).getOrThrow())
            }
            val validRules = importedList.filter { it.pattern.isNotBlank() }
            if (validRules.isNotEmpty()) {
                appDb.replaceRuleDao.insert(*validRules.toTypedArray())
            }
            validRules.size
        }
    }

    suspend fun exportRulesJson(): String = withContext(Dispatchers.IO) {
        val all = appDb.replaceRuleDao.all()
        GSON.toJson(all)
    }
}
