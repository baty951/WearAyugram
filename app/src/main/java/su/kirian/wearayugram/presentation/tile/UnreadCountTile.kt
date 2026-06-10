package su.kirian.wearayugram.presentation.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import su.kirian.wearayugram.WearAyugramApp

class UnreadCountTile : androidx.wear.tiles.TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            val app = application as WearAyugramApp
            val unread = runCatching {
                app.chatRepository.chatList.first()
                    .sumOf { it.unreadCount }
            }.getOrDefault(0)

            val label = if (unread > 0) "$unread непрочитанных" else "Нет новых"

            TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(
                        LayoutElementBuilders.Text.Builder()
                            .setText(label)
                            .build()
                    )
                )
                .build()
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion("1")
                .build()
        )
}
