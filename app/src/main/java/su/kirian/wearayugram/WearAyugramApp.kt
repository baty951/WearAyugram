package su.kirian.wearayugram

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.util.Log
import androidx.room.Room
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.domain.model.AuthState
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.data.local.AppDatabase
import su.kirian.wearayugram.data.repository.AuthRepositoryImpl
import su.kirian.wearayugram.data.repository.ChatRepositoryImpl
import su.kirian.wearayugram.data.repository.MessageRepositoryImpl
import su.kirian.wearayugram.data.service.TelegramForegroundService
import su.kirian.wearayugram.data.tdlib.FileDownloader
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.domain.repository.AuthRepository
import su.kirian.wearayugram.domain.repository.ChatRepository
import su.kirian.wearayugram.domain.repository.MessageRepository

class WearAyugramApp : Application() {

    lateinit var telegramClient: TelegramClient
        private set
    lateinit var fileDownloader: FileDownloader
        private set
    lateinit var authRepository: AuthRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set
    lateinit var messageRepository: MessageRepository
        private set
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        AyugramSettings.init(this)

        database = Room.databaseBuilder(this, AppDatabase::class.java, "wearayugram.db").build()

        val dbDir = getDir(Constants.TDLIB_DB_DIR, MODE_PRIVATE).absolutePath
        telegramClient = TelegramClient.init(dbDir)
        // AuthRepositoryImpl must be created immediately after TelegramClient so it
        // catches the very first WaitTdlibParameters event and sends SetTdlibParameters
        authRepository = AuthRepositoryImpl(telegramClient, dbDir)
        chatRepository = ChatRepositoryImpl(telegramClient)
        fileDownloader = FileDownloader(telegramClient)
        messageRepository = MessageRepositoryImpl(telegramClient, applicationContext, database, fileDownloader)

        createNotificationChannels()
        startForegroundService(Intent(this, TelegramForegroundService::class.java))
        registerFcmToken()
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Registers the FCM token with TDLib. RegisterDevice requires an authorized
     * client — sending it during startup raced the TDLib handshake and failed with
     * 400 "call setTdlibParameters first", so we wait for AuthState.Ready first.
     * [explicitToken] is passed by TelegramFcmService.onNewToken on token rotation.
     */
    fun registerFcmToken(explicitToken: String? = null) {
        appScope.launch {
            authRepository.authState.first { it is AuthState.Ready }
            if (explicitToken != null) {
                sendRegisterDevice(explicitToken)
                return@launch
            }
            // GMS on the watch often returns transient SERVICE_NOT_AVAILABLE right
            // after launch — retry with growing backoff.
            for (attempt in 1..5) {
                val token = fetchFcmToken()
                if (token != null) {
                    sendRegisterDevice(token)
                    return@launch
                }
                Log.w(TAG, "FCM token fetch failed (attempt $attempt), retrying")
                kotlinx.coroutines.delay(10_000L * attempt)
            }
            Log.w(TAG, "FCM token fetch gave up after 5 attempts")
        }
    }

    private suspend fun fetchFcmToken(): String? =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it, null) }
                .addOnFailureListener { cont.resume(null, null) }
        }

    private fun sendRegisterDevice(token: String) {
        appScope.launch {
            val request = TdApi.RegisterDevice().apply {
                deviceToken = TdApi.DeviceTokenFirebaseCloudMessaging(token, true)
                otherUserIds = LongArray(0)
            }
            runCatching { telegramClient.send<TdApi.PushReceiverId>(request) }
                .onSuccess { Log.i(TAG, "FCM token registered with TDLib") }
                .onFailure { Log.w(TAG, "FCM RegisterDevice failed: ${it.message}") }
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    Constants.CHANNEL_MESSAGES,
                    "Сообщения",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                NotificationChannel(
                    Constants.CHANNEL_DELETE_NOTIFY,
                    "Удалённые сообщения",
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    Constants.CHANNEL_SERVICE,
                    "Фоновая служба",
                    NotificationManager.IMPORTANCE_MIN
                ),
            )
        )
    }

    companion object {
        private const val TAG = "WearAyugram"
        lateinit var instance: WearAyugramApp
            private set
    }
}
