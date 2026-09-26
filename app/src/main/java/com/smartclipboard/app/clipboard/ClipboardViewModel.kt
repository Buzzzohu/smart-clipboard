package com.smartclipboard.app.clipboard

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartclipboard.app.R
import com.smartclipboard.app.data.ClipboardDatabase
import com.smartclipboard.app.data.ClipboardItem
import com.smartclipboard.app.data.ClipboardRepository
import com.smartclipboard.app.data.ImportSettings
import com.smartclipboard.app.data.ImportTextCleaner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Coordinates foreground reads, duplicate checks, and save confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClipboardViewModel(application: Application) : AndroidViewModel(application) {
    private val reader = ClipboardTextReader(application)
    private val repository = ClipboardRepository(
        ClipboardDatabase.getInstance(application)
    )
    val categories = repository.categoryList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    suspend fun saveCategory(id: Long?, name: String, icon: String?) = repository.saveCategory(id, name, icon)
    suspend fun deleteCategory(id: Long) { repository.deleteCategory(id); mutableCategory.value = null }
    suspend fun deleteItems(ids: Set<Long>): Boolean = try {
        repository.deleteItems(ids)
        mutableMessages.emit(LibraryMessage.Deleted)
        true
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
      catch (_: Exception) { mutableMessages.emit(LibraryMessage.Failed); false }
    fun moveItems(ids: Set<Long>, categoryId: Long) {
        viewModelScope.launch {
            try { repository.moveItems(ids, categoryId); mutableMessages.emit(LibraryMessage.Edited) }
            catch (_: Exception) { mutableMessages.emit(LibraryMessage.Failed) }
        }
    }
    private val clipboard = application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mutableState = MutableStateFlow<ClipboardStatus>(ClipboardStatus.Empty)
    val state = mutableState.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query = mutableQuery.asStateFlow()
    private val mutableCategory = MutableStateFlow<String?>(null)
    val category = mutableCategory.asStateFlow()
    private val mutableFavoritesOnly = MutableStateFlow(false)
    val favoritesOnly = mutableFavoritesOnly.asStateFlow()
    val items = combine(mutableQuery, mutableCategory, mutableFavoritesOnly) { query, category, favorites ->
        Triple(query, category, favorites)
    }.flatMapLatest { (query, category, favorites) ->
        repository.observeSearch(query, category, favorites)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val mutableMessages = MutableSharedFlow<LibraryMessage>(extraBufferCapacity = 1)
    val messages = mutableMessages.asSharedFlow()
    private val mutableNotices = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val notices = mutableNotices.asSharedFlow()

    private var lastHandledContent: String? = null
    private var readVersion = 0

    fun refresh(passive: Boolean = false) {
        if ((mutableState.value as? ClipboardStatus.Candidate)?.isSaving == true) return
        val version = ++readVersion
        val prepared = reader.read()?.let { ImportTextCleaner.clean(it, ImportSettings.stripSender(getApplication())) }
        when (val result = ClipboardImportPolicy.evaluate(prepared)) {
            // An empty clipboard is normal on app launch; keep the library quiet.
            ClipboardImportResult.Empty -> mutableState.value = ClipboardStatus.Empty
            ClipboardImportResult.Ignored -> {
                if (passive) mutableState.value = ClipboardStatus.SkippedByPolicy
                else updateStatus(ClipboardStatus.SkippedByPolicy, R.string.clipboard_ignored_by_rule)
            }
            is ClipboardImportResult.Candidate -> viewModelScope.launch {
                try {
                    val exists = repository.contains(result.content)
                    if (version != readVersion) return@launch
                    when {
                        exists -> {
                            if (passive) mutableState.value = ClipboardStatus.AlreadySaved(result.content)
                            else updateStatus(ClipboardStatus.AlreadySaved(result.content), R.string.clipboard_already_saved)
                        }
                        lastHandledContent == result.content -> mutableState.value = ClipboardStatus.IgnoredByUser(result.content)
                        else -> mutableState.value = ClipboardStatus.Candidate(result.content)
                    }
                } catch (_: Exception) {
                    if (version == readVersion) updateStatus(ClipboardStatus.ReadFailed, R.string.clipboard_read_failed)
                }
            }
        }
    }

    fun setQuery(value: String) {
        mutableQuery.value = value
    }

    fun setCategory(value: String?) {
        mutableCategory.value = value
    }

    fun setFavoritesOnly(value: Boolean) {
        mutableFavoritesOnly.value = value
    }

    private fun updateStatus(status: ClipboardStatus, noticeRes: Int) {
        mutableState.value = status
        mutableNotices.tryEmit(noticeRes)
    }

    fun ignore() {
        val candidate = mutableState.value as? ClipboardStatus.Candidate ?: return
        lastHandledContent = candidate.content
        updateStatus(ClipboardStatus.IgnoredByUser(candidate.content), R.string.clipboard_ignored_by_user)
    }

    fun save(categoryId: Long = 1) {
        val candidate = mutableState.value as? ClipboardStatus.Candidate ?: return
        if (candidate.isSaving) return
        val content = candidate.content
        val version = ++readVersion
        mutableState.value = candidate.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val inserted = repository.save(content, categoryId = categoryId)
                lastHandledContent = content
                if (version == readVersion) {
                    if (inserted) updateStatus(ClipboardStatus.Saved(content), R.string.clipboard_saved)
                    else updateStatus(ClipboardStatus.AlreadySaved(content), R.string.clipboard_already_saved)
                }
            } catch (_: Exception) {
                if (version == readVersion) updateStatus(ClipboardStatus.SaveFailed(content), R.string.clipboard_save_failed)
            }
        }
    }

    /** Manual entry intentionally bypasses the automatic letters/digits filter. */
    fun addManual(content: String, tags: String, categoryId: Long = 1) {
        val prepared = ImportTextCleaner.clean(content, ImportSettings.stripSender(getApplication()))
        viewModelScope.launch {
            val message = try {
                if (repository.save(prepared, tags, categoryId)) LibraryMessage.Saved
                else LibraryMessage.Duplicate
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun importBatch(raw: String, categoryId: Long = 1) {
        val stripSender = ImportSettings.stripSender(getApplication())
        viewModelScope.launch {
            val message = try {
                val result = repository.importBatch(raw, stripSender, categoryId)
                LibraryMessage.BatchImported(result.added, result.skipped)
            } catch (_: Exception) {
                LibraryMessage.Failed
            }
            mutableMessages.emit(message)
        }
    }

    fun edit(id: Long, content: String, tags: String, categoryId: Long = 1) {
        viewModelScope.launch {
            val message = try {
                if (repository.edit(id, content, tags, categoryId)) LibraryMessage.Edited
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
