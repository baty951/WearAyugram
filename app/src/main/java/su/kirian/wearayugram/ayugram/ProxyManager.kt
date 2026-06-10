package su.kirian.wearayugram.ayugram

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.data.tdlib.TelegramClient

object ProxyManager {

    private const val CONNECT_TIMEOUT_MS = 5_000L

    /** Reads persisted config from DataStore and applies it (used at startup). */
    suspend fun applyProxy(client: TelegramClient) {
        val cfg = AyugramSettings.getProxyConfig()
        apply(client, cfg.enabled, cfg.host, cfg.port, cfg.secret)
    }

    /** Applies an explicit config directly — avoids DataStore read-after-write races. */
    fun apply(client: TelegramClient, enabled: Boolean, host: String, port: Int, secret: String) {
        if (!enabled || host.isBlank() || secret.isBlank()) {
            client.sendFire(TdApi.DisableProxy())
            return
        }
        val proxyType = TdApi.ProxyTypeMtproto(secret)
        val proxy     = TdApi.Proxy(host, port, proxyType)
        client.sendFire(TdApi.AddProxy(proxy, true, ""))
    }

    /**
     * Enables the configured proxy and waits up to [CONNECT_TIMEOUT_MS] for TDLib to
     * reach ConnectionStateReady. If the connection succeeds in time the proxy stays
     * enabled; otherwise it is disabled so TDLib falls back to a direct connection.
     * No-op if no proxy is configured.
     */
    suspend fun applyWithFallback(client: TelegramClient) {
        val cfg = AyugramSettings.getProxyConfig()
        if (cfg.host.isBlank() || cfg.secret.isBlank()) {
            client.sendFire(TdApi.DisableProxy())
            return
        }

        // Try the proxy first.
        apply(client, enabled = true, host = cfg.host, port = cfg.port, secret = cfg.secret)

        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            client.updatesOf<TdApi.UpdateConnectionState>()
                .first {
                    it.state is TdApi.ConnectionStateReady ||
                        it.state is TdApi.ConnectionStateUpdating
                }
            true
        } ?: false

        if (connected) {
            AyugramSettings.setProxyEnabled(true)
        } else {
            // Proxy didn't connect in time — fall back to direct.
            client.sendFire(TdApi.DisableProxy())
            AyugramSettings.setProxyEnabled(false)
        }
    }
}
