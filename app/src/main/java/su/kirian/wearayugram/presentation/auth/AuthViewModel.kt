package su.kirian.wearayugram.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.AuthState

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = (app as WearAyugramApp).authRepository

    val authState: StateFlow<AuthState> = authRepo.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthState.Loading)

    private val _qrLink = MutableStateFlow<String?>(null)
    val qrLink: StateFlow<String?> = _qrLink.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun startQrLogin() {
        viewModelScope.launch {
            runCatching {
                authRepo.requestQrLogin().collect { link -> _qrLink.value = link }
            }.onFailure { _error.value = it.message }
        }
    }

    fun sendPhone(phone: String) {
        viewModelScope.launch {
            runCatching { authRepo.sendPhoneNumber(phone) }
                .onFailure { _error.value = it.message }
        }
    }

    fun sendCode(code: String) {
        viewModelScope.launch {
            runCatching { authRepo.sendCode(code) }
                .onFailure { _error.value = it.message }
        }
    }

    fun sendPassword(password: String) {
        viewModelScope.launch {
            runCatching { authRepo.sendPassword(password) }
                .onFailure { _error.value = it.message }
        }
    }

    fun clearError() { _error.value = null }
}
