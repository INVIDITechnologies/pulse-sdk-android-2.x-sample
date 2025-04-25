package com.ooyala.pulseplayer.PulseManager;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
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
import java.util.Collections;
import java.util.List;

@UnstableApi
public class PulseManagerLive implements PulseLiveSessionListener {

    private PulseLiveSession pulseLiveSession;
    private Button triggerAdBreakBtn;
    private Button showAdsBtn;
    private Button extendSessionBtn;
    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private Context context;
    private VideoItem videoItem = new VideoItem();
    private MediaSource mediaSource;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private int midrollBreakIndex = 0;
    private List<Float> playbackPosition = new ArrayList<>();
    private List<Float> extendedPlaybackPositions = new ArrayList<>();
    private List<PulseLiveAdBreak> mAdBreaks = new ArrayList<PulseLiveAdBreak>();
    private PulseVideoAd currentPulseVideoAd;
    private static String TAG = "PulseLiveManager";

    private Handler adProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable adProgressRunnable;
    private final int AD_PROGRESS_INTERVAL = 200;

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.context = context;
        this.triggerAdBreakBtn = (Button) playerView.findViewById(R.id.adBreak);
        this.showAdsBtn = (Button) playerView.findViewById(R.id.showAds);
        this.extendSessionBtn = (Button) playerView.findViewById(R.id.extendSession);
        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        initializePlayer();
    }

    /////////////////////Playback helper////////////////////
    public void initializePlayer () {
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

        triggerAdBreakBtn.setOnClickListener(v -> {
            if (midrollBreakIndex < playbackPosition.size()) {
                Float position = playbackPosition.get(midrollBreakIndex++);
                PulseLiveAdBreak mAdBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.MIDROLL, position);
                Toast toast = Toast.makeText(context, "Triggerd getAdBreak for playback position - " + playbackPosition.get(midrollBreakIndex-1), Toast.LENGTH_LONG);
                toast.show();
                if (mAdBreak != null) {
                    mAdBreak.getAllLinearAds(this::prepareAdsForPlay);
                    mAdBreaks.add(mAdBreak);
                }
            } else {
                Toast toast = Toast.makeText(context, "No more playback position available for current session. Extend session to request for more breaks.", Toast.LENGTH_LONG);
                toast.show();
            }

        });

        showAdsBtn.setOnClickListener(v -> {
            if (mAdBreaks != null && !mAdBreaks.isEmpty()) {
                if (mAdBreaks.get(0).getPlayableAdsTotal()>0) {
                    exoPlayer.seekToNextMediaItem();
                    mAdBreaks.remove(0); //Remove the currently playing ad break details from the list. If there are no more remaining adbreaks, this if block should not be executed.
                    showAdsBtn.setVisibility(View.INVISIBLE); //Setting it invisible so user cannot start playing following break when previous break is already playing.
                } else {
                    Toast toast = Toast.makeText(context, "No ads to show.", Toast.LENGTH_LONG);
                    toast.show();
                }
            } else {
                Toast toast = Toast.makeText(context, "No AdBreak available", Toast.LENGTH_LONG);
                toast.show();
            }
        });

        extendSessionBtn.setOnClickListener(v -> {
            requestSessionExtension();
            if (extendedPlaybackPositions.size() > 0) {
                playbackPosition.addAll(extendedPlaybackPositions);
            }
        });
    }

    private void prepareAdsForPlay (List < PulseVideoAd > ads) {
        if (ads == null || ads.isEmpty()) {
            Log.w("PulseManagerLive", "No ads available.");
            CharSequence text = "Inventory ad returned from Pulse for Preroll position.";
            if (midrollBreakIndex > 0) {
                text = "Inventory ad returned from Pulse for playback position - " + playbackPosition.get(midrollBreakIndex - 1);
            }
            Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
            toast.show();
            return;
        }
        int adIndex = 0;
        for (PulseVideoAd ad : ads) {
            MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());
            if (mediaFile != null) {
                CharSequence displayTitle = "Playing ads for Preroll Break";
                if (midrollBreakIndex > 0) {
                    displayTitle = "Playing ads for playback position: " + playbackPosition.get(midrollBreakIndex - 1);
                }
                MediaMetadata mediaMetadata = new MediaMetadata
                        .Builder()
                        .setDisplayTitle(displayTitle)
                        .build();

                MediaItem mediaItem = new MediaItem.Builder()
                        .setMediaId(mediaType.AD.getMessage())
                        .setUri(mediaFile.getURI().toString())
                        .setMediaMetadata(mediaMetadata)
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
            if (mediaItem != null && mediaItem.mediaId.equals(mediaType.AD.getMessage())) {
                Log.d(TAG, "onMediaItemTransition - reason: Ad playback Started");
                currentPulseVideoAd = (PulseVideoAd) mediaItem.localConfiguration.tag;
                currentPulseVideoAd.adStarted();
                startAdProgressTracking();
                Toast toast = Toast.makeText(context, mediaItem.mediaMetadata.displayTitle , Toast.LENGTH_LONG);
                toast.show();

            } else {
                Log.d(TAG, "onMediaItemTransition - reason: Content Started/Resumed");
                currentPulseVideoAd = null;
                showAdsBtn.setVisibility(View.VISIBLE);
                stopAdProgressTracking();
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

    private void startAdProgressTracking () {
        adProgressRunnable = new Runnable() {
            @Override
            public void run() {
                long currentMediaPlaybackPosition = exoPlayer.getCurrentPosition();
                MediaItem currentPlayerContent = exoPlayer.getCurrentMediaItem();

                // to stop Runnable tracking if content is playing
                if (currentPlayerContent == null || !mediaType.AD.getMessage().equals(currentPlayerContent.mediaId)) {
                    Log.d(TAG, "Ad progress: tracking stopped.");
                    stopAdProgressTracking();
                    return;
                }

                float progress = (float) currentMediaPlaybackPosition / 1000;
                currentPulseVideoAd.adPositionChanged(progress);

                Log.d(TAG, "Ad Progress: " + progress);
                adProgressHandler.postDelayed(this, AD_PROGRESS_INTERVAL);
            }
        };
        adProgressHandler.post(adProgressRunnable);
    }

    private void stopAdProgressTracking () {
        if (adProgressRunnable != null) {
            adProgressHandler.removeCallbacks(adProgressRunnable);
            adProgressRunnable = null;
            Log.d(TAG, "Ad progress: tracking stopped.");
        }
    }

    public void releasePlayer () {
        if (exoPlayer != null) {
            exoPlayer.removeListener(playbackListener);
            exoPlayer.release();
            exoPlayer = null;
        }
        if (adProgressHandler != null) {
            adProgressHandler.removeCallbacksAndMessages(null);
        }
    }

    private MediaFile selectAppropriateMediaFile (List < MediaFile > potentialMediaFiles) {
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
    private RequestSettings getRequestSettings () {
        RequestSettings newRequestSettings = new RequestSettings();
        if (videoItem.getMidrollPositions() != null && videoItem.getMidrollPositions().length != 0) {
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
    private ContentMetadata getContentMetadata () {
        ContentMetadata contentMetadata = new ContentMetadata();
        contentMetadata.setTags(new ArrayList<>(Arrays.asList(videoItem.getTags())));
        return contentMetadata;
    }

    /////////////////////Session extension method//////////////////////
    private void requestSessionExtension () {
        android.util.Log.i(TAG, "Request a session extension for two midrolls at 30s after previous midroll break.");
        RequestSettings updatedRequestSettings = new RequestSettings();
        List<Float> newPlaybackPositions = new ArrayList<>();
        newPlaybackPositions.add(playbackPosition.size() > 0 ? (playbackPosition.get(playbackPosition.size() - 1) + 30) : 30);
        newPlaybackPositions.add(playbackPosition.size() > 0 ? (playbackPosition.get(playbackPosition.size() - 1) + 60) : 60);
        updatedRequestSettings.setLinearPlaybackPositions(newPlaybackPositions);
        extendedPlaybackPositions = newPlaybackPositions;
        updatedRequestSettings.setInsertionPointFilter(Collections.singletonList(RequestSettings.InsertionPointType.PLAYBACK_POSITION));
        //Make a session extension request and instantiate a PulseSessionExtensionListener.
        //The onComplete callback would be called when the session is successfully extended.
        pulseLiveSession.extendSession(getContentMetadata(), updatedRequestSettings, () -> Log.i(TAG, "Session was successfully extended."));
        Toast toast = Toast.makeText(context, "Session Extended for playback positions - " + newPlaybackPositions, Toast.LENGTH_LONG);
        toast.show();
    }

    @Override
    public void getStaggeringValues (ResponseHeader responseHeader){
        PulseLiveSessionListener.super.getStaggeringValues(responseHeader);

        PulseLiveAdBreak pAdBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.PREROLL);
        if (pAdBreak != null) {
            pAdBreak.getAllLinearAds(this::prepareAdsForPlay);
            exoPlayer.seekToNextMediaItem();
        }
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void illegalOperationOccurred (com.ooyala.pulse.Error error){

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
