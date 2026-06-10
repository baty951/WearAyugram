package su.kirian.wearayugram.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.BuildConfig
import su.kirian.wearayugram.Constants
import su.kirian.wearayugram.ayugram.ProxyManager
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.domain.model.AuthState
import su.kirian.wearayugram.domain.repository.AuthRepository

class AuthRepositoryImpl(
    private val client: TelegramClient,
    private val filesDir: String,
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Hot StateFlow that always holds the latest auth state. The repo is created
    // very early (Application.onCreate), so this collector captures every
    // UpdateAuthorizationState — including the Ready that fires immediately for an
    // already-logged-in user, which a late UI subscriber would otherwise miss
    // because the shared updates flow only replays the single most recent object.
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        client.updatesOf<TdApi.UpdateAuthorizationState>()
            .onEach { update ->
                _authState.value = update.authorizationState.toAuthState()
                if (update.authorizationState is TdApi.AuthorizationStateWaitTdlibParameters) {
                    sendTdlibParameters()
                    // Try the proxy with a 5s connect-or-fallback check, off the
                    // auth collector so it doesn't stall later auth-state updates.
                    scope.launch { ProxyManager.applyWithFallback(client) }
                }
            }
            .launchIn(scope)
    }

    private suspend fun sendTdlibParameters() {
        client.send(
            TdApi.SetTdlibParameters().apply {
                databaseDirectory = filesDir
                filesDirectory = filesDir
                useMessageDatabase = true
                useSecretChats = false
                apiId = Constants.API_ID
                apiHash = Constants.API_HASH
                systemLanguageCode = "ru"
                deviceModel = "WearOS Watch"
                systemVersion = "WearOS"
                applicationVersion = BuildConfig.VERSION_NAME
            }
        )
    }

    override suspend fun requestQrLogin(): Flow<String> =
        client.updatesOf<TdApi.UpdateAuthorizationState>()
            .filter { it.authorizationState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation }
            .map { (it.authorizationState as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link }
            .onStart {
                // Empty array = don't exclude other sessions from the QR link
                client.send(TdApi.RequestQrCodeAuthentication(LongArray(0)))
            }

    override suspend fun sendPhoneNumber(phone: String) {
        client.send(TdApi.SetAuthenticationPhoneNumber(phone, null))
    }

    override suspend fun sendCode(code: String) {
        client.send(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun sendPassword(password: String) {
        client.send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        client.send(TdApi.LogOut())
    }
}

private fun TdApi.AuthorizationState.toAuthState(): AuthState = when (this) {
    is TdApi.AuthorizationStateWaitTdlibParameters -> AuthState.Loading
    is TdApi.AuthorizationStateWaitPhoneNumber -> AuthState.WaitPhone
    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> AuthState.WaitQr(link)
    is TdApi.AuthorizationStateWaitCode -> AuthState.WaitCode
    is TdApi.AuthorizationStateWaitPassword -> AuthState.WaitPassword
    is TdApi.AuthorizationStateReady -> AuthState.Ready
    else -> AuthState.Loading
}
