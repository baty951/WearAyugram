package su.kirian.wearayugram.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val ghostMode by vm.ghostMode.collectAsStateWithLifecycle()
    val antiRevoke by vm.antiRevoke.collectAsStateWithLifecycle()
    val deleteNotify by vm.deleteNotify.collectAsStateWithLifecycle()
    val localPremium by vm.localPremium.collectAsStateWithLifecycle()
    val photoAutoload by vm.photoAutoload.collectAsStateWithLifecycle()
    val cacheCleared by vm.cacheCleared.collectAsStateWithLifecycle()
    val activeAccount by vm.activeAccount.collectAsStateWithLifecycle()
    val accountNames by vm.accountNames.collectAsStateWithLifecycle()
    val accountBusy by vm.accountBusy.collectAsStateWithLifecycle()

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Настройки")
                }
            }
            item {
                SwitchButton(
                    checked = ghostMode,
                    onCheckedChange = vm::setGhostMode,
                    label = { Text("Ghost Mode") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchButton(
                    checked = antiRevoke,
                    onCheckedChange = vm::setAntiRevoke,
                    label = { Text("Anti-revoke") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchButton(
                    checked = deleteNotify,
                    onCheckedChange = vm::setDeleteNotify,
                    label = { Text("Уведомл. удал.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchButton(
                    checked = localPremium,
                    onCheckedChange = vm::setLocalPremium,
                    label = { Text("Local Premium") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SwitchButton(
                    checked = photoAutoload,
                    onCheckedChange = vm::setPhotoAutoload,
                    label = { Text("Автозагрузка фото") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(
                    onClick = vm::clearMediaCache,
                    enabled = cacheCleared != false,
                    label = {
                        Text(
                            when (cacheCleared) {
                                null -> "Очистить кэш медиа"
                                false -> "Очистка..."
                                true -> "Кэш очищен ✓"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("Аккаунты")
                }
            }
            items(accountNames.size) { slot ->
                val name = accountNames.getOrNull(slot)
                val isActive = slot == activeAccount
                FilledTonalButton(
                    onClick = { vm.switchAccount(slot) },
                    enabled = !accountBusy && !isActive,
                    label = {
                        Text(
                            text = (if (isActive) "✓ " else "") +
                                (name ?: "Слот ${slot + 1}: добавить"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(
                    onClick = vm::logout,
                    enabled = !accountBusy,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    label = { Text(if (accountBusy) "Подождите…" else "Выйти из аккаунта") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
