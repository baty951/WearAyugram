package su.kirian.wearayugram.presentation.chatlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgChat

class ChatListViewModel(app: Application) : AndroidViewModel(app) {

    private val chatRepo = (app as WearAyugramApp).chatRepository

    val chats: StateFlow<List<TgChat>> = chatRepo.chatList
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { chatRepo.loadChats(limit = 30) }
    }

    fun refresh() {
        viewModelScope.launch { chatRepo.loadChats(limit = 30) }
    }
}
