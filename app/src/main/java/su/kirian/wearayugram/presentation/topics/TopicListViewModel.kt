package su.kirian.wearayugram.presentation.topics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgTopic

class TopicListViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val chatId: Long = checkNotNull(savedState["chatId"])

    private val chatRepo = (app as WearAyugramApp).chatRepository

    private val _topics = MutableStateFlow<List<TgTopic>>(emptyList())
    val topics: StateFlow<List<TgTopic>> = _topics.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // Group title for the screen header; falls back to a generic label until loaded.
    private val _chatTitle = MutableStateFlow("Темы")
    val chatTitle: StateFlow<String> = _chatTitle.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepo.getChatById(chatId)?.title?.takeIf { it.isNotBlank() }?.let {
                _chatTitle.value = it
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _topics.value = chatRepo.getTopics(chatId)
            _loading.value = false
        }
    }
}
