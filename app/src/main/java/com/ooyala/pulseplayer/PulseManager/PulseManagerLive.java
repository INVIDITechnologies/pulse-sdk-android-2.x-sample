package com.ooyala.pulseplayer.PulseManager;


import android.content.Context;
import android.widget.Button;

import androidx.media3.common.MediaItem;
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
public class PulseManagerLive implements PulseLiveSessionListener, PulseLiveAdBreak {

    private PulseLiveSession pulseLiveSession;
    private Button triggerAdBreakBtn;
    private PulseLiveAdBreak adBreak;
    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private Context context;
    private VideoItem videoItem = new VideoItem();
    private MediaSource mediaSource;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private Boolean contentStarted = false;
    private int[] midrollPositions;
    private int midrollIndex = 0;
    private List<PulseVideoAd> currentAdList;
    private int currentAdIndex = 0;
    private String currentAdIdentifier = "";

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, Button triggerAdBreakBtn, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.context = context;
        this.triggerAdBreakBtn = triggerAdBreakBtn;

        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        initializePlayer();
    }

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private PulseManagerLive() {
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
        // Create a dash media source pointing to a dash manifest uri.
        mediaSource =
                new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(videoItem.getContentUrl()));

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
        exoPlayer.setPlayWhenReady(true);
        contentStarted = true;

        midrollPositions = videoItem.getMidrollPositions();
        if (midrollPositions == null) midrollPositions = new int[0];

        triggerAdBreakBtn.setOnClickListener(v -> {
            RequestSettings.AdBreakType adBreakType = contentStarted
                    ? RequestSettings.AdBreakType.MIDROLL
                    : RequestSettings.AdBreakType.PREROLL;

            if (midrollIndex < midrollPositions.length) {
                int position = midrollPositions[midrollIndex++];
                PulseLiveAdBreak mAdBreak = contentStarted
                        ? pulseLiveSession.getAdBreak(adBreakType, position)
                        : pulseLiveSession.getAdBreak(adBreakType);

                if (mAdBreak != null) {
                    adBreak = mAdBreak;
                    showAds(adBreak);
                }
            } else {
                Log.e("PulseManagerLive", "Ad break is null. Cannot play ad.");
            }
        });
    }

    private void showAds(PulseLiveAdBreak adBreak) {
        adBreak.getAllLinearAds(this::handleAdPlayback);
    }

    private void handleAdPlayback(List<PulseVideoAd> ads) {
        if (ads == null || ads.isEmpty()) {
            Log.w("PulseManagerLive", "No ads available.");
            return;
        }
        playAd(ads, 0); // Start with first ad
    }


    private void playAd(List<PulseVideoAd> ads, int index) {
        currentAdList = ads;
        currentAdIndex = index;

        if (index >= ads.size()) {
            Log.i("PulseManagerLive", "All ads completed. Resuming content.");
            resumeContentPlayback();
            return;
        }

        PulseVideoAd ad = ads.get(index);
        currentAdIdentifier = ad.getIdentifier();
        MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());

        if (mediaFile != null) {
            Log.i("PulseManagerLive", "Starting Ad " + (index + 1) + "/" + ads.size());

            MediaItem mediaItem = new MediaItem.Builder()
                    .setMediaId(ad.getIdentifier())
                    .setUri(mediaFile.getURI().toString())
                    .build();

            MediaSource adSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem);

            exoPlayer.setMediaSource(adSource);
            exoPlayer.prepare();
            exoPlayer.setPlayWhenReady(true);
            exoPlayer.addListener(adPlaybackListener);

        } else {
            playAd(ads, index + 1);
        }
    }

    private final Player.Listener adPlaybackListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                exoPlayer.removeListener(this);
                Log.i("PulseManagerLive", "Completed Ad: " + currentAdIdentifier);
                playAd(currentAdList, currentAdIndex + 1);
            }
        }
    };

    private void resumeContentPlayback() {
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
        exoPlayer.seekToDefaultPosition();
        exoPlayer.setPlayWhenReady(true);
    }

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
    }

    @Override
    public void illegalOperationOccurred(com.ooyala.pulse.Error error) {

    }

    @Override
    public int getPlayableAdsTotal() {
        return 0;
    }

    @Override
    public void getAllLinearAds(AdListReadyCallback adListReadyCallback) {
       adBreak.getAllLinearAds(adListReadyCallback);
    }

    @Override
    public RequestSettings.AdBreakType getAdBreakType() {
        return null;
    }
}
