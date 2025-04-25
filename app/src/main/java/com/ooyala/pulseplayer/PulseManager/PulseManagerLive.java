package com.ooyala.pulseplayer.PulseManager;


import android.content.Context;
import android.view.View;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.ooyala.pulse.ContentMetadata;

import com.ooyala.pulse.MediaFile;
import com.ooyala.pulse.Pulse;

import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulse.PulseLiveAdBreak;
import com.ooyala.pulse.PulseLiveSession;
import com.ooyala.pulse.PulseLiveSessionListener;

import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulse.RequestSettings;
import com.ooyala.pulse.ResponseHeader;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UnstableApi
public class PulseManagerLive implements PulseLiveSessionListener {

    private PulseLiveSession pulseLiveSession;
    private Button triggerAdBreakBtn;
    private Button showAdsBtn;
    private PulseLiveAdBreak adBreak;
    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private Context context;
    private VideoItem videoItem = new VideoItem();
    private MediaSource mediaSource;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private int[] midrollPositions;
    private int midrollBreakIndex = 0;
    private List<PulseLiveAdBreak> mAdBreaks = new ArrayList<PulseLiveAdBreak>();
    private PulseVideoAd currentPulseVideoAd;
    private static String TAG = "PulseLiveManager";

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, Button triggerAdBreakBtn, Button showAdsBtn, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.context = context;
        this.triggerAdBreakBtn = triggerAdBreakBtn;
        this.showAdsBtn = showAdsBtn;

        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        initializePlayer();
    }

    /////////////////////Playback helper////////////////////
    public void initializePlayer() {
        exoPlayer = new ExoPlayer.Builder(context).build();
        playerView.setPlayer(exoPlayer);

        String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));

        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setReadTimeoutMs(4000)
                .setUserAgent(userAgent);

        DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(mediaType.CONTENT.getMessage())
                .setUri(videoItem.getContentUrl())
                .build();

        // Create a dash media source pointing to a dash manifest uri.
        mediaSource =
                new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem);

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
        exoPlayer.addListener(playbackListener);
        exoPlayer.setPlayWhenReady(false);
        midrollPositions = videoItem.getMidrollPositions();
        if (midrollPositions == null) midrollPositions = new int[0];

        if ("Midroll".equals(videoItem.getContentTitle())) {
            exoPlayer.setPlayWhenReady(true);
            triggerAdBreakBtn.setOnClickListener(v -> {
                if (midrollBreakIndex < midrollPositions.length) {
                    int position = midrollPositions[midrollBreakIndex++];
                    PulseLiveAdBreak mAdBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.MIDROLL, position);

                    if (mAdBreak != null) {
                        mAdBreak.getAllLinearAds(this::prepareAdsForPlay);
                        mAdBreaks.add(mAdBreak);
                    }
                } else {
                    Log.e(TAG, "Ad break is null.");
                }
            });

            showAdsBtn.setOnClickListener(v -> {
                if (mAdBreaks != null && !mAdBreaks.isEmpty()) {
                    adBreak = mAdBreaks.get(0);
                    exoPlayer.seekToNextMediaItem();
                    showAdsBtn.setVisibility(View.INVISIBLE); //Setting it invisible so user cannot start playing following break when previous break is already playing.
                } else {
                    Log.d(TAG, "No AdBreak available.");
                }
            });
        }
    }

    private void prepareAdsForPlay(List<PulseVideoAd> ads) {
        if (ads == null || ads.isEmpty()) {
            Log.w("PulseManagerLive", "No ads available.");
            return;
        }
        int adIndex = 0;
        for (PulseVideoAd ad : ads) {
            MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());
            if (mediaFile != null) {
                MediaItem mediaItem = new MediaItem.Builder()
                        .setMediaId(mediaType.AD.getMessage())
                        .setUri(mediaFile.getURI().toString())
                        .setTag(ad)
                        .build();
                MediaSource adSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                        .createMediaSource(mediaItem);
                adIndex++;
                exoPlayer.addMediaSource(adSource);
            }
        }
        if (adIndex > 0) {
            if (midrollBreakIndex > 0) {
                Log.d(TAG, String.format("%d ads are added for adBreak %d", adIndex, midrollBreakIndex));
            } else {
                Log.d(TAG, String.format("%d ads are added for preroll or postroll adBreak.", adIndex));
            }
            exoPlayer.addMediaSource(mediaSource);
        }
    }

    private final Player.Listener playbackListener = new Player.Listener() {
        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
            if (currentPulseVideoAd != null) {
                currentPulseVideoAd.adFinished();
            }
            if (mediaItem.mediaId == mediaType.AD.getMessage()) {
                Log.d(TAG, "onMediaItemTransition - reason: Ad playback Started");
                currentPulseVideoAd = (PulseVideoAd) mediaItem.localConfiguration.tag;
                currentPulseVideoAd.adStarted();
            } else {
                Log.d(TAG, "onMediaItemTransition - reason: Content Started/Resumed");
                currentPulseVideoAd = null;
                showAdsBtn.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.w(TAG, "onPlayerError - error (" + error.errorCode + "): " + error.getMessage());
            if (exoPlayer.getCurrentMediaItem().mediaId == mediaType.AD.getMessage()) {
                Log.d(TAG, "onPlayerError Method, currentPulseVideoAd is:" + currentPulseVideoAd.getTitle() + " : " + currentPulseVideoAd.getIdentifier());
                try {
                    currentPulseVideoAd.adFailed(PulseAdError.COULD_NOT_PLAY);
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    };

    private MediaFile selectAppropriateMediaFile(List<MediaFile> potentialMediaFiles) {
        MediaFile selected = null;
        int highestBitrate = 0;
        for (MediaFile file : potentialMediaFiles) {
            if (file.getBitRate() > highestBitrate) {
                highestBitrate = file.getBitRate();
                selected = file;
            }
        }
        return selected;
    }

    //////////////////////////// Helper Methods ///////////////////////////////

    /**
     * Create an instance of RequestSetting from the selected videoItem.
     *
     * @return The created {@link RequestSettings}
     */
    private RequestSettings getRequestSettings() {
        RequestSettings newRequestSettings = new RequestSettings();

        if (videoItem.getMidrollPositions() != null && videoItem.getMidrollPositions().length != 0) {
            ArrayList<Float> playbackPosition = new ArrayList<>();
            for (int i = 0; i < videoItem.getMidrollPositions().length; i++) {
                playbackPosition.add((float) videoItem.getMidrollPositions()[i]);
            }
            newRequestSettings.setLinearPlaybackPositions(playbackPosition);
        }
        return newRequestSettings;
    }

    /**
     * Create an instance of ContentMetadata from the selected videoItem.
     *
     * @return The created {@link ContentMetadata}.
     */
    private ContentMetadata getContentMetadata() {
        ContentMetadata contentMetadata = new ContentMetadata();
        contentMetadata.setTags(new ArrayList<>(Arrays.asList(videoItem.getTags())));
        return contentMetadata;
    }

    @Override
    public void getStaggeringValues(ResponseHeader responseHeader) {
        PulseLiveSessionListener.super.getStaggeringValues(responseHeader);

        if ("Preroll".equals(videoItem.getContentTitle())) {
            PulseLiveAdBreak pAdBreak =
                    pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.PREROLL);

            if (pAdBreak != null) {
                pAdBreak.getAllLinearAds(this::prepareAdsForPlay);
                exoPlayer.seekToNextMediaItem();
            }
            exoPlayer.setPlayWhenReady(true);
        }
    }

    @Override
    public void illegalOperationOccurred(com.ooyala.pulse.Error error) {

    }

    private enum mediaType {
        AD("ad"),
        CONTENT("content");
        private String message;

        mediaType(String s) {
            this.message = s;
        }

        public String getMessage() {
            return message;
        }
    }
}
