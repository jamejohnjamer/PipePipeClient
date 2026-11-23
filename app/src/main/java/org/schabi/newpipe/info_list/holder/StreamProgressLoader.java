package org.schabi.newpipe.info_list.holder;

import android.view.View;

import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Loads the "watched progress" of a list row OFF the main thread.
 * The old code called {@code loadStreamState(...).blockingGet()} while binding each row, which
 * performs a database write + read on the UI thread per row (confirmed ANR: main thread parked in
 * BlockingMultiObserver.blockingGet inside RecyclerView layout).
 */
final class StreamProgressLoader {
    private StreamProgressLoader() { }

    static void load(final AnimatedProgressBar bar, final HistoryRecordManager manager,
                     final InfoItem infoItem, final StreamInfoItem item, final boolean animate) {
        bar.setTag(item); // detects row recycling while the query is running
        manager.loadStreamState(infoItem)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(states -> {
                    if (bar.getTag() != item) {
                        return; // row was recycled for another item
                    }
                    final StreamStateEntity state = states.length > 0 ? states[0] : null;
                    if (state != null && item.getDuration() > 0
                            && item.getStreamType() != StreamType.LIVE_STREAM) {
                        bar.setMax((int) item.getDuration());
                        final int seconds =
                                (int) TimeUnit.MILLISECONDS.toSeconds(state.getProgressMillis());
                        if (!animate) {
                            bar.setProgress(seconds);
                            bar.setVisibility(View.VISIBLE);
                        } else if (bar.getVisibility() == View.VISIBLE) {
                            bar.setProgressAnimated(seconds);
                        } else {
                            bar.setProgress(seconds);
                            ViewUtils.animate(bar, true, 500);
                        }
                    } else if (bar.getVisibility() == View.VISIBLE) {
                        if (animate) {
                            ViewUtils.animate(bar, false, 500);
                        } else {
                            bar.setVisibility(View.GONE);
                        }
                    }
                }, throwable -> { /* progress bar is cosmetic: ignore failures */ });
    }
}
