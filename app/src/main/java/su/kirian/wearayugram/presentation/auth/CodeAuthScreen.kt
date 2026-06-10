package su.kirian.wearayugram.presentation.auth

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import su.kirian.wearayugram.domain.model.AuthState
import su.kirian.wearayugram.presentation.Routes

@Composable
fun CodeAuthScreen(navController: NavController, viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    val codeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            code = replies?.getCharSequence(KEY_CODE)?.toString()?.trim() ?: code
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> navController.navigate(Routes.CHAT_LIST) {
                popUpTo(0) { inclusive = true }
            }
            else -> {}
        }
    }

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    if (code.isNotBlank()) {
                        if (authState is AuthState.WaitPassword) {
                            viewModel.sendPassword(code)
                        } else {
                            viewModel.sendCode(code)
                        }
                    }
                },
                enabled = code.isNotBlank()
            ) {
                Text("OK")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (authState is AuthState.WaitPassword) "Пароль 2FA"
                        else "Код из SMS"
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val label = if (authState is AuthState.WaitPassword) "Пароль" else "12345"
                        val remoteInput = RemoteInput.Builder(KEY_CODE)
                            .setLabel(label)
                            .build()
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                        codeLauncher.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = if (code.isEmpty()) "Нажми для ввода" else code,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private const val KEY_CODE = "code"
