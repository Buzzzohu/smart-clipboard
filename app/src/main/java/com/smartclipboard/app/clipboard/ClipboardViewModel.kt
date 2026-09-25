package com.smartclipboard.app.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartclipboard.app.data.ClipboardDatabase
import com.smartclipboard.app.data.ClipboardItem
import com.smartclipboard.app.data.ClipboardRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Coordinates foreground reads, duplicate checks, and save confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = ClipboardTextReader(application)
    private val repository = ClipboardRepository(
        ClipboardDatabase.getInstance(application).clipboardItemDao()
    )
    private val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mutableState = MutableStateFlow<ClipboardStatus>(ClipboardStatus.Empty)
    val state = mutableState.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query = mutableQuery.asStateFlow()
    val items = mutableQuery.flatMapLatest(repository::observeSearch)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableMessages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = 1)
    val messages = mutableMessages.asSharedFlow()

    private var lastHandledContent: String? = null
    private var readVersion = 0

    init {
        viewModelScope.launch {
            // Existing rows remain readable even if a metadata update fails.
            runCatching { repository.reclassifyExisting() }
        }
    }

    fun refresh() {
        if ((mutableState.value as? ClipboardStatus.Candidate)?.isSaving == true) return
        val version = ++readVersion
        when (val result = ClipboardImportPolicy.evaluate(reader.read())) {
            ClipboardImportResult.Empty -> mutableState.value = ClipboardStatus.Empty
            ClipboardImportResult.Ignored -> mutableState.value = ClipboardStatus.SkippedByPolicy
            is ClipboardImportResult.Candidate -> viewModelScope.launch {
                try {
                    val exists = repository.contains(result.content)
                    if (version != readVersion) return@launch
                    mutableState.value = when {
                        exists -> ClipboardStatus.AlreadySaved(result.content)
                        lastHandledContent == result.content -> ClipboardStatus.IgnoredByUser(result.content)
                        else -> ClipboardStatus.Candidate(result.content)
                    }
                } catch (_: Exception) {
                    if (version == readVersion) mutableState.value = ClipboardStatus.ReadFailed
                }
            }
        }
    }

    fun setQuery(value: String) {
        mutableQuery.value = value
    }

    fun ignore() {
        val candidate = mutableState.value as? ClipboardStatus.Candidate ?: return
        lastHandledContent = candidate.content
        mutableState.value = ClipboardStatus.IgnoredByUser(candidate.content)
    }

    fun save() {
        val candidate = mutableState.value as? ClipboardStatus.Candidate ?: return
        if (candidate.isSaving) return
        val content = candidate.content
        val version = ++readVersion
        mutableState.value = candidate.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val inserted = repository.save(content)
                lastHandledContent = content
                if (version == readVersion) {
                    mutableState.value = if (inserted) {
                        ClipboardStatus.Saved(content)
                    } else {
                        ClipboardStatus.AlreadySaved(content)
                    }
                }
            } catch (_: Exception) {
                if (version == readVersion) mutableState.value = ClipboardStatus.SaveFailed(content)
            }
        }
    }

    /** Manual entry intentionally bypasses the automatic letters/digits filter. */
    fun addManual(content: String, tags: String) {
        viewModelScope.launch {
            val message = try {
                if (repository.save(content, tags)) LibraryMessage.Saved
                else LibraryMessage.Duplicate
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun importBatch(raw: String) {
        viewModelScope.launch {
            val message = try {
                val result = repository.importBatch(raw)
                LibraryMessage.BatchImported(result.added, result.skipped)
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun edit(id: Long, content: String, tags: String) {
        viewModelScope.launch {
            val message = try {
                if (repository.edit(id, content, tags)) LibraryMessage.Edited
                else LibraryMessage.DuplicateOrMissing
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val message = try {
                if (repository.delete(id)) LibraryMessage.Deleted else LibraryMessage.Failed
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            try {
                if (!repository.toggleFavorite(id)) mutableMessages.emit(LibraryMessage.Failed)
            } catch (_: Exception) {
                mutableMessages.emit(LibraryMessage.Failed)
            }
        }
    }

    fun copy(item: ClipboardItem) {
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText("", item.content))
        } catch (_: Exception) {
            mutableMessages.tryEmit(LibraryMessage.Failed)
            return
        }
        viewModelScope.launch {
            try {
                repository.recordUse(item.id)
            } catch (_: Exception) {
                // The system clipboard already contains the requested text.
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                mutableMessages.emit(LibraryMessage.Copied)
            }
        }
    }
}

sealed interface LibraryMessage {
    data object Saved : LibraryMessage
    data object Duplicate : LibraryMessage
    data object DuplicateOrMissing : LibraryMessage
    data object Edited : LibraryMessage
    data object Deleted : LibraryMessage
    data object Copied : LibraryMessage
    data object Failed : LibraryMessage
    data class BatchImported(val added: Int, val skipped: Int) : LibraryMessage
}

sealed interface ClipboardStatus {
    data object Empty : ClipboardStatus
    data object SkippedByPolicy : ClipboardStatus
    data object ReadFailed : ClipboardStatus
    data class Candidate(val content: String, val isSaving: Boolean = false) : ClipboardStatus
    data class AlreadySaved(val content: String) : ClipboardStatus
    data class IgnoredByUser(val content: String) : ClipboardStatus
    data class Saved(val content: String) : ClipboardStatus
    data class SaveFailed(val content: String) : ClipboardStatus
}
