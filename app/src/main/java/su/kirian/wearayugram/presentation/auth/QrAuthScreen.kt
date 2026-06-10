package su.kirian.wearayugram.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import su.kirian.wearayugram.domain.model.AuthState
import su.kirian.wearayugram.presentation.Routes

@Composable
fun QrAuthScreen(navController: NavController, viewModel: AuthViewModel) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val qrLink by viewModel.qrLink.collectAsStateWithLifecycle()

    // Only start QR login when TDLib is ready to accept RequestQrCodeAuthentication
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.WaitPhone -> viewModel.startQrLogin()
            is AuthState.Ready -> navController.navigate(Routes.CHAT_LIST) {
                popUpTo(0) { inclusive = true }
            }
            is AuthState.WaitCode -> navController.navigate(Routes.CODE_AUTH)
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val link = qrLink
        if (link == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Генерация QR...",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        } else {
            val bitmap = remember(link) { generateQrBitmap(link, 280) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR для входа в Telegram",
                    modifier = Modifier.size(170.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Telegram → Настройки → Устройства",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.size(width = 180.dp, height = 32.dp)
                )
                Spacer(Modifier.height(4.dp))
                CompactButton(onClick = {
                    navController.navigate(Routes.PHONE_AUTH)
                }) {
                    Text("По номеру")
                }
            }
        }
    }
}
