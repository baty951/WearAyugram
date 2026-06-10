package su.kirian.wearayugram.presentation

object Routes {
    const val QR_AUTH = "auth_qr"
    const val PHONE_AUTH = "auth_phone"
    const val CODE_AUTH = "auth_code"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val SETTINGS      = "settings"
    const val PROXY_SETTINGS = "proxy_settings"

    fun chat(chatId: Long) = "chat/$chatId"
}
