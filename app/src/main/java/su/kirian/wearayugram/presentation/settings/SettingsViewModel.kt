package su.kirian.wearayugram.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.presentation.RestartActivity

class SettingsViewModel : ViewModel() {

    val ghostMode = AyugramSettings.ghostMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val antiRevoke = AyugramSettings.antiRevoke()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val deleteNotify = AyugramSettings.deleteNotify()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val localPremium = AyugramSettings.localPremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val photoAutoload = AyugramSettings.photoAutoload()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // null = idle, false = clearing, true = done (label feedback)
    private val _cacheCleared = MutableStateFlow<Boolean?>(null)
    val cacheCleared = _cacheCleared.asStateFlow()

    val activeAccount = AyugramSettings.activeAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val accountNames = AyugramSettings.accountNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), List(AyugramSettings.MAX_ACCOUNTS) { null })

    // true while switching/logging out — disables the account buttons.
    private val _accountBusy = MutableStateFlow(false)
    val accountBusy = _accountBusy.asStateFlow()

    fun switchAccount(slot: Int) {
        if (_accountBusy.value || slot == activeAccount.value) return
        _accountBusy.value = true
        viewModelScope.launch {
            AyugramSettings.setActiveAccount(slot)
            RestartActivity.restartApp(WearAyugramApp.instance)
        }
    }

    fun logout() {
        if (_accountBusy.value) return
        _accountBusy.value = true
        viewModelScope.launch {
            val slot = activeAccount.value
            AyugramSettings.clearAccountName(slot)
            // LogOut wipes this slot's TDLib database; give it a moment, then a
            // fresh process lands on the auth screen for the same slot.
            runCatching { TelegramClient.get().send(TdApi.LogOut()) }
            delay(1_500)
            RestartActivity.restartApp(WearAyugramApp.instance)
        }
    }

    fun setGhostMode(v: Boolean) = viewModelScope.launch { AyugramSettings.setGhostMode(v) }
    fun setAntiRevoke(v: Boolean) = viewModelScope.launch { AyugramSettings.setAntiRevoke(v) }
    fun setDeleteNotify(v: Boolean) = viewModelScope.launch { AyugramSettings.setDeleteNotify(v) }
    fun setLocalPremium(v: Boolean) = viewModelScope.launch { AyugramSettings.setLocalPremium(v) }
    fun setPhotoAutoload(v: Boolean) = viewModelScope.launch { AyugramSettings.setPhotoAutoload(v) }

    fun clearMediaCache() {
        if (_cacheCleared.value == false) return
        _cacheCleared.value = false
        viewModelScope.launch {
            // size=0 + ttl=0 + immunityDelay=0: delete every deletable cached file
            // right away. TDLib never touches files that are currently in use, and
            // empty fileTypes/chatIds means "all".
            runCatching {
                TelegramClient.get().send(
                    TdApi.OptimizeStorage(
                        0, 0, 0, 0,
                        emptyArray(), LongArray(0), LongArray(0),
                        false, 0
                    )
                )
            }
            _cacheCleared.value = true
        }
    }
}
