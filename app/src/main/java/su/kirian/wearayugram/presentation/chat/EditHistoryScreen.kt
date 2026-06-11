package su.kirian.wearayugram.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import su.kirian.wearayugram.WearAyugramApp
import su.kirian.wearayugram.domain.model.TgMessageEdit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved pre-edit versions of a message (anti-edit history). Versions only exist
 * for edits that happened while the app held the message — TDLib itself never
 * keeps old text.
 */
@Composable
fun EditHistoryScreen(navController: NavController, messageId: Long) {
    val app = navController.context.applicationContext as WearAyugramApp
    val versions by app.messageRepository.editHistory(messageId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text("История изменений")
                }
            }
            if (versions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет сохранённых версий",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(versions, key = { it.id }) { version ->
                    EditVersionItem(version)
                }
            }
        }
    }
}

@Composable
private fun EditVersionItem(version: TgMessageEdit) {
    val cs = MaterialTheme.colorScheme
    // Single Text node — reliable as a TransformingLazyColumn item root.
    val annotated = remember(version) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = cs.onSurface, fontSize = 14.sp)) {
                append(version.text)
            }
            withStyle(SpanStyle(color = cs.onSurface.copy(alpha = 0.55f), fontSize = 9.sp)) {
                append("   до " + editTimeFormat.format(Date(version.editedAt)))
            }
        }
    }
    Text(
        text = annotated,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

private val editTimeFormat = SimpleDateFormat("HH:mm d MMM", Locale.getDefault())
