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

    /**
     * ONE background thread for all row-progress queries (never a thread per row, never the
     * unbounded Schedulers.io() pool, which could pile up hundreds of threads waiting for the
     * database while scrolling).
     */
    private static final io.reactivex.rxjava3.core.Scheduler SCHEDULER =
            Schedulers.from(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "row-progress");
                t.setDaemon(true);
                return t;
            }));

    static void load(final AnimatedProgressBar bar, final HistoryRecordManager manager,
                     final InfoItem infoItem, final StreamInfoItem item, final boolean animate) {
        bar.setTag(item); // detects row recycling while the query is running
        // defer: rows that were already recycled by the time their turn comes cost nothing
        io.reactivex.rxjava3.core.Single.defer(() -> bar.getTag() == item
                        ? manager.loadStreamState(infoItem)
                        : io.reactivex.rxjava3.core.Single.just(new StreamStateEntity[0]))
                .subscribeOn(SCHEDULER)
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
