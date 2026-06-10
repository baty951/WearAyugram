package su.kirian.wearayugram.ayugram

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.ayugramDataStore by preferencesDataStore(name = "ayugram_settings")

object AyugramSettings {
    val KEY_GHOST_MODE    = booleanPreferencesKey("ghost_mode")
    val KEY_ANTI_REVOKE   = booleanPreferencesKey("anti_revoke")
    val KEY_DELETE_NOTIFY = booleanPreferencesKey("delete_notify")
    val KEY_LOCAL_PREMIUM = booleanPreferencesKey("local_premium")

    val KEY_PHOTO_AUTOLOAD = booleanPreferencesKey("photo_autoload")

    val KEY_PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
    val KEY_PROXY_HOST    = stringPreferencesKey("proxy_host")
    val KEY_PROXY_PORT    = intPreferencesKey("proxy_port")
    val KEY_PROXY_SECRET  = stringPreferencesKey("proxy_secret")

    private lateinit var context: Context

    // Until the user touches the toggle, photo auto-download follows the hardware:
    // low-RAM watches decode and scroll photos poorly, so they default to tap-to-load.
    private var photoAutoloadDefault = true

    // Weak watches also skip the fullscreen-size ("x") photo variant.
    var isLowRamDevice = false
        private set

    fun init(ctx: Context) {
        context = ctx.applicationContext
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isLowRamDevice = am.isLowRamDevice || am.memoryClass < 96
        photoAutoloadDefault = !isLowRamDevice
    }

    fun ghostMode(): Flow<Boolean>    = context.ayugramDataStore.data.map { it[KEY_GHOST_MODE] ?: false }
    fun antiRevoke(): Flow<Boolean>   = context.ayugramDataStore.data.map { it[KEY_ANTI_REVOKE] ?: true }
    fun deleteNotify(): Flow<Boolean> = context.ayugramDataStore.data.map { it[KEY_DELETE_NOTIFY] ?: true }
    fun localPremium(): Flow<Boolean> = context.ayugramDataStore.data.map { it[KEY_LOCAL_PREMIUM] ?: false }
    fun photoAutoload(): Flow<Boolean> = context.ayugramDataStore.data.map { it[KEY_PHOTO_AUTOLOAD] ?: photoAutoloadDefault }

    fun proxyEnabled(): Flow<Boolean> = context.ayugramDataStore.data.map { it[KEY_PROXY_ENABLED] ?: false }
    fun proxyHost(): Flow<String>     = context.ayugramDataStore.data.map { it[KEY_PROXY_HOST] ?: "" }
    fun proxyPort(): Flow<Int>        = context.ayugramDataStore.data.map { it[KEY_PROXY_PORT] ?: 443 }
    fun proxySecret(): Flow<String>   = context.ayugramDataStore.data.map { it[KEY_PROXY_SECRET] ?: "" }

    suspend fun setGhostMode(v: Boolean)    = context.ayugramDataStore.edit { it[KEY_GHOST_MODE] = v }
    suspend fun setAntiRevoke(v: Boolean)   = context.ayugramDataStore.edit { it[KEY_ANTI_REVOKE] = v }
    suspend fun setDeleteNotify(v: Boolean) = context.ayugramDataStore.edit { it[KEY_DELETE_NOTIFY] = v }
    suspend fun setLocalPremium(v: Boolean) {
        LocalPremiumPatcher.isLocalPremiumEnabled = v
        context.ayugramDataStore.edit { it[KEY_LOCAL_PREMIUM] = v }
    }

    suspend fun setPhotoAutoload(v: Boolean) = context.ayugramDataStore.edit { it[KEY_PHOTO_AUTOLOAD] = v }

    suspend fun setProxyEnabled(v: Boolean) = context.ayugramDataStore.edit { it[KEY_PROXY_ENABLED] = v }
    suspend fun setProxyHost(v: String)     = context.ayugramDataStore.edit { it[KEY_PROXY_HOST] = v }
    suspend fun setProxyPort(v: Int)        = context.ayugramDataStore.edit { it[KEY_PROXY_PORT] = v }
    suspend fun setProxySecret(v: String)   = context.ayugramDataStore.edit { it[KEY_PROXY_SECRET] = v }

    data class ProxyConfig(val enabled: Boolean, val host: String, val port: Int, val secret: String)

    suspend fun getProxyConfig(): ProxyConfig {
        val prefs = context.ayugramDataStore.data.first()
        return ProxyConfig(
            enabled = prefs[KEY_PROXY_ENABLED] ?: false,
            host    = prefs[KEY_PROXY_HOST] ?: "",
            port    = prefs[KEY_PROXY_PORT] ?: 443,
            secret  = prefs[KEY_PROXY_SECRET] ?: ""
        )
    }
}
