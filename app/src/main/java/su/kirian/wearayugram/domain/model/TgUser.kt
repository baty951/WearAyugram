package su.kirian.wearayugram.domain.model

data class TgUser(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String,
    val phoneNumber: String,
    val isBot: Boolean,
    val isPremium: Boolean,
    val isVerified: Boolean,
    val profilePhotoPath: String?,
) {
    val displayName: String get() = buildString {
        append(firstName)
        if (lastName.isNotEmpty()) append(" $lastName")
    }
}
