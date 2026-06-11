package su.kirian.wearayugram.data.tdlib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads TDLib files (photo sizes etc.) and returns the local path.
 *
 * DownloadFile(synchronous=true) makes TDLib answer the request only once the file
 * is fully on disk, so the suspend send() is the whole download — no UpdateFile
 * bookkeeping needed.
 */
class FileDownloader(private val client: TelegramClient) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // The watch CPU and radio choke on parallel transfers; two at a time is plenty.
    private val semaphore = Semaphore(2)

    // Recompositions and repeated taps must not start duplicate downloads of the
    // same file: callers for an in-flight fileId await the existing Deferred.
    private val inFlight = ConcurrentHashMap<Int, Deferred<String?>>()

    suspend fun download(fileId: Int, timeoutMs: Long = DOWNLOAD_TIMEOUT_MS): String? {
        if (fileId == 0) return null
        val deferred = inFlight.computeIfAbsent(fileId) {
            scope.async {
                semaphore.withPermit {
                    withTimeoutOrNull(timeoutMs) {
                        runCatching {
                            val file = client.send(TdApi.DownloadFile(fileId, PRIORITY, 0, 0, true))
                            file.local?.path
                                ?.takeIf { file.local.isDownloadingCompleted && it.isNotEmpty() }
                        }.getOrNull()
                    }
                }
            }
        }
        val path = runCatching { deferred.await() }.getOrNull()
        // Failed attempts are evicted so a later tap retries; successes stay cached.
        if (path == null) inFlight.remove(fileId, deferred)
        return path
    }

    companion object {
        // 1..32, higher = sooner. Chat photos should win over background fetches.
        private const val PRIORITY = 16
        const val DOWNLOAD_TIMEOUT_MS = 60_000L
        // Videos are much larger than photos/voice — give them several minutes.
        const val VIDEO_TIMEOUT_MS = 300_000L
    }
}
