package su.kirian.wearayugram.presentation.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.ayugram.ProxyManager

class ProxyConfigReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val host   = intent.getStringExtra("host") ?: return
        val port   = intent.getIntExtra("port", 443)
        val secret = intent.getStringExtra("secret") ?: return
        val app = context.applicationContext as WearAyugramApp
        // Apply immediately from the intent extras (no DataStore round-trip),
        // then persist for subsequent launches.
        ProxyManager.apply(app.telegramClient, true, host, port, secret)
        CoroutineScope(Dispatchers.IO).launch {
            AyugramSettings.setProxyHost(host)
            AyugramSettings.setProxyPort(port)
            AyugramSettings.setProxySecret(secret)
            AyugramSettings.setProxyEnabled(true)
        }
    }
    companion object {
        const val ACTION = "su.kirian.wearayugram.SET_PROXY"
    }
}
