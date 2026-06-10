package su.kirian.wearayugram.domain.repository

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.AuthState

interface AuthRepository {
    val authState: Flow<AuthState>
    suspend fun requestQrLogin(): Flow<String>
    suspend fun sendPhoneNumber(phone: String)
    suspend fun sendCode(code: String)
    suspend fun sendPassword(password: String)
    suspend fun logOut()
}
