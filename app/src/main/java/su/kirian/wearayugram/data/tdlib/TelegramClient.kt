package su.kirian.wearayugram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TelegramClient private constructor(private val filesDir: String) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // replay=1 ensures late subscribers (e.g. AuthRepository) don't miss the first WaitTdlibParameters event
    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 1,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    private val client: Client = Client.create(
        { update -> scope.launch { _updates.emit(update) } },
        null,
        null
    )

    init {
        // Keep at 1 (errors only). Level 3 dumps every update/query to logcat and
        // visibly janks the UI on the watch.
        Client.execute(TdApi.SetLogVerbosityLevel(1))
    }

    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { cont ->
            client.send(function) { result ->
                if (result is TdApi.Error) {
                    cont.resumeWithException(TdException(result.code, result.message))
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result as T)
                }
            }
        }

    // Fire-and-forget: sends without waiting for a result
    fun sendFire(function: TdApi.Function<*>) {
        client.send(function) {}
    }

    inline fun <reified T : TdApi.Object> updatesOf(): Flow<T> =
        updates.filterIsInstance<T>()

    companion object {
        @Volatile private var instance: TelegramClient? = null

        fun init(filesDir: String): TelegramClient =
            instance ?: synchronized(this) {
                instance ?: TelegramClient(filesDir).also { instance = it }
            }

        fun get(): TelegramClient =
            checkNotNull(instance) { "TelegramClient not initialized. Call init() first." }
    }
}
