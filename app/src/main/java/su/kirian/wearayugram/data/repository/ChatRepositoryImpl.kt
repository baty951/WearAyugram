package su.kirian.wearayugram.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import su.kirian.wearayugram.data.tdlib.TelegramClient
import su.kirian.wearayugram.data.tdlib.toDomain
import su.kirian.wearayugram.domain.model.TgChat
import su.kirian.wearayugram.domain.repository.ChatRepository

class ChatRepositoryImpl(private val client: TelegramClient) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _chatList = MutableStateFlow<List<TgChat>>(emptyList())
    override val chatList: Flow<List<TgChat>> = _chatList.asStateFlow()

    // Refresh requests are funnelled through this queue and batched: per incoming
    // message TDLib fires several updates, and refreshing the chat (GetChat + full
    // list copy + recomposition) for each one janks the chat list during scroll.
    private val refreshQueue = Channel<Long>(Channel.UNLIMITED)

    init {
        // UpdateChatLastMessage already covers everything UpdateNewMessage signalled
        // for chat-list purposes, so we don't listen to UpdateNewMessage here.
        client.updatesOf<TdApi.UpdateChatLastMessage>()
            .onEach { refreshQueue.send(it.chatId) }
            .launchIn(scope)

        client.updatesOf<TdApi.UpdateChatReadInbox>()
            .onEach { update ->
                _chatList.update { list ->
                    list.map { if (it.id == update.chatId) it.copy(unreadCount = update.unreadCount) else it }
                }
            }
            .launchIn(scope)

        // Position changes reorder the whole list; conflate bursts so a storm of
        // updates costs one full reload per ~1s instead of one per update
        // (loadChats = GetChats + 30 sequential GetChat calls).
        client.updatesOf<TdApi.UpdateChatPosition>()
            .conflate()
            .onEach {
                delay(1_000)
                loadChats()
            }
            .launchIn(scope)

        // Batch consumer: the first request opens a 400ms window, every request that
        // arrives within it coalesces; each distinct chat is fetched once and the
        // list is emitted once per window.
        scope.launch {
            while (true) {
                val pending = hashSetOf(refreshQueue.receive())
                val deadline = System.currentTimeMillis() + 400
                while (true) {
                    val remain = deadline - System.currentTimeMillis()
                    if (remain <= 0) break
                    val next = withTimeoutOrNull(remain) { refreshQueue.receive() } ?: break
                    pending += next
                }

                val fetched = pending.mapNotNull { id ->
                    runCatching { client.send(TdApi.GetChat(id)).toDomain() }.getOrNull()
                }
                if (fetched.isNotEmpty()) {
                    _chatList.update { current ->
                        val byId = fetched.associateBy { it.id }
                        val updated = current.map { byId[it.id] ?: it }
                        val newOnes = fetched.filter { f -> current.none { it.id == f.id } }
                        newOnes + updated
                    }
                }
            }
        }
    }

    override suspend fun loadChats(limit: Int) {
        runCatching { client.send(TdApi.LoadChats(null, limit)) }
        val result = runCatching { client.send(TdApi.GetChats(null, limit)) }.getOrNull() ?: return
        val chats = mutableListOf<TgChat>()
        for (id in result.chatIds) {
            runCatching { client.send(TdApi.GetChat(id)).toDomain() }.getOrNull()?.let { chats.add(it) }
        }
        _chatList.value = chats
    }

    override suspend fun getChatById(chatId: Long): TgChat? =
        runCatching { client.send(TdApi.GetChat(chatId)).toDomain() }.getOrNull()
}
