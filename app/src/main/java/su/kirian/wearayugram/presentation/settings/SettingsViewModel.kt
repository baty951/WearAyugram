package su.kirian.wearayugram.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import su.kirian.wearayugram.ayugram.AyugramSettings

class SettingsViewModel : ViewModel() {

    val ghostMode = AyugramSettings.ghostMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val antiRevoke = AyugramSettings.antiRevoke()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val deleteNotify = AyugramSettings.deleteNotify()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val localPremium = AyugramSettings.localPremium()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGhostMode(v: Boolean) = viewModelScope.launch { AyugramSettings.setGhostMode(v) }
    fun setAntiRevoke(v: Boolean) = viewModelScope.launch { AyugramSettings.setAntiRevoke(v) }
    fun setDeleteNotify(v: Boolean) = viewModelScope.launch { AyugramSettings.setDeleteNotify(v) }
    fun setLocalPremium(v: Boolean) = viewModelScope.launch { AyugramSettings.setLocalPremium(v) }
}
