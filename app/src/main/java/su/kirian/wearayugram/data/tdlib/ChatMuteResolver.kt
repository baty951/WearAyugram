package su.kirian.wearayugram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.drinkless.tdlib.TdApi

/**
 * Resolves whether a chat is muted. A chat with useDefaultMuteFor=true follows
 * its category default (private/group/channel scope) — the common way phones
 * mute "all channels" — so the verdict needs both the per-chat settings and the
 * scope settings. Scope settings are fetched lazily and kept live by
 * UpdateScopeNotificationSettings.
 */
class ChatMuteResolver(private val client: TelegramClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // scope constructor -> muted
    private val scopeMuted = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    init {
        client.updatesOf<TdApi.UpdateScopeNotificationSettings>()
            .onEach { scopeMuted[it.scope.constructor] = it.notificationSettings.muteFor > 0 }
            .launchIn(scope)
    }

    fun scopeOf(type: TdApi.ChatType): TdApi.NotificationSettingsScope = when (type) {
        is TdApi.ChatTypeSupergroup ->
            if (type.isChannel) TdApi.NotificationSettingsScopeChannelChats()
            else TdApi.NotificationSettingsScopeGroupChats()
        is TdApi.ChatTypeBasicGroup -> TdApi.NotificationSettingsScopeGroupChats()
        else -> TdApi.NotificationSettingsScopePrivateChats()
    }

    suspend fun isScopeMuted(scopeConstructor: Int): Boolean =
        scopeMuted[scopeConstructor] ?: runCatching {
            val tdScope = when (scopeConstructor) {
                TdApi.NotificationSettingsScopeChannelChats.CONSTRUCTOR ->
                    TdApi.NotificationSettingsScopeChannelChats()
                TdApi.NotificationSettingsScopeGroupChats.CONSTRUCTOR ->
                    TdApi.NotificationSettingsScopeGroupChats()
                else -> TdApi.NotificationSettingsScopePrivateChats()
            }
            client.send(TdApi.GetScopeNotificationSettings(tdScope)).muteFor > 0
            // Cache only successful answers: a failed query (e.g. fired before auth
            // completes) must not pin the scope as unmuted until the next update.
        }.onSuccess { scopeMuted[scopeConstructor] = it }.getOrDefault(false)

    suspend fun isMuted(settings: TdApi.ChatNotificationSettings, scopeConstructor: Int): Boolean =
        if (!settings.useDefaultMuteFor) settings.muteFor > 0
        else isScopeMuted(scopeConstructor)

    suspend fun isMuted(chat: TdApi.Chat): Boolean =
        isMuted(chat.notificationSettings, scopeOf(chat.type).constructor)
}
