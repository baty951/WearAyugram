package su.kirian.wearayugram.ayugram

import kotlinx.coroutines.flow.Flow

object AntiRevokeManager {
    val isEnabled: Flow<Boolean> get() = AyugramSettings.antiRevoke()
    suspend fun setEnabled(v: Boolean) = AyugramSettings.setAntiRevoke(v)
}
