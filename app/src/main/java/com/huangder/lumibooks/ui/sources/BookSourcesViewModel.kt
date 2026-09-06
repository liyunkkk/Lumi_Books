package com.huangder.lumibooks.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.data.entities.BookSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookSourcesViewModel : ViewModel() {

    val sources: StateFlow<List<BookSource>> = BookSourceRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    fun importFromJson(json: String) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            BookSourceRepository.importFromJson(json)
                .onSuccess { _importResult.value = "成功导入 $it 个书源" }
                .onFailure { _importResult.value = "导入失败：${it.message}" }
            _importing.value = false
        }
    }

    fun setEnabled(source: BookSource, enabled: Boolean) {
        viewModelScope.launch { BookSourceRepository.setEnabled(source.bookSourceUrl, enabled) }
    }

    fun delete(source: BookSource) {
        viewModelScope.launch { BookSourceRepository.delete(source.bookSourceUrl) }
    }

    fun deleteAll() {
        viewModelScope.launch { BookSourceRepository.deleteAll() }
    }

    fun consumeResult() {
        _importResult.value = null
    }
}
