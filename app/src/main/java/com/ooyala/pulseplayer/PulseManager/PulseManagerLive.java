package com.ooyala.pulseplayer.PulseManager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
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
import com.ooyala.pulseplayer.utils.HelperMethods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@UnstableApi
public class PulseManagerLive implements PulseLiveSessionListener {

    private PulseLiveSession pulseLiveSession;
    private Button fetchNextBreakBtn;
    private Button showAdsBtn;
    private Button extendSessionBtn;
    private Button skipBtn;
    private ImageButton exoPlayBtn;
    private ImageButton exoPauseBtn;
    private boolean skipEnabled = false;
    private final PlayerView playerView;
    private ExoPlayer exoPlayer;
    private final Context context;
    private VideoItem videoItem = new VideoItem();
    private MediaSource mediaSource;
    private DefaultHttpDataSource.Factory httpDataSourceFactory;
    private int midRollBreakIndex = 0;
    private final List<Float> playbackPosition = new ArrayList<>();
    private List<Float> extendedPlaybackPositions = new ArrayList<>();
    private final List<PulseLiveAdBreak> mAdBreaks = new ArrayList<>();
    private PulseVideoAd currentPulseVideoAd;
    private static final String TAG = PulseManagerLive.class.getSimpleName();

    private final Handler adProgressHandler = new Handler(Looper.getMainLooper());
    private Runnable adProgressRunnable;
    private final int AD_PROGRESS_INTERVAL = 1000;

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.context = context;
        bindViews();
        setupSessionAndPlayer();
    }

    private void setupSessionAndPlayer() {
        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        initializePlayer();
    }

    private void bindViews() {
        fetchNextBreakBtn = playerView.findViewById(R.id.adBreak);
        showAdsBtn = playerView.findViewById(R.id.showAds);
        extendSessionBtn = playerView.findViewById(R.id.extendSession);
        skipBtn = playerView.findViewById(R.id.skipBtn);
        skipBtn.setVisibility(View.INVISIBLE);
        exoPlayBtn = playerView.findViewById(R.id.exo_play);
        exoPauseBtn = playerView.findViewById(R.id.exo_pause);
    }

    /////////////////////Playback helper////////////////////
    public void initializePlayer() {
        exoPlayer = new ExoPlayer.Builder(context).build();
        playerView.setPlayer(exoPlayer);

        String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));

        httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setReadTimeoutMs(4000)
                .setUserAgent(userAgent);

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(MediaType.CONTENT.getMessage())
                .setUri(videoItem.getContentUrl())
                .build();

        if(videoItem.getContentUrl().endsWith(".mpd")) {
            // Create a dash media source pointing to a dash manifest uri.
            mediaSource = new DashMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem);
        } else {
            mediaSource = new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem);
        }

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();
        exoPlayer.addListener(playbackListener);
        exoPlayer.setPlayWhenReady(false);

        fetchNextBreakBtn.setOnClickListener(v -> {
            if (midRollBreakIndex < playbackPosition.size()) {
                Float position = playbackPosition.get(midRollBreakIndex++);
                PulseLiveAdBreak mAdBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.MIDROLL, position);

                String toastMessage = "Triggered getAdBreak for playback position - " + playbackPosition.get(midRollBreakIndex - 1);
                HelperMethods.showCustomTextSizeToast(v.getContext(), toastMessage, 30);

                if (mAdBreak != null) {
                    mAdBreak.getAllLinearAds(this::prepareAdsForPlay);
                    mAdBreaks.add(mAdBreak);
                }
            } else {
                String toastMessage = "No more playback position available for current session. Extend session to request for more breaks.";
                HelperMethods.showCustomTextSizeToast(v.getContext(), toastMessage, 30);
            }

        });

        showAdsBtn.setOnClickListener(v -> {
            if (mAdBreaks != null && !mAdBreaks.isEmpty()) {
                if (mAdBreaks.get(0).getPlayableAdsTotal() > 0) {
                    exoPlayer.seekToNextMediaItem();
                    mAdBreaks.remove(0); //Remove the currently playing ad break details from the list. If there are no more remaining adBreaks, this if block should not be executed.
                } else {
                    HelperMethods.showCustomTextSizeToast(v.getContext(), "No ads to show.", 30);
                }
            } else {
                HelperMethods.showCustomTextSizeToast(v.getContext(), "No AdBreak available", 30);
            }
        });

        extendSessionBtn.setOnClickListener(v -> {
            requestSessionExtension();
            if (!extendedPlaybackPositions.isEmpty()) {
                playbackPosition.addAll(extendedPlaybackPositions);
            }
        });

        exoPauseBtn.setOnClickListener(v -> exoPlayer.setPlayWhenReady(false));

        exoPlayBtn.setOnClickListener(v -> exoPlayer.setPlayWhenReady(true));
    }

    private void prepareAdsForPlay(List<PulseVideoAd> ads) {
        if (ads == null || ads.isEmpty()) {
            Log.w("PulseManagerLive", "No ads available.");
            CharSequence text = "Inventory ad returned from Pulse for PreRoll position.";
            if (midRollBreakIndex > 0) {
                text = "Inventory ad returned from Pulse for playback position - " + playbackPosition.get(midRollBreakIndex - 1);
            }

            HelperMethods.showCustomTextSizeToast(context, String.valueOf(text), 30);
            return;
        }
        int adIndex = 0;
        for (PulseVideoAd ad : ads) {
            MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());
            if (mediaFile != null) {
                CharSequence displayTitle = "ads for PreRoll Break";
                if (midRollBreakIndex > 0) {
                    displayTitle = "ads for playback position: " + playbackPosition.get(midRollBreakIndex - 1);
                }
                MediaMetadata mediaMetadata = new MediaMetadata
                        .Builder()
                        .setDisplayTitle(displayTitle)
                        .build();

                MediaItem mediaItem = new MediaItem.Builder()
                        .setMediaId(MediaType.AD.getMessage())
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
            if (midRollBreakIndex > 0) {
                Log.d(TAG, String.format(Locale.ENGLISH,"%d ads are added for adBreak %d at playback position %f", adIndex, midRollBreakIndex, playbackPosition.get(midRollBreakIndex -1)));
            } else {
                Log.d(TAG, String.format(Locale.ENGLISH,"%d ads are added for preRoll or postRoll adBreak.", adIndex));
                exoPlayer.seekToNextMediaItem();
            }
            exoPlayer.addMediaSource(mediaSource); //Add content Media in the list to play after ad playback.
        }
    }

    private final Player.Listener playbackListener = new Player.Listener() {
        @Override
        public void onPlayWhenReadyChanged(boolean playerState, int reason) {
            // If there’s no current ad, nothing to do
            if (currentPulseVideoAd == null) return;

            if (playerState) {
                currentPulseVideoAd.adResumed();
                startAdProgressTracking();
            } else {
                stopAdProgressTracking();
                currentPulseVideoAd.adPaused();
            }
        }
        @Override
        public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {

            if (currentPulseVideoAd != null) {
                currentPulseVideoAd.adFinished();
                stopAdProgressTracking();
            }
            if (mediaItem != null && mediaItem.mediaId.equals(MediaType.AD.getMessage())) {
                Log.d(TAG, "onMediaItemTransition - reason: Ad playback Started");
                skipEnabled = false;
                if (mediaItem.localConfiguration != null
                        && mediaItem.localConfiguration.tag instanceof PulseVideoAd) {
                    currentPulseVideoAd = (PulseVideoAd) mediaItem.localConfiguration.tag;
                    currentPulseVideoAd.adStarted();
                } else {
                    Log.w(TAG, "No valid PulseVideoAd tag to start");
                }
                if (exoPlayer.getPlayWhenReady()) {
                    startAdProgressTracking();
                } else {
                    currentPulseVideoAd.adPaused();
                }
                showAdsBtn.setVisibility(View.INVISIBLE);

                HelperMethods.showCustomTextSizeToast(context, "Playing " + mediaItem.mediaMetadata.displayTitle, 30);

                //If this ad is skippable, update the skip button.
                if (currentPulseVideoAd.isSkippable()) {
                    skipBtn.setVisibility(View.VISIBLE);
                    updateSkipButton(0);
                }

            } else {
                Log.d(TAG, "onMediaItemTransition - reason: Content Started/Resumed");
                currentPulseVideoAd = null;
                showAdsBtn.setVisibility(View.VISIBLE);
                skipBtn.setVisibility(View.INVISIBLE);
                stopAdProgressTracking();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            Log.w(TAG, "onPlayerError - error (" + error.errorCode + "): " + error.getMessage());
            if (exoPlayer.getCurrentMediaItem() != null &&
                    MediaType.AD.getMessage().equals(exoPlayer.getCurrentMediaItem().mediaId) &&
                    currentPulseVideoAd != null) {
                Log.d(TAG, "onPlayerError Method, currentPulseVideoAd is:" + currentPulseVideoAd.getTitle() + " : " + currentPulseVideoAd.getIdentifier());
                try {
                    currentPulseVideoAd.adFailed(PulseAdError.COULD_NOT_PLAY);
                    stopAdProgressTracking();
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    Log.e(TAG, msg, e);
                }
            }
        }
    };

    /**
     * A helper method to update the ad skip button.
     *
     * @param currentAdPlayHead the ad playback progress.
     */
    private void updateSkipButton(int currentAdPlayHead) {
        if (currentPulseVideoAd.isSkippable() && !skipEnabled) {
            if (skipBtn.getVisibility() == View.VISIBLE) {
                int remainingTime = (int) (currentPulseVideoAd.getSkipOffset() - currentAdPlayHead);
                String skipBtnText = "Skip ad in ";
                skipBtn.setText(String.format("%s%ss", skipBtnText, remainingTime));
            }
            if ((currentPulseVideoAd.getSkipOffset() <= (currentAdPlayHead))) {
                skipBtn.setText(R.string.skip_ad);
                skipEnabled = true;
                skipBtn.setOnClickListener(v -> {
                    skipBtn.setOnClickListener(null);
                    skipBtn.setVisibility(View.INVISIBLE);

                    currentPulseVideoAd.adSkipped();
                    currentPulseVideoAd = null;
                    Log.d(TAG, "onMediaItemTransition - reason: Ad skipped by user");
                    stopAdProgressTracking();

                    // Move to next media
                    if (exoPlayer.hasNextMediaItem()) {
                        exoPlayer.seekToNextMediaItem();
                    }
                });
            }
        }
    }


    private void startAdProgressTracking() {
        adProgressRunnable = new Runnable() {
            @Override
            public void run() {
                long currentMediaPlaybackPosition = exoPlayer.getCurrentPosition();
                MediaItem currentPlayerContent = exoPlayer.getCurrentMediaItem();

                // to stop Runnable tracking if content is playing
                if (currentPlayerContent == null || !MediaType.AD.getMessage().equals(currentPlayerContent.mediaId)) {
                    Log.d(TAG, "Ad progress: tracking stopped.");
                    stopAdProgressTracking();
                    return;
                }

                float progress = (float) currentMediaPlaybackPosition / 1000;
                if (currentPulseVideoAd != null) {
                    currentPulseVideoAd.adPositionChanged(progress);
                    updateSkipButton((int) progress);

                    Log.d(TAG, "Ad Progress: " + progress);
                }
                adProgressHandler.postDelayed(this, AD_PROGRESS_INTERVAL);
            }
        };
        adProgressHandler.post(adProgressRunnable);
    }

    private void stopAdProgressTracking() {
        if (adProgressRunnable != null) {
            adProgressHandler.removeCallbacks(adProgressRunnable);
            adProgressRunnable = null;
            Log.d(TAG, "Ad progress: tracking stopped.");
        }
    }

    public void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.removeListener(playbackListener);
            exoPlayer.release();
            exoPlayer = null;
        }
        if (adProgressHandler != null) {
            adProgressHandler.removeCallbacksAndMessages(null);
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

    /////////////////////Session extension method//////////////////////
    private void requestSessionExtension() {
        android.util.Log.i(TAG, "Request a session extension for two midRolls at 30s after previous midRoll break.");
        RequestSettings updatedRequestSettings = new RequestSettings();
        List<Float> newPlaybackPositions = new ArrayList<>();
        newPlaybackPositions.add(!playbackPosition.isEmpty() ? (playbackPosition.get(playbackPosition.size() - 1) + 30) : 30);
        newPlaybackPositions.add(!playbackPosition.isEmpty() ? (playbackPosition.get(playbackPosition.size() - 1) + 60) : 60);
        updatedRequestSettings.setLinearPlaybackPositions(newPlaybackPositions);
        extendedPlaybackPositions = newPlaybackPositions;
        updatedRequestSettings.setInsertionPointFilter(Collections.singletonList(RequestSettings.InsertionPointType.PLAYBACK_POSITION));
        //Make a session extension request and instantiate a PulseSessionExtensionListener.
        //The onComplete callback would be called when the session is successfully extended.
        pulseLiveSession.extendSession(getContentMetadata(), updatedRequestSettings, () -> Log.i(TAG, "Session was successfully extended."));
        String toastMessage = "Session Extended for playback positions - " + newPlaybackPositions;
        HelperMethods.showCustomTextSizeToast(context, toastMessage, 30);
    }

    @Override
    public void getStaggeringValues(ResponseHeader responseHeader) {
        PulseLiveSessionListener.super.getStaggeringValues(responseHeader);

        PulseLiveAdBreak pAdBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.PREROLL);
        if (pAdBreak != null) {
            pAdBreak.getAllLinearAds(this::prepareAdsForPlay);
        }
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    public void illegalOperationOccurred(com.ooyala.pulse.Error error) {
        throw new RuntimeException(error.getMessage());
    }
}
