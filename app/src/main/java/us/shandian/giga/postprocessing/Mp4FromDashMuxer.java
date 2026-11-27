package us.shandian.giga.postprocessing;

import android.content.Context;
import org.schabi.newpipe.streams.Mp4FromDashWriter;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;

/**
 * @author kapodamy
 */
class Mp4FromDashMuxer extends Postprocessing {

    Mp4FromDashMuxer() {
        super(true, true, ALGORITHM_MP4_FROM_DASH_MUXER);
    }

    @Override
    int process(String source, Context context, SharpStream out, SharpStream... sources) throws IOException {
        return process(out, sources);
    }

    int process(SharpStream out, SharpStream... sources) throws IOException {
        Mp4FromDashWriter muxer = new Mp4FromDashWriter(sources);
        final us.shandian.giga.get.DownloadMission m = currentMission();
        if (m != null) {
            muxer.setTags(m.tagArtist, m.tagTitle, m.tagDate == null ? null : m.tagDate.substring(0, Math.min(10, m.tagDate.length())));
        }
        if (m != null) {
            boolean dateSet = false;
            if (m.tagDate != null) {
                try {
                    muxer.setCreationTimeUnixSeconds(
                            java.time.Instant.parse(m.tagDate).getEpochSecond());
                    dateSet = true;
                } catch (final Exception ignored) {
                    dateSet = false;
                }
            }
            if (!dateSet) {
                muxer.clearCreationTime(); // unknown publish date: do not show the download date
            }
        }
        muxer.parseSources();
        muxer.selectTracks(0, 0);
        muxer.build(out);

        return OK_RESULT;
    }

}
