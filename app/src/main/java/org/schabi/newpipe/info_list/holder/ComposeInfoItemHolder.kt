package org.schabi.newpipe.info_list.holder

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.info_list.CommonItem
import org.schabi.newpipe.info_list.InfoItemBuilder
import org.schabi.newpipe.info_list.ItemViewMode
import org.schabi.newpipe.info_list.PipePipeComposeTheme
import org.schabi.newpipe.info_list.buildInfoItemState
import org.schabi.newpipe.local.history.HistoryRecordManager
import org.schabi.newpipe.util.ThemeHelper

class ComposeInfoItemHolder(
    private val infoItemBuilder: InfoItemBuilder,
    parent: ViewGroup,
    private val itemViewMode: ItemViewMode
) : InfoItemHolder(
    infoItemBuilder,
    ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
) {

    private val composeView = itemView as ComposeView

    override fun updateFromItem(infoItem: InfoItem, historyRecordManager: HistoryRecordManager) {
        // 1) draw immediately without any database access
        render(infoItem, buildInfoItemState(composeView.context, infoItem, null) ?: return)

        // 2) load the watched-progress off the main thread, then redraw this row once
        if (infoItem is org.schabi.newpipe.extractor.stream.StreamInfoItem) {
            composeView.tag = infoItem
            historyRecordManager.loadStreamState(infoItem)
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe({ states ->
                    if (composeView.tag !== infoItem) return@subscribe
                    val streamState = states.firstOrNull() ?: return@subscribe
                    buildInfoItemState(composeView.context, infoItem, null, streamState)
                        ?.let { render(infoItem, it) }
                }, { })
        }
    }

    private fun render(infoItem: InfoItem, state: org.schabi.newpipe.info_list.ComposeItemState) {
        composeView.setContent {
            PipePipeComposeTheme(composeView.context) {
                CommonItem(
                    state = state,
                    isGridLayout = ThemeHelper.isGrid(itemViewMode),
                    isCardLayout = itemViewMode == ItemViewMode.CARD,
                    showDragHandle = false,
                    onClick = {
                        when (infoItem) {
                            is org.schabi.newpipe.extractor.stream.StreamInfoItem -> infoItemBuilder.getOnStreamSelectedListener()?.selected(infoItem)
                            is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> infoItemBuilder.getOnChannelSelectedListener()?.selected(infoItem)
                            is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> infoItemBuilder.getOnPlaylistSelectedListener()?.selected(infoItem)
                        }
                    },
                    onLongClick = {
                        when (infoItem) {
                            is org.schabi.newpipe.extractor.stream.StreamInfoItem -> infoItemBuilder.getOnStreamSelectedListener()?.held(infoItem)
                            is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> infoItemBuilder.getOnChannelSelectedListener()?.held(infoItem)
                            is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> infoItemBuilder.getOnPlaylistSelectedListener()?.held(infoItem)
                        }
                    }
                )
            }
        }
    }
}
