package su.kirian.wearayugram.ayugram

import kotlinx.coroutines.flow.Flow
import su.kirian.wearayugram.domain.model.TgUser

object LocalPremiumPatcher {
    var isLocalPremiumEnabled = false

    val isEnabledFlow: Flow<Boolean> get() = AyugramSettings.localPremium()
    suspend fun setEnabled(v: Boolean) = AyugramSettings.setLocalPremium(v)

    fun TgUser.withLocalPremium(): TgUser =
        if (isLocalPremiumEnabled) copy(isPremium = true) else this
}
