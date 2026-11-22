package org.schabi.newpipe.util;

import android.content.Context;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.Serializable;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.util.Utility;

/**
 * A list adapter for a list of {@link Stream streams}.
 * It currently supports {@link VideoStream}, {@link AudioStream} and {@link SubtitlesStream}.
 *
 * @param <T> the primary stream type's class extending {@link Stream}
 * @param <U> the secondary stream type's class extending {@link Stream}
 */
public class StreamItemAdapter<T extends Stream, U extends Stream> extends BaseAdapter {
    private final Context context;

    private final StreamSizeWrapper<T> streamsWrapper;
    private final SparseArray<SecondaryStreamHelper<U>> secondaryStreams;

    /**
     * Indicates that at least one of the primary streams is an instance of {@link VideoStream},
     * has no audio ({@link VideoStream#isVideoOnly()} returns true) and has no secondary stream
     * associated with it.
     */
    private final boolean hasAnyVideoOnlyStreamWithNoSecondaryStream;

    public StreamItemAdapter(final Context context, final StreamSizeWrapper<T> streamsWrapper,
                             final SparseArray<SecondaryStreamHelper<U>> secondaryStreams) {
        this.context = context;
        this.streamsWrapper = streamsWrapper;
        this.secondaryStreams = secondaryStreams;

        this.hasAnyVideoOnlyStreamWithNoSecondaryStream =
                checkHasAnyVideoOnlyStreamWithNoSecondaryStream();
    }

    public StreamItemAdapter(final Context context, final StreamSizeWrapper<T> streamsWrapper) {
        this(context, streamsWrapper, null);
    }

    public List<T> getAll() {
        return streamsWrapper.getStreamsList();
    }

    public SparseArray<SecondaryStreamHelper<U>> getAllSecondary() {
        return secondaryStreams;
    }

    @Override
    public int getCount() {
        return streamsWrapper.getStreamsList().size();
    }

    @Override
    public T getItem(final int position) {
        return streamsWrapper.getStreamsList().get(position);
    }

    @Override
    public long getItemId(final int position) {
        return position;
    }

    @Override
    public View getDropDownView(final int position, final View convertView,
                                final ViewGroup parent) {
        return getCustomView(position, convertView, parent, true);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        return getCustomView(((Spinner) parent).getSelectedItemPosition(),
                convertView, parent, false);
    }

    private View getCustomView(final int position, final View view, final ViewGroup parent,
                               final boolean isDropdownItem) {
        View convertView = view;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(
                    R.layout.stream_quality_item, parent, false);
        }

        final ImageView woSoundIconView = convertView.findViewById(R.id.wo_sound_icon);
        final TextView formatNameView = convertView.findViewById(R.id.stream_format_name);
        final TextView qualityView = convertView.findViewById(R.id.stream_quality);
        final TextView sizeView = convertView.findViewById(R.id.stream_size);

        final T stream = getItem(position);

        int woSoundIconVisibility = View.GONE;
        String qualityString;

        if (stream instanceof VideoStream) {
            final VideoStream videoStream = ((VideoStream) stream);
            qualityString = videoStream.getResolution();

            if (hasAnyVideoOnlyStreamWithNoSecondaryStream) {
                if (videoStream.isVideoOnly()) {
                    woSoundIconVisibility = hasSecondaryStream(position)
                            // It has a secondary stream associated with it, so check if it's a
                            // dropdown view so it doesn't look out of place (missing margin)
                            // compared to those that don't.
                            ? (isDropdownItem ? View.INVISIBLE : View.GONE)
                            // It doesn't have a secondary stream, icon is visible no matter what.
                            : View.VISIBLE;
                } else if (isDropdownItem) {
                    woSoundIconVisibility = View.INVISIBLE;
                }
            }
        } else if (stream instanceof AudioStream) {
            final AudioStream audioStream = ((AudioStream) stream);
            final String trackName = audioStream.getAudioTrackName();
            final String bitrateStr = (audioStream.getQuality() != null && !audioStream.getQuality().equals("tiny"))
                    ? audioStream.getQuality()
                    : (audioStream.getAverageBitrate() > 0
                    ? audioStream.getAverageBitrate() + "kbps"
                    : (audioStream.getFormat() == null ? "Unknown" : audioStream.getFormat().getName()));
            qualityString = trackName != null ? trackName + " - " + bitrateStr : bitrateStr;
        } else if (stream instanceof SubtitlesStream) {
            qualityString = ((SubtitlesStream) stream).getDisplayLanguageName();
            if (((SubtitlesStream) stream).isAutoGenerated()) {
                qualityString += " (" + context.getString(R.string.caption_auto_generated) + ")";
            }
        } else {
            try{
                qualityString = stream.getFormat().getSuffix();
            } catch (Exception e){
                qualityString = "Unknown";
            }
        }

        if (streamsWrapper.getSizeInBytes(position) > 0) {
            final SecondaryStreamHelper<U> secondary = secondaryStreams == null ? null
                    : secondaryStreams.get(position);
            if (secondary != null) {
                final long size
                        = secondary.getSizeInBytes() + streamsWrapper.getSizeInBytes(position);
                sizeView.setText(Utility.formatBytes(size));
            } else {
                sizeView.setText(streamsWrapper.getFormattedSize(position));
            }
            sizeView.setVisibility(View.VISIBLE);
        } else {
            sizeView.setVisibility(View.GONE);
        }

        if (stream instanceof SubtitlesStream) {
            formatNameView.setText(((SubtitlesStream) stream).getLanguageTag());
        } else if (stream.getFormat() == MediaFormat.WEBMA_OPUS) {
            // noinspection AndroidLintSetTextI18n
            formatNameView.setText("opus");
        } else {
            String text = "";
            if (stream instanceof VideoStream) {
                text = ((VideoStream) stream).getCodec().toUpperCase().split("\\.")[0];
                if (text.isEmpty()) {
                    text = stream.getFormat().getName();
                }
            } else {
                text = stream.getFormat().getName();
            }
            formatNameView.setText(text);
        }

        qualityView.setText(qualityString);
        woSoundIconView.setVisibility(woSoundIconVisibility);

        return convertView;
    }

    /**
     * @param position which primary stream to check.
     * @return whether the primary stream at position has a secondary stream associated with it.
     */
    private boolean hasSecondaryStream(final int position) {
        return secondaryStreams != null && secondaryStreams.get(position) != null;
    }

    /**
     * @return if there are any video-only streams with no secondary stream associated with them.
     * @see #hasAnyVideoOnlyStreamWithNoSecondaryStream
     */
    private boolean checkHasAnyVideoOnlyStreamWithNoSecondaryStream() {
        for (int i = 0; i < streamsWrapper.getStreamsList().size(); i++) {
            final T stream = streamsWrapper.getStreamsList().get(i);
            if (stream instanceof VideoStream) {
                final boolean videoOnly = ((VideoStream) stream).isVideoOnly();
                if (videoOnly && !hasSecondaryStream(i)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * A wrapper class that includes a way of storing the stream sizes.
     *
     * @param <T> the stream type's class extending {@link Stream}
     */
    public static class StreamSizeWrapper<T extends Stream> implements Serializable {
        private static final StreamSizeWrapper<Stream> EMPTY = new StreamSizeWrapper<>(
                Collections.emptyList(), null);
        private final List<T> streamsList;
        private final long[] streamSizes;
        private final String unknownSize;

        public StreamSizeWrapper(final List<T> sL, final Context context) {
            this.streamsList = sL != null
                    ? sL
                    : Collections.emptyList();
            this.streamSizes = new long[streamsList.size()];
            this.unknownSize = context == null
                    ? "--.-" : context.getString(R.string.unknown_content);

            Arrays.fill(streamSizes, -2);
        }

        /** Max parallel size probes: fast results, but never a connection flood. */
        private static final int MAX_PARALLEL_SIZE_PROBES = 6;

        /**
         * Fetches the sizes of all streams in a wrapper, in parallel (bounded) and
         * incrementally: emits {@code true} each time one more size is known, so the UI
         * can refresh as results arrive instead of waiting for the slowest stream.
         * A failing probe only marks that one stream as unknown (-1).
         *
         * @param <X> the stream type's class extending {@link Stream}
         * @param streamsWrapper the wrapper
         * @return an {@link Observable} emitting on the main thread after each size is set
         */
        public static <X extends Stream> Observable<Boolean> fetchSizeForWrapper(
                final StreamSizeWrapper<X> streamsWrapper) {
            return Observable.fromIterable(new ArrayList<>(streamsWrapper.getStreamsList()))
                    .filter(stream -> streamsWrapper.getSizeInBytes(stream) <= -2)
                    .flatMap(stream -> Observable
                            .fromCallable(() -> new AbstractMap.SimpleEntry<X, Long>(
                                    stream, probeSize(stream)))
                            .subscribeOn(Schedulers.io())
                            .onErrorReturnItem(new AbstractMap.SimpleEntry<X, Long>(stream, -1L)),
                            MAX_PARALLEL_SIZE_PROBES)
                    .observeOn(AndroidSchedulers.mainThread())
                    .map(entry -> {
                        streamsWrapper.setSize(entry.getKey(), entry.getValue());
                        return true;
                    });
        }

        private static long probeSize(final Stream stream) throws IOException {
            if (stream.getDeliveryMethod() == DeliveryMethod.HLS) {
                return -1;
            }
            if (stream.getDeliveryMethod() == DeliveryMethod.SABR) {
                return getSabrContentLength(stream);
            }
            // googlevideo URLs usually carry the exact size (clen=...): no network request needed
            final long fromUrl = contentLengthFromUrl(stream.getUrl());
            if (fromUrl > 0) {
                return fromUrl;
            }
            return DownloaderImpl.getInstance().getContentLength(stream.getUrl());
        }

        private static final java.util.regex.Pattern CLEN_PATTERN =
                java.util.regex.Pattern.compile("[?&/]clen[=/](\\d{1,12})");

        static long contentLengthFromUrl(final String url) {
            if (url == null) {
                return -1;
            }
            final java.util.regex.Matcher m = CLEN_PATTERN.matcher(url);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (final NumberFormatException ignored) {
                    return -1;
                }
            }
            return -1;
        }

        private static long getSabrContentLength(final Stream stream) {
            if (!(stream.getDeliveryMethodInfo() instanceof YoutubeSabrInfo)) {
                return -1;
            }
            final int itag;
            if (stream instanceof AudioStream) {
                itag = ((AudioStream) stream).getItag();
            } else if (stream instanceof VideoStream) {
                itag = ((VideoStream) stream).getItag();
            } else {
                return -1;
            }
            YoutubeSabrInfo.Format format = null;
            for (final YoutubeSabrInfo.Format candidate
                    : ((YoutubeSabrInfo) stream.getDeliveryMethodInfo()).getFormats()) {
                if (candidate.getItag() == itag) {
                    format = candidate;
                    break;
                }
            }
            return format != null && format.getContentLength() > 0
                    ? format.getContentLength() : -1;
        }

        public static <X extends Stream> StreamSizeWrapper<X> empty() {
            //noinspection unchecked
            return (StreamSizeWrapper<X>) EMPTY;
        }

        public List<T> getStreamsList() {
            return streamsList;
        }

        public long getSizeInBytes(final int streamIndex) {
            return streamSizes[streamIndex];
        }

        public long getSizeInBytes(final T stream) {
            return streamSizes[streamsList.indexOf(stream)];
        }

        public String getFormattedSize(final int streamIndex) {
            return formatSize(getSizeInBytes(streamIndex));
        }

        public String getFormattedSize(final T stream) {
            return formatSize(getSizeInBytes(stream));
        }

        private String formatSize(final long size) {
            if (size > -1) {
                return Utility.formatBytes(size);
            }
            return unknownSize;
        }

        public void setSize(final int streamIndex, final long sizeInBytes) {
            streamSizes[streamIndex] = sizeInBytes;
        }

        public void setSize(final T stream, final long sizeInBytes) {
            streamSizes[streamsList.indexOf(stream)] = sizeInBytes;
        }
    }
}
