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
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
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
fun PhoneAuthScreen(navController: NavController, viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var phoneNumber by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val replies = RemoteInput.getResultsFromIntent(result.data)
            phoneNumber = replies?.getCharSequence(KEY_PHONE)?.toString()?.trim() ?: phoneNumber
        }
    }

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Ready -> navController.navigate(Routes.CHAT_LIST) {
                popUpTo(0) { inclusive = true }
            }
            is AuthState.WaitCode -> {
                sending = false
                navController.navigate(Routes.CODE_AUTH)
            }
            else -> {}
        }
    }

    // Reset sending indicator if error occurred
    LaunchedEffect(error) {
        if (error != null) sending = false
    }

    val listState = rememberTransformingLazyColumnState()
    // TdLib accepts SetAuthenticationPhoneNumber from WaitPhone and also from
    // WaitQr (which cancels the QR flow). Only Loading/Ready block phone entry.
    val isReady = authState is AuthState.WaitPhone || authState is AuthState.WaitQr
    val canSubmit = phoneNumber.isNotBlank() && isReady && !sending

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = {
                    if (canSubmit) {
                        sending = true
                        viewModel.clearError()
                        viewModel.sendPhone(phoneNumber)
                    }
                },
                enabled = canSubmit
            ) {
                if (sending) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                else Text("Войти")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            authState is AuthState.Loading -> "Подключение..."
                            !isReady -> "Инициализация..."
                            else -> "Номер телефона"
                        }
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        val remoteInput = RemoteInput.Builder(KEY_PHONE)
                            .setLabel("+7 999 123 45 67")
                            .build()
                        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                        phoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (phoneNumber.isEmpty()) "Нажми для ввода" else phoneNumber,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            if (error != null) {
                item {
                    Text(
                        text = "Ошибка: $error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
            }
            item {
                Button(
                    onClick = { navController.navigate(Routes.PROXY_SETTINGS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("MTProxy")
                }
            }
            item {
                Button(
                    onClick = { navController.navigate(Routes.QR_AUTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text("QR-код")
                }
            }
        }
    }
}

private const val KEY_PHONE = "phone"
