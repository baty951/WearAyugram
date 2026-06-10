package su.kirian.wearayugram.ayugram

import kotlinx.coroutines.flow.Flow

object GhostModeManager {
    val isEnabled: Flow<Boolean> get() = AyugramSettings.ghostMode()
    suspend fun setEnabled(v: Boolean) = AyugramSettings.setGhostMode(v)
}
