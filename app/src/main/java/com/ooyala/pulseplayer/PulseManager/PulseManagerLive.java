package com.ooyala.pulseplayer.PulseManager;


import android.app.Activity;
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
    PulseLiveAdBreak.AdListReadyCallback adListReadyCallback;

    private int adBreakIndex = -1;
    private Boolean adsAreFetched = false;
    private List<PulseVideoAd> fetchedAds = null;

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, Button triggerAdBreakBtn, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.context = context;
        this.triggerAdBreakBtn = triggerAdBreakBtn;

        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        initializePlayer();
    }

    private static PulseManagerLive pulseManagerLive = new PulseManagerLive();

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private PulseManagerLive() {
    }

    /* Static 'instance' method */
    public static PulseManagerLive getInstance() {
        return pulseManagerLive;
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

        triggerAdBreakBtn.setOnClickListener(v -> {
            RequestSettings.AdBreakType adBreakType = contentStarted
                    ? RequestSettings.AdBreakType.MIDROLL
                    : RequestSettings.AdBreakType.PREROLL;

            PulseLiveAdBreak mAdBreak = contentStarted
                    ? pulseLiveSession.getAdBreak(adBreakType, 20)
                    : pulseLiveSession.getAdBreak(adBreakType);

            if (mAdBreak != null) {
                adBreak = mAdBreak;
                showAds(adBreak);
            } else if (fetchedAds != null && adsAreFetched) {
                handleAdPlayback(fetchedAds);
            } else {
                Log.e("PulseManagerLive", "Ad break is null. Cannot play ad.");
            }
        });
    }

    private void showAds(PulseLiveAdBreak adBreak) {

        adBreak.getAllLinearAds(new AdListReadyCallback() {
            @Override
            public void onSuccess(List<PulseVideoAd> ads) {

                handleAdPlayback(ads);
            }
        });

    }


    private void handleAdPlayback(List<PulseVideoAd> ads) {
        if (ads == null || ads.isEmpty()) {
            adsAreFetched = false;
            Log.w("PulseManagerLive", "No ads available.");
            return;
        }

        adBreakIndex++;
        adsAreFetched = true;

        if (adBreakIndex < ads.size()) {
            fetchedAds = ads;
            playAd(ads.get(adBreakIndex));
        } else {
            Log.i("PulseManagerLive", "No more ads to show.");
            triggerAdBreakBtn.setEnabled(false); // Disable the button
            adsAreFetched = false;
        }
    }


    protected void playAd(PulseVideoAd ad) {
        MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());
        if (mediaFile != null) {
            MediaItem mediaItem = new MediaItem.Builder()
                    .setMediaId(ad.getIdentifier())
                    .setUri(mediaFile.getURI().toString())
                    .build();

            // Create a data source factory.
            DataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            // Create a progressive media source pointing to a stream uri.
            MediaSource adSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);

            // saving the reference to live stream media source
            MediaSource resumeMediaSource = mediaSource;

            exoPlayer.setMediaSource(adSource);
            exoPlayer.prepare();
            exoPlayer.play();
            exoPlayer.setPlayWhenReady(true);

            // listener to check when ad ends
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_ENDED) {
                        // resuming original content
                        exoPlayer.setMediaSource(resumeMediaSource);
                        exoPlayer.prepare();
                        exoPlayer.play();
                        exoPlayer.seekToDefaultPosition();
                        exoPlayer.setPlayWhenReady(true);

                        // removing this listener to avoid being called again and again.
                        exoPlayer.removeListener(this);
                    }
                }
            });
        }
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
