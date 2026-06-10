package su.kirian.wearayugram.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import su.kirian.wearayugram.domain.model.AuthState
import su.kirian.wearayugram.presentation.auth.AuthViewModel
import su.kirian.wearayugram.presentation.auth.CodeAuthScreen
import su.kirian.wearayugram.presentation.auth.PhoneAuthScreen
import su.kirian.wearayugram.presentation.auth.QrAuthScreen
import su.kirian.wearayugram.presentation.chat.ChatScreen
import su.kirian.wearayugram.presentation.chat.PhotoViewScreen
import su.kirian.wearayugram.presentation.chatlist.ChatListScreen
import su.kirian.wearayugram.presentation.proxy.ProxySettingsScreen
import su.kirian.wearayugram.presentation.settings.SettingsScreen
import su.kirian.wearayugram.presentation.theme.WearAyugramTheme

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notifications are optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            WearAyugramTheme {
                WearApp()
            }
        }
    }
}

@Composable
fun WearApp() {
    val navController = rememberSwipeDismissableNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Ready) {
            navController.navigate(Routes.CHAT_LIST) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Routes.PHONE_AUTH
        ) {
            composable(Routes.QR_AUTH) {
                QrAuthScreen(navController, authViewModel)
            }
            composable(Routes.PHONE_AUTH) {
                PhoneAuthScreen(navController, authViewModel)
            }
            composable(Routes.CODE_AUTH) {
                CodeAuthScreen(navController, authViewModel)
            }
            composable(Routes.CHAT_LIST) {
                ChatListScreen(navController)
            }
            composable(Routes.CHAT) { backStack ->
                val chatId = backStack.arguments?.getString("chatId")?.toLongOrNull() ?: return@composable
                ChatScreen(navController, chatId)
            }
            composable(Routes.PHOTO_VIEW) { backStack ->
                val chatId = backStack.arguments?.getString("chatId")?.toLongOrNull() ?: return@composable
                val messageId = backStack.arguments?.getString("messageId")?.toLongOrNull() ?: return@composable
                PhotoViewScreen(navController, chatId, messageId)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.PROXY_SETTINGS) {
                ProxySettingsScreen(navController)
            }
        }
    }
}
