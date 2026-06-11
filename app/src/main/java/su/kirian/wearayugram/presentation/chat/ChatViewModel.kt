package su.kirian.wearayugram.presentation.chat

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.ayugram.AyugramSettings
import su.kirian.wearayugram.domain.model.MessageContent
import su.kirian.wearayugram.domain.model.TgMessage

class ChatViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val chatId: Long = checkNotNull(savedState["chatId"])

    // Forum topic id; 0 = a regular chat opened without a topic.
    val topicId: Int = savedState["topicId"] ?: 0

    private val msgRepo = (app as WearAyugramApp).messageRepository

    val messages: StateFlow<List<TgMessage>> = msgRepo.messages(chatId, topicId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val photoAutoload: StateFlow<Boolean> = AyugramSettings.photoAutoload()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        msgRepo.setOpenChat(chatId)
        viewModelScope.launch { msgRepo.loadHistory(chatId, topicId = topicId) }
    }

    override fun onCleared() {
        super.onCleared()
        stopVoicePlayback()
        msgRepo.setOpenChat(-1L)
    }

    fun loadOlder() {
        val oldest = messages.value.firstOrNull()?.id ?: return
        viewModelScope.launch { msgRepo.loadHistory(chatId, fromMessageId = oldest, topicId = topicId) }
    }

    fun sendText(text: String) {
        viewModelScope.launch { msgRepo.sendText(chatId, text, topicId) }
    }

    fun sendVoice(filePath: String, durationSeconds: Int) {
        viewModelScope.launch { msgRepo.sendVoice(chatId, filePath, durationSeconds, topicId) }
    }

    fun markRead(messageIds: LongArray) {
        viewModelScope.launch { msgRepo.markAsRead(chatId, messageIds) }
    }

    fun downloadPhoto(messageId: Long) {
        viewModelScope.launch { msgRepo.downloadPhoto(chatId, messageId, topicId) }
    }

    fun downloadVideoThumb(messageId: Long) {
        viewModelScope.launch { msgRepo.downloadVideoThumb(chatId, messageId, topicId) }
    }

    fun downloadSticker(messageId: Long) {
        viewModelScope.launch { msgRepo.downloadSticker(chatId, messageId, topicId) }
    }

    // ---- Voice playback ----

    private var mediaPlayer: MediaPlayer? = null
    private var positionTicker: Job? = null

    private val _playingVoiceId = MutableStateFlow<Long?>(null)
    val playingVoiceId: StateFlow<Long?> = _playingVoiceId.asStateFlow()

    private val _loadingVoiceId = MutableStateFlow<Long?>(null)
    val loadingVoiceId: StateFlow<Long?> = _loadingVoiceId.asStateFlow()

    private val _voicePositionSec = MutableStateFlow(0)
    val voicePositionSec: StateFlow<Int> = _voicePositionSec.asStateFlow()

    /** Tap on a voice bubble: starts playback (downloading first if needed) or stops it. */
    fun toggleVoice(message: TgMessage) {
        val voice = message.content as? MessageContent.Voice ?: return
        if (_playingVoiceId.value == message.id) {
            stopVoicePlayback()
            return
        }
        viewModelScope.launch {
            stopVoicePlayback()
            val path = voice.localPath ?: run {
                _loadingVoiceId.value = message.id
                val p = msgRepo.downloadVoice(chatId, message.id, topicId)
                _loadingVoiceId.value = null
                p
            } ?: return@launch
            startVoicePlayback(message.id, path)
        }
    }

    private suspend fun startVoicePlayback(messageId: Long, path: String) {
        // prepare() reads and parses the file — keep it off the main thread.
        val mp = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(path)
                    prepare()
                }
            }.getOrNull()
        } ?: return
        mp.setOnCompletionListener { stopVoicePlayback() }
        mp.start()
        mediaPlayer = mp
        _voicePositionSec.value = 0
        _playingVoiceId.value = messageId
        positionTicker = viewModelScope.launch {
            while (true) {
                _voicePositionSec.value = runCatching { mp.currentPosition / 1000 }.getOrDefault(0)
                delay(250)
            }
        }
    }

    fun stopVoicePlayback() {
        positionTicker?.cancel()
        positionTicker = null
        mediaPlayer?.let { mp ->
            runCatching { mp.stop() }
            mp.release()
        }
        mediaPlayer = null
        _playingVoiceId.value = null
        _voicePositionSec.value = 0
    }
}
