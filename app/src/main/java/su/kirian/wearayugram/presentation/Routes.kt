package su.kirian.wearayugram.presentation

object Routes {
    const val QR_AUTH = "auth_qr"
    const val PHONE_AUTH = "auth_phone"
    const val CODE_AUTH = "auth_code"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val TOPICS = "topics/{chatId}"
    const val CHAT_TOPIC = "chat/{chatId}/topic/{topicId}"
    const val PHOTO_VIEW = "photo/{chatId}/{messageId}/{topicId}"
    const val VIDEO_PLAY = "video/{chatId}/{messageId}/{topicId}/{loop}"
    const val EDIT_HISTORY = "edit_history/{messageId}"
    const val SEARCH = "search"
    const val SETTINGS      = "settings"
    const val PROXY_SETTINGS = "proxy_settings"

    fun chat(chatId: Long) = "chat/$chatId"
    fun topics(chatId: Long) = "topics/$chatId"
    fun chatTopic(chatId: Long, topicId: Int) = "chat/$chatId/topic/$topicId"
    fun photoView(chatId: Long, messageId: Long, topicId: Int) = "photo/$chatId/$messageId/$topicId"
    fun videoPlay(chatId: Long, messageId: Long, topicId: Int, loop: Boolean = false) =
        "video/$chatId/$messageId/$topicId/${if (loop) 1 else 0}"
    fun editHistory(messageId: Long) = "edit_history/$messageId"
}
