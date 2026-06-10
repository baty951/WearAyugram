package su.kirian.wearayugram.presentation.proxy

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper

@Composable
fun ProxySettingsScreen(navController: NavController) {
    val vm: ProxyViewModel = viewModel()
    val enabled by vm.proxyEnabled.collectAsStateWithLifecycle()
    val host    by vm.proxyHost.collectAsStateWithLifecycle()
    val port    by vm.proxyPort.collectAsStateWithLifecycle()
    val secret  by vm.proxySecret.collectAsStateWithLifecycle()

    val hostLauncher = remoteInputLauncher(KEY_HOST) { vm.setHost(it) }
    val portLauncher = remoteInputLauncher(KEY_PORT) { vm.setPort(it) }
    val secretLauncher = remoteInputLauncher(KEY_SECRET) { vm.setSecret(it) }

    val listState = rememberTransformingLazyColumnState()
    val canApply = host.isNotBlank() && secret.isNotBlank()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { if (canApply) vm.applyAndEnable() },
                enabled = canApply
            ) {
                Text("Применить")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("MTProxy")
                }
            }
            item {
                SwitchButton(
                    checked = enabled,
                    onCheckedChange = vm::setEnabled,
                    label = { Text("Включить прокси") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                FieldButton(
                    label = "Хост",
                    value = host.ifEmpty { "не задан" },
                    onClick = { hostLauncher.launch(remoteInputIntent(KEY_HOST, "example.com")) }
                )
            }
            item {
                FieldButton(
                    label = "Порт",
                    value = port.toString(),
                    onClick = { portLauncher.launch(remoteInputIntent(KEY_PORT, "443")) }
                )
            }
            item {
                FieldButton(
                    label = "Секрет",
                    value = if (secret.isEmpty()) "не задан" else secret.take(8) + "...",
                    onClick = { secretLauncher.launch(remoteInputIntent(KEY_SECRET, "ee...")) }
                )
            }
            item {
                Text(
                    text = "Секрет начинается с ee для fake-TLS. Скопируй из t.me/mtpro_xyz или аналогов.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            item {
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text("Назад")
                }
            }
        }
    }
}

@Composable
private fun FieldButton(label: String, value: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun remoteInputLauncher(key: String, onResult: (String) -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(key)?.toString()?.trim()
            if (!text.isNullOrEmpty()) onResult(text)
        }
    }

private fun remoteInputIntent(key: String, hint: String) =
    RemoteInputIntentHelper.createActionRemoteInputIntent().also { intent ->
        RemoteInputIntentHelper.putRemoteInputsExtra(
            intent, listOf(RemoteInput.Builder(key).setLabel(hint).build())
        )
    }

private const val KEY_HOST   = "proxy_host"
private const val KEY_PORT   = "proxy_port"
private const val KEY_SECRET = "proxy_secret"
