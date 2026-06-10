package su.kirian.wearayugram.ayugram

import kotlinx.coroutines.flow.Flow

object DeleteNotifyManager {
    val isEnabled: Flow<Boolean> get() = AyugramSettings.deleteNotify()
    suspend fun setEnabled(v: Boolean) = AyugramSettings.setDeleteNotify(v)
}
