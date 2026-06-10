package su.kirian.wearayugram.presentation.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgMessage

class ChatViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val chatId: Long = checkNotNull(savedState["chatId"])

    private val msgRepo = (app as WearAyugramApp).messageRepository

    val messages: StateFlow<List<TgMessage>> = msgRepo.messages(chatId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        msgRepo.setOpenChat(chatId)
        viewModelScope.launch { msgRepo.loadHistory(chatId) }
    }

    override fun onCleared() {
        super.onCleared()
        msgRepo.setOpenChat(-1L)
    }

    fun loadOlder() {
        val oldest = messages.value.firstOrNull()?.id ?: return
        viewModelScope.launch { msgRepo.loadHistory(chatId, fromMessageId = oldest) }
    }

    fun sendText(text: String) {
        viewModelScope.launch { msgRepo.sendText(chatId, text) }
    }

    fun sendVoice(filePath: String, durationSeconds: Int) {
        viewModelScope.launch { msgRepo.sendVoice(chatId, filePath, durationSeconds) }
    }

    fun markRead(messageIds: LongArray) {
        viewModelScope.launch { msgRepo.markAsRead(chatId, messageIds) }
    }
}
