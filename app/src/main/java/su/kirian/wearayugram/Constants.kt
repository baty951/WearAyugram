package su.kirian.wearayugram

object Constants {
    // Supplied via secrets.properties -> BuildConfig (register at https://my.telegram.org)
    val API_ID: Int = BuildConfig.TG_API_ID
    val API_HASH: String = BuildConfig.TG_API_HASH

    const val TDLIB_DB_DIR = "tdlib"

    // Notification channels
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_DELETE_NOTIFY = "delete_notifications"
    const val CHANNEL_SERVICE = "background_service"
}
