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
import su.kirian.wearayugram.domain.model.TgChatFolder
import su.kirian.wearayugram.domain.repository.ChatRepository

class ChatRepositoryImpl(private val client: TelegramClient) : ChatRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _chatList = MutableStateFlow<List<TgChat>>(emptyList())
    override val chatList: Flow<List<TgChat>> = _chatList.asStateFlow()

    private val _folders = MutableStateFlow<List<TgChatFolder>>(emptyList())
    override val folders: Flow<List<TgChatFolder>> = _folders.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Int?>(null)
    override val selectedFolderId: Flow<Int?> = _selectedFolderId.asStateFlow()

    // null = main list. All TDLib list requests below go through this.
    private fun currentList(): TdApi.ChatList? =
        _selectedFolderId.value?.let { TdApi.ChatListFolder(it) }

    private fun TdApi.Chat.belongsToCurrentList(): Boolean {
        val folderId = _selectedFolderId.value
        return positions.any { pos ->
            pos.order != 0L && when (val l = pos.list) {
                is TdApi.ChatListMain -> folderId == null
                is TdApi.ChatListFolder -> l.chatFolderId == folderId
                else -> false
            }
        }
    }

    // Refresh requests are funnelled through this queue and batched: per incoming
    // message TDLib fires several updates, and refreshing the chat (GetChat + full
    // list copy + recomposition) for each one janks the chat list during scroll.
    private val refreshQueue = Channel<Long>(Channel.UNLIMITED)

    init {
        client.updatesOf<TdApi.UpdateChatFolders>()
            .onEach { update ->
                _folders.value = update.chatFolders.map { it.toDomain() }
                // The shown folder was deleted on another device — fall back to main.
                val selected = _selectedFolderId.value
                if (selected != null && update.chatFolders.none { it.id == selected }) {
                    selectFolder(null)
                }
            }
            .launchIn(scope)

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

                val fetchedRaw = pending.mapNotNull { id ->
                    runCatching { client.send(TdApi.GetChat(id)) }.getOrNull()
                }
                if (fetchedRaw.isNotEmpty()) {
                    _chatList.update { current ->
                        val byId = fetchedRaw.associate { it.id to it.toDomain() }
                        val updated = current.map { byId[it.id] ?: it }
                        // Only chats that belong to the shown list may be prepended —
                        // otherwise a message in any chat would leak it into the
                        // currently selected folder.
                        val newOnes = fetchedRaw
                            .filter { raw -> current.none { it.id == raw.id } && raw.belongsToCurrentList() }
                            .map { it.toDomain() }
                        newOnes + updated
                    }
                }
            }
        }
    }

    override suspend fun loadChats(limit: Int) {
        val list = currentList()
        runCatching { client.send(TdApi.LoadChats(list, limit)) }
        val result = runCatching { client.send(TdApi.GetChats(list, limit)) }.getOrNull() ?: return
        val chats = mutableListOf<TgChat>()
        for (id in result.chatIds) {
            runCatching { client.send(TdApi.GetChat(id)).toDomain() }.getOrNull()?.let { chats.add(it) }
        }
        // The folder may have been switched while we fetched 30 sequential GetChats —
        // don't overwrite the new folder's list with stale results.
        if (list?.let { it as? TdApi.ChatListFolder }?.chatFolderId == _selectedFolderId.value ||
            (list == null && _selectedFolderId.value == null)
        ) {
            _chatList.value = chats
        }
    }

    override suspend fun selectFolder(folderId: Int?) {
        if (_selectedFolderId.value == folderId) return
        _selectedFolderId.value = folderId
        // Clear immediately so the UI shows the loading state instead of the
        // previous folder's chats.
        _chatList.value = emptyList()
        loadChats(30)
    }

    override suspend fun getChatById(chatId: Long): TgChat? =
        runCatching { client.send(TdApi.GetChat(chatId)).toDomain() }.getOrNull()
}
