package su.kirian.wearayugram.presentation.proxy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.ayugram.ProxyManager

class ProxyViewModel(app: Application) : AndroidViewModel(app) {

    private val client = (app as WearAyugramApp).telegramClient

    val proxyEnabled: StateFlow<Boolean> = AyugramSettings.proxyEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val proxyHost: StateFlow<String> = AyugramSettings.proxyHost()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val proxyPort: StateFlow<Int> = AyugramSettings.proxyPort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 443)

    val proxySecret: StateFlow<String> = AyugramSettings.proxySecret()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setEnabled(v: Boolean) = viewModelScope.launch {
        AyugramSettings.setProxyEnabled(v)
        ProxyManager.applyProxy(client)
    }

    fun setHost(v: String) = viewModelScope.launch { AyugramSettings.setProxyHost(v) }

    fun setPort(v: String) = viewModelScope.launch {
        v.toIntOrNull()?.let { AyugramSettings.setProxyPort(it) }
    }

    fun setSecret(v: String) = viewModelScope.launch { AyugramSettings.setProxySecret(v) }

    fun applyAndEnable() = viewModelScope.launch {
        AyugramSettings.setProxyEnabled(true)
        val cfg = AyugramSettings.getProxyConfig()
        ProxyManager.apply(client, true, cfg.host, cfg.port, cfg.secret)
    }
}
