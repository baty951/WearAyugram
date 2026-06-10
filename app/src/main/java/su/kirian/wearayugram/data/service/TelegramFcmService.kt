package su.kirian.wearayugram.data.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.drinkless.tdlib.TdApi
import org.json.JSONObject
import su.kirian.wearayugram.WearAyugramApp

class TelegramFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Routed through the app so registration waits for AuthState.Ready —
        // RegisterDevice on an unauthorized client fails with 400.
        (application as WearAyugramApp).registerFcmToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val app = application as WearAyugramApp

        // Hand the push payload to TDLib so it fetches the new message and emits
        // updates (which our notification pipeline turns into a notification).
        // TDLib expects the full JSON payload with google.sent_time added.
        if (message.data.isNotEmpty()) {
            val payload = JSONObject(message.data as Map<*, *>).apply {
                put("google.sent_time", message.sentTime)
            }.toString()
            app.telegramClient.sendFire(TdApi.ProcessPushNotification(payload))
        } else {
            Log.w(TAG, "FCM message with empty data payload")
        }

        // Keep the connection alive long enough to fetch and show the message.
        runCatching { startForegroundService(Intent(this, TelegramForegroundService::class.java)) }
    }

    companion object {
        private const val TAG = "WearAyugram"
    }
}
