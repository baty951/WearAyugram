package su.kirian.wearayugram.domain.model

sealed class AuthState {
    data object Loading : AuthState()
    data object WaitPhone : AuthState()
    data class WaitQr(val link: String) : AuthState()
    data object WaitCode : AuthState()
    data object WaitPassword : AuthState()
    data object Ready : AuthState()
}
