package com.ooyala.pulseplayer.PulseManager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.ooyala.pulse.ContentMetadata;
import com.ooyala.pulse.Error;
import com.ooyala.pulse.FriendlyObstruction;
import com.ooyala.pulse.MediaFile;
import com.ooyala.pulse.OmidAdSession;
import com.ooyala.pulse.PlayerState;
import com.ooyala.pulse.Pulse;
import com.ooyala.pulse.PulseAdBreak;
import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulse.PulseCompanionAd;
import com.ooyala.pulse.PulsePauseAd;
import com.ooyala.pulse.PulseSession;
import com.ooyala.pulse.PulseSessionListener;
import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulse.RequestSettings;
import com.ooyala.pulseplayer.BuildConfig;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;
import com.ooyala.pulseplayer.videoPlayer.CustomCompanionBannerView;
import com.ooyala.pulseplayer.videoPlayer.CustomImageView;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class PulseManager implements PulseSessionListener {

    private PulseSession pulseSession;
    private PlayerView playerView;
    private View adView;
    private ImageView nextAdThumbnail;
    private ExoPlayer exoPlayerInstance;
    private MediaSource mediaSource;
    private MediaSource nextAdMediaSource;
    private Button skipBtn;
    private CustomImageView pauseImageView;
    private CustomCompanionBannerView companionBannerViewTop, companionBannerViewBottom;
    private Activity activity;
    private Context context;
    private SeekBar playerVolumeController;
    private Float playerVolume;
    private VideoItem videoItem = new VideoItem();
    private List<String> availableCompanionBannerZones = new ArrayList();
    private boolean duringVideoContent = false, duringAd = false, duringPause = false, companionClicked = false, playAd = false, playVideoContent = false;
    private boolean nextAdPreloaded = false;
    private boolean contentStarted = false;
    private boolean adPaused = false;
    private boolean adStarted = false;
    private PulseVideoAd currentPulseVideoAd;
    private PulsePauseAd currentPulsePauseAd;
    public static Handler contentProgressHandler;
    private static Handler playbackHandler = new Handler();
    private long currentContentProgress = 0;
    private long playbackPosition = 0;

    private boolean isSessionExtensionRequested = false;
    private long currentAdProgress = 0L;
    private boolean skipEnabled = false;
    private ClickThroughCallback clickThroughCallback;
    private List<FriendlyObstruction> friendlyObs;

    private int currentWindow = 0;
    private boolean playWhenReady = true;
    private ExoPlayerEventListener playbackStateListener = new ExoPlayerEventListener();
    private static final String TAG = "Pulse Demo Player";


    public PulseManager(VideoItem videoItem, PlayerView playerView, View adView, ImageView nextAdThumbnail, List<FriendlyObstruction> friendlyObs, Button skipButton, CustomImageView imageView, CustomCompanionBannerView topcompanionBanner, CustomCompanionBannerView bottomCompanionBanner, Activity activity, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.skipBtn = skipButton;
        this.nextAdThumbnail = nextAdThumbnail;
        this.pauseImageView = imageView;
        this.companionBannerViewTop = topcompanionBanner;
        this.companionBannerViewBottom = bottomCompanionBanner;
        this.friendlyObs = friendlyObs;
        this.adView = adView;

        //The companion banner zones can be assigned to the companion ad through Pulse Manager.
        //In this sample app, we used the contentDescription element of banner views to indicate the desired zone.
        if (companionBannerViewTop.getContentDescription() != null) {
            availableCompanionBannerZones.add(companionBannerViewTop.getContentDescription().toString());
        }
        if (companionBannerViewBottom.getContentDescription() != null) {
            availableCompanionBannerZones.add(companionBannerViewBottom.getContentDescription().toString());
        }
        this.activity = activity;
        this.context = context;

        playerVolumeController = activity.findViewById(R.id.volume_controller);
        initPlayerVolumeControl();
        // Create and start a pulse session
        pulseSession = Pulse.createSession(getContentMetadata(), getRequestSettings());
        pulseSession.startSession(this);

        //Initiating the handler to track the progress of content/ad playback.
        contentProgressHandler = new Handler();
        contentProgressHandler.post(onEveryTimeInterval);

        mediaSource = buildMediaSource(videoItem.getContentUrl());
    }

    private static PulseManager pulseManager = new PulseManager();

    /* A private Constructor prevents any other
     * class from instantiating.
     */
    private PulseManager() {
    }

    /* Static 'instance' method */
    public static PulseManager getInstance() {
        return pulseManager;
    }

    @Override
    public void startContentPlayback() {
        //Setup video player for content playback.
        if (!duringPause) {
            contentStarted = true;
        }
        playVideoContent();
    }

    @Override
    public void startAdBreak(PulseAdBreak adBreak) {
        Log.i(TAG, "Ad break started.");
        duringAd = false;
        duringVideoContent = false;
    }

    @Override
    public void startAdPlayback(PulseVideoAd pulseVideoAd, float timeout) {
        currentPulseVideoAd = pulseVideoAd;
        if ("omsdk".equals(videoItem.getContentId())) {
            if ("OM AdVerification with skipAd as Friendly Obstruction".equals(videoItem.getContentTitle()) || "OMSDK Certification - skipAd as friendly obstruction".equals(videoItem.getContentTitle())) {
                OmidAdSession.createOmidAdSession(currentPulseVideoAd, context, adView, "invidi.pulseplayer.com", friendlyObs);
            } else {
                OmidAdSession.createOmidAdSession(currentPulseVideoAd, context, adView, "invidi.pulseplayer.com");
            }
        }
        playAdContent(timeout, pulseVideoAd);
        //Try to show the companion ads attached to this ad.
        showCompanionAds(pulseVideoAd);
    }

    @Override
    public void preloadNextAd(PulseVideoAd ad) {
        MediaFile mediaFile = selectAppropriateMediaFile(ad.getMediaFiles());
        if (mediaFile != null) {
            String adUri = mediaFile.getURI().toString();
            Bitmap bitmap = null;
            try {
                bitmap = retriveVideoFrameFromVideo(adUri);
                if (bitmap != null) {
                    nextAdThumbnail.setImageBitmap(bitmap);
                    nextAdThumbnail.setVisibility(View.VISIBLE);
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            nextAdMediaSource = buildMediaSource(adUri);
            nextAdPreloaded = true;
        } else {
            Log.i(TAG, "Ad media file was not found.");
        }
    }

    public static Bitmap retriveVideoFrameFromVideo(String videoPath) throws Throwable {
        Bitmap bitmap = null;
        MediaMetadataRetriever mediaMetadataRetriever = null;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(videoPath, new HashMap<String, String>());
            //   mediaMetadataRetriever.setDataSource(videoPath);
            bitmap = mediaMetadataRetriever.getFrameAtTime();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Throwable("Exception in retriveVideoFrameFromVideo(String videoPath)" + e.getMessage());

        } finally {
            if (mediaMetadataRetriever != null) {
                mediaMetadataRetriever.release();
            }
        }
        return bitmap;
    }

    /**
     * Pulse SDK calls this method when pause ad should be displayed.
     *
     * @param pulsePauseAd The {@link PulsePauseAd} that should be displayed.
     */
    @Override
    public void showPauseAd(PulsePauseAd pulsePauseAd) {
        Log.i(TAG, "Pulse signaled pause ad display");
        currentPulsePauseAd = pulsePauseAd;
        //When companion banner is clicked, the content should be paused and pulseSession.contentPaused() should be reported.
        //Considering that we don't want to display pause ad every time a companion banner is clicked, we check companionClicked flag in our showPauseAd implementation.
        if (!exoPlayerInstance.getPlayWhenReady() && !companionClicked) {
            if (pauseImageView != null && currentPulsePauseAd != null) {
                //Assign a listener to the custom imageView to monitor its image related events.
                pauseImageView.setCustomImgViewListener(new CustomImageView.CustomImgViewListener() {
                    @Override
                    public void onCloseBtnCLicked() {
                        // If user closed the pause ad, report adClosed to Pulse SDK.
                        pauseImageView.setVisibility(View.INVISIBLE);
                        currentPulsePauseAd.adClosed();
                        currentPulsePauseAd = null;
                    }

                    @Override
                    public void onPauseAdClicked() {
                        // If user clicked on the pause ad, report adClickThroughTriggered to Pulse SDK.
                        if (currentPulsePauseAd != null) {
                            if (currentPulsePauseAd.getClickThroughURL() != null) {
                                currentPulsePauseAd.adClickThroughTriggered();
                                clickThroughCallback.onPauseAdClicked(currentPulsePauseAd.getClickThroughURL());
                            }
                        }
                    }

                    @Override
                    public void onImageDisplayed() {
                        // If resource was successfully loaded and player is still in paused mode, report adDisplayed to Pulse SDK.
                        if (!exoPlayerInstance.getPlayWhenReady()) {
                            pauseImageView.setVisibility(View.VISIBLE);
                            if (currentPulsePauseAd != null) {
                                currentPulsePauseAd.adDisplayed();
                            }
                        }
                    }

                    @Override
                    public void onImageLoadingFailed(PulseAdError error) {
                        // If loading resource failed report the error to Pulse SDK.
                        if (currentPulsePauseAd != null) {
                            currentPulsePauseAd.adFailed(error);
                        }
                    }
                });

                // Set up the imageView to show the pause ad.
                pauseImageView.init();
                // Get the mime type of the pause ad resource.
                String pauseAdType = currentPulsePauseAd.getResourceType();
                // Verify if the resource format is supported.
                if (pauseAdType.equals("image/jpeg")) {
                    URL srcUrl = currentPulsePauseAd.getResourceURL();
                    // If the resource is reachable, try loading the resource.
                    if (srcUrl != null) {
                        pauseImageView.loadImage(srcUrl);
                    }
                }
            }
        }
    }

    @Override
    public void sessionEnded() {
        Log.i(TAG, "Session ended");
        duringVideoContent = false;
        duringAd = false;
        currentContentProgress = 0;
        removeCallback(contentProgressHandler);
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void illegalOperationOccurred(Error error) {
        // In debug mode a runtime exception would be thrown in order to find and
        // correct mistakes in the integration.
        if (BuildConfig.DEBUG) {
            throw new RuntimeException(error.getMessage());
        } else {
            // Don't know how to recover from this, stop the session and continue
            // with the content.
            Log.i(TAG, error.getMessage());
            pulseSession.stopSession();
            pulseSession = null;
            startContentPlayback();
        }
    }

    /**
     * Try to play the companion ads attached to the provided ad.
     *
     * @param pulseVideoAd The ad video.
     */
    private void showCompanionAds(PulseVideoAd pulseVideoAd) {
        if (pulseVideoAd.getCompanions() != null) {
            List<PulseCompanionAd> companionAds = pulseVideoAd.getCompanions();
            for (int i = 0; i < companionAds.size(); i++) {
                verifyCompanionAd(companionAds.get(i));
            }
        }
    }

    /**
     * Try to hide the companion ads once assosicated video ad is finished.
     *
     * @param pulseVideoAd The ad video.
     */
    private void hideCompanionAds(PulseVideoAd pulseVideoAd) {
        if (pulseVideoAd.getCompanions() != null) {
            List<PulseCompanionAd> companionAds = pulseVideoAd.getCompanions();
            for (int i = 0; i < companionAds.size(); i++) {
                verifyCompanionAd(companionAds.get(i));
            }
        }
    }

    /**
     * Verify that the provided companion ad can be displayed.
     *
     * @param companionAd The companion ad.
     */
    private void verifyCompanionAd(PulseCompanionAd companionAd) {
        if (companionAd != null && verifyCompanionAdZone(companionAd)) {
            if (companionAd.getZoneIdentifier().equals("companion-top")) {
                displayCompanionBanner(companionAd, companionBannerViewTop);
            } else if (companionAd.getZoneIdentifier().equals("companion-bottom")) {
                displayCompanionBanner(companionAd, companionBannerViewBottom);
            }
        }
    }

    /**
     * Verify that the provided companion ad's zone matches one of the designated companion banner zones.
     *
     * @param companionAd The companion ad.
     */
    private boolean verifyCompanionAdZone(PulseCompanionAd companionAd) {
        for (String str : availableCompanionBannerZones) {
            if (companionAd != null) {
                if (companionAd.getZoneIdentifier().equals(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Initiate the companion banner view and display the provided companion banner.
     *
     * @param companionAd         The companion ad.
     * @param companionBannerView The custom companion banner view.
     */
    private void displayCompanionBanner(final PulseCompanionAd companionAd, CustomCompanionBannerView companionBannerView) {
        //Assign a listener to the customCompanionBannerView to monitor its image related events.
        companionBannerView.setCustomCompanionBannerViewListener(new CustomCompanionBannerView.CustomCompanionBannerViewListener() {
            @Override
            public void onCompanionBannerClicked() {
                if (companionAd != null) {
                    companionClicked = true;
                    if (companionAd.getClickThroughURL() != null) {
                        if (exoPlayerInstance.getPlaybackState() == Player.STATE_READY && exoPlayerInstance.getPlayWhenReady()) {
                            exoPlayerInstance.setPlayWhenReady(false);
                        }
                        clickThroughCallback.onCompanionAdClicked(companionAd.getClickThroughURL());
                    }
                }
            }

            @Override
            public void onCompanionBannerDisplayed() {
                companionAd.adDisplayed();
            }
        });

        // Set up the imageView to show the companion ad.
        companionBannerView.init();
        // Get the MIME type of the companion ad resource.
        String companionAdType = companionAd.getResourceType();
        if (companionAdType != null) {
            // Verify if the resource format is supported.
            if (companionAdType.equals("image/png")) {
                URL srcUrl = companionAd.getResourceURL();
                // If the resource is reachable, try loading the resource.
                if (srcUrl != null) {
                    companionBannerView.loadImage(srcUrl);
                }
            }
        }
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
        newRequestSettings.setMaxBitRate(800);
        newRequestSettings.setAdvertisingID("96bd03b6-defc-4203-83d3-dc1c730801f7");
        newRequestSettings.setApplicationName("pulseplayer");
        newRequestSettings.setApplicationVersion(BuildConfig.VERSION_NAME);
        newRequestSettings.setApplicationID(BuildConfig.APPLICATION_ID);
        newRequestSettings.setApplicationBundle("applicationBundle");
        newRequestSettings.setStartAdTimeout(500000000);
        newRequestSettings.setThirdPartyTimeout(200000000);
        newRequestSettings.setTotalPassbackTimeout(300000000);
        return newRequestSettings;
    }

    /**
     * Create an instance of ContentMetadata from the selected videoItem.
     *
     * @return The created {@link ContentMetadata}.
     */
    private ContentMetadata getContentMetadata() {
        ContentMetadata contentMetadata = new ContentMetadata();
        contentMetadata.setContentProviderInformation("pcode", "embed");
        contentMetadata.setTags(new ArrayList<>(Arrays.asList(videoItem.getTags())));
        contentMetadata.setCategory(videoItem.getCategory());
        return contentMetadata;
    }

    /**
     * An ad contains a list of media file with different dimensions and bit rates.
     * In this example this method selects the media file with the highest bit rate
     * but in a production application the best media file should be selected based
     * on resolution/bandwidth/format considerations.
     *
     * @param potentialMediaFiles A list of available mediaFiles.
     * @return the selected media file.
     */
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

    /////////////////////Playback helper////////////////////

    public void initializePlayer() {
        //Get the selected videoItem from the bundled information.
        if (exoPlayerInstance == null) {
            exoPlayerInstance = new ExoPlayer.Builder(context).build();
        }
        exoPlayerInstance.addListener(playbackStateListener);
        exoPlayerInstance.setPlayWhenReady(playWhenReady);
        if (playAd) {
            exoPlayerInstance.seekTo(currentWindow, currentAdProgress);
        } else if (playVideoContent) {
            exoPlayerInstance.seekTo(currentWindow, currentContentProgress);
        }
        exoPlayerInstance.prepare(mediaSource, false, false);
        playerView.setPlayer(exoPlayerInstance);
        //Assign an onTouchListener to player while displaying ad to support click through event.
        playerView.setOnClickListener(v -> {
            if (duringAd) {
                //If there is a clickthrouhg url, report adClickThroughTriggered event and try to open the url.
                if (currentPulseVideoAd.getClickthroughURL() != null) {
                    //Added to prevent click on the ads that are not loaded yet which would prevent "ad paused before the ad played" error.
                    duringAd = false;
//                            exoPlayerInstance.setPlayWhenReady(false);
                    //Report ad clicked to Pulse SDK.
//                    currentPulseVideoAd.adPaused();  // commented this since it will be called in onPause method of VideoPlayer Activity also to avoid "ad paused before the ad played" error.
                    currentPulseVideoAd.adClickThroughTriggered();
                    clickThroughCallback.onClicked(currentPulseVideoAd);
                    Log.i(TAG, "ClickThrough occurred.");
                }
            }
        });
    }

    public void releasePlayer() {
        if (exoPlayerInstance != null) {
            playWhenReady = exoPlayerInstance.getPlayWhenReady();
            playbackPosition = exoPlayerInstance.getCurrentPosition();
            // Condition to avoid resume process if playback position is less than 0 ms, which causes " Did not expect user ad resume while waiting for ad to start." error
            if (playbackPosition > 0) {
                if (duringAd) {
                    currentAdProgress = playbackPosition;
                } else if (duringVideoContent) {
                    currentContentProgress = playbackPosition;
                }
            } else {
                adPaused = false;
                duringPause = false;
            }
            currentWindow = exoPlayerInstance.getCurrentWindowIndex();
            exoPlayerInstance.removeListener(playbackStateListener);
            exoPlayerInstance.release();
            exoPlayerInstance = null;
            playerView.setPlayer(null);
        }
    }

    private MediaSource buildMediaSource(String uri) {
        // This is the MediaSource representing the media to be played.
        // Getting media from raw resource

        String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));

        // Default parameters, except allowCrossProtocolRedirects is true
        DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)
                .setUserAgent(userAgent);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .build();

        return new ProgressiveMediaSource.Factory(httpDataSourceFactory)
                    .createMediaSource(mediaItem);
    }

    /**
     * Play/resume the selected video content.
     */
    private void playVideoContent() {
        playVideoContent = true;
        mediaSource = buildMediaSource(videoItem.getContentUrl());
        initializePlayer();
    }

    /**
     * Try to play the provided ad.
     *
     * @param timeout      The timeout for ad playback.
     * @param pulseVideoAd The ad video.
     */
    private void playAdContent(float timeout, final PulseVideoAd pulseVideoAd) {
        //Configure a handler to monitor playback timeout.
        playbackHandler.postDelayed(playbackRunnable, (long) (timeout * 1000));
        if (nextAdPreloaded == true && nextAdMediaSource != null) {
            playAd = true;
            mediaSource = nextAdMediaSource;
            nextAdMediaSource = null;
            initializePlayer();
        } else {
            MediaFile mediaFile = selectAppropriateMediaFile(pulseVideoAd.getMediaFiles());
            if (mediaFile != null) {
                String adUri = mediaFile.getURI().toString();
                playAd = true;
                mediaSource = buildMediaSource(adUri);
                initializePlayer();
            } else {
                playbackHandler.removeCallbacks(playbackRunnable);
                duringAd = false;
                Log.i(TAG, "Ad media file was not found.");
                skipBtn.setVisibility(View.INVISIBLE);
                currentPulseVideoAd.adFailed(PulseAdError.REQUEST_FAILED);
                adStarted = false;
                adPaused = false;
            }
        }
    }

    ////////////////////click through related methods///////////
    public void returnFromClickThrough() {
        playerView.showController();
        if (companionClicked) {
            if (duringAd) {
                resumeAdPlayback();
            }
            companionClicked = false;
        }
        if (!duringPause) {
            duringAd = true;
            resumeAdPlayback();
        }
    }

    public void setOnClickThroughCallback(ClickThroughCallback callback) {
        clickThroughCallback = callback;
    }

    public interface ClickThroughCallback {
        void onClicked(PulseVideoAd ad);

        void onPauseAdClicked(URL url);

        void onCompanionAdClicked(URL url);
    }

    /////////////////////Runnable methods//////////////////////
    /**
     * A runnable responsible for monitoring ad playback timeout.
     */
    private Runnable playbackRunnable = new Runnable() {
        @Override
        public void run() {
            // Timeout for ad playback is reached and it should be reported to Pulse SDK.
            Log.i(TAG, "Time out for ad playback is reached");
            if (currentPulseVideoAd != null) {
                currentPulseVideoAd.adFailed(PulseAdError.REQUEST_TIMED_OUT);
            } else {
                throw new RuntimeException("currentPulseVideoAd is null");
            }
        }
    };

    /**
     * A runnable called periodically to keep track of the content/Ad playback's progress.
     */
    private Runnable onEveryTimeInterval = new Runnable() {
        @Override
        public void run() {
            //Time interval in milliseconds to check playback progress.
            int timeInterval = 200;
            contentProgressHandler.postDelayed(onEveryTimeInterval, timeInterval);
            if (duringVideoContent) {
                if (exoPlayerInstance != null && exoPlayerInstance.getCurrentPosition() != 0) {
                    currentContentProgress = exoPlayerInstance.getCurrentPosition();
                    //Report content progress to Pulse SDK. This progress would be used to trigger ad break.
                    if (pulseSession != null) {
                        pulseSession.contentPositionChanged(currentContentProgress / 1000);
                    }
                }
                //Check for session extension scenario.
                if (videoItem.getContentTitle() != null && videoItem.getContentTitle().equals("Session extension") && !isSessionExtensionRequested) {
                    if (Math.abs(currentContentProgress - 10000) < 100) {
                        //Set the flag to true so same session extension is not requested multiple times.
                        isSessionExtensionRequested = true;
                        requestSessionExtension();
                    }
                }

            } else if (duringAd && !companionClicked) {
                if (exoPlayerInstance != null && exoPlayerInstance.getCurrentPosition() != 0) {
                    currentAdProgress = exoPlayerInstance.getCurrentPosition();
                    //Report ad video progress to Pulse SDK.
                    if (currentPulseVideoAd != null) {
                        currentPulseVideoAd.adPositionChanged(currentAdProgress / 1000);
                    }
                    updateSkipButton((int) (currentAdProgress / 1000));
                }
            }
        }
    };

    /**
     * A helper method to start a handler by assigning a callback method.
     *
     * @param handler the handler that should be started.
     */
    private void setCallBackHandler(Handler handler) {
        if (handler == playbackHandler) {
            playbackHandler.post(playbackRunnable);
        } else if (handler == contentProgressHandler) {
            contentProgressHandler.post(onEveryTimeInterval);
        }
    }

    /**
     * A helper method to stop a handler by removing its callback method.
     *
     * @param handler the handler that should be stopped.
     */
    public void removeCallback(Handler handler) {
        if (handler == playbackHandler) {
            playbackHandler.removeCallbacks(playbackRunnable);
        } else if (handler == contentProgressHandler) {
            contentProgressHandler.removeCallbacks(onEveryTimeInterval);
        }
    }

    /**
     * A helper method to update the ad skip button.
     *
     * @param currentAdPlayhead the ad playback progress.
     */
    private void updateSkipButton(int currentAdPlayhead) {
        if (currentPulseVideoAd.isSkippable() && !skipEnabled) {
            if (skipBtn.getVisibility() == View.VISIBLE) {
                int remainingTime = (int) (currentPulseVideoAd.getSkipOffset() - currentAdPlayhead);
                String skipBtnText = "Skip ad in ";
                skipBtn.setText(String.format("%s%s", skipBtnText, Integer.toString(remainingTime)));
            }
            if ((currentPulseVideoAd.getSkipOffset() <= (currentAdPlayhead))) {
                skipBtn.setText(R.string.skip_ad);
                skipEnabled = true;
                skipBtn.setOnClickListener(v -> {
                    skipBtn.setOnClickListener(null);
                    skipBtn.setVisibility(View.INVISIBLE);
                    adStarted = false;
                    adPaused = false;
                    playAd = false;
                    duringAd = false;
                    currentAdProgress = 0L;
                    currentPulseVideoAd.adSkipped();
                    nextAdPreloaded = false;
                    nextAdThumbnail.setVisibility(View.INVISIBLE);
                    nextAdThumbnail.setImageBitmap(null);
                });
            }
        }
    }

    public void removeFriendlyObstructions() {
        if ("omsdk".equals(videoItem.getContentId())) {
            // This will remove all registered friendly obstructions.
            // In case you want to remove specific list of registered friendlyObstructions use OmidAdSession.removeFriendlyObstructions(friendlyObs);
            OmidAdSession.removeAllFriendlyObstructions();
        }
    }

    public void sendEnterFullScreenEvent() {
        if (playAd) {
            currentPulseVideoAd.playerStateChanged(PlayerState.FULLSCREEN);
        }
    }

    public void sendExitFullScreenEvent() {
        if (playAd) {
            currentPulseVideoAd.playerStateChanged(PlayerState.NORMAL);
        }
    }

    /////////////////////Session extension method//////////////////////
    private void requestSessionExtension() {
        Log.i(TAG, "Request a session extension for two midrolls at 20th second.");
        //Modifying the initial ContentMetadata and RequestSetting to request for midrolls at 20 second.
        ContentMetadata updatedContentMetadata = getContentMetadata();
        updatedContentMetadata.setTags(Collections.singletonList("standard-midrolls"));
        updatedContentMetadata.setContentProviderInformation("pcode1", "embed1");
        RequestSettings updatedRequestSettings = getRequestSettings();
        updatedRequestSettings.setLinearPlaybackPositions(Collections.singletonList(20f));
        updatedRequestSettings.setInsertionPointFilter(Collections.singletonList(RequestSettings.InsertionPointType.PLAYBACK_POSITION));
        //Make a session extension request and instantiate a PulseSessionExtensionListener.
        //The onComplete callback would be called when the session is successfully extended.
        pulseSession.extendSession(updatedContentMetadata, updatedRequestSettings, () -> Log.i(TAG, "Session was successfully extended. There are now midroll ads at 20th second."));
    }

    private void onAdPlay() {
        duringAd = true;
        skipEnabled = false;
        //If the ad is played, remove the timeout handler.
        playbackHandler.removeCallbacks(playbackRunnable);

        setCallBackHandler(contentProgressHandler);
        //If the ad is resumed after being paused, call resumeAdPlayback.
        if (adPaused) {
            resumeAdPlayback();
        } else {
            //If this is the first time this ad is played, report adStarted to Pulse.
            if (!adStarted) {
                adStarted = true;
                currentPulseVideoAd.adStarted();
                //In case you want to add friendlyObstruction later on after the ad has started.
                if ("omsdk".equals(videoItem.getContentId())) {
                    if ("OM AdVerification skipAd as Friendly Obstruction after ad started".equals(videoItem.getContentTitle())) {
                        OmidAdSession.addFriendlyObstructions(friendlyObs);
                    }
                }
            }
            //If this ad is skippable, update the skip button.
            if (currentPulseVideoAd.isSkippable()) {
                skipBtn.setVisibility(View.VISIBLE);
                updateSkipButton(0);
            }
        }
    }

    /**
     * This method would be called when user return from a click through page.
     * If the ad video support seeking, it would be resumed otherwise the ad would be played from the beginning.
     */
    private void resumeAdPlayback() {
        contentProgressHandler.post(onEveryTimeInterval);
        if (currentPulseVideoAd != null) {
            initializePlayer();
            //Report ad resume to Pulse SDK.
            currentPulseVideoAd.adResumed();
            duringAd = true;
            adPaused = false;
            //If ad is skippable, update the skip button.
            if (currentPulseVideoAd.isSkippable()) {
                skipBtn.setVisibility(View.VISIBLE);
                updateSkipButton((int) (exoPlayerInstance.getCurrentPosition() / 1000));
            }
        }
    }

    private void onContentPlay() {
        setCallBackHandler(contentProgressHandler);
        //contentStarted boolean is used to ensure that contentStarted event is only reported once.
        if (contentStarted) {
            if (pulseSession != null) {
                //Report start of content playback.
                pulseSession.contentStarted();
            }
            contentStarted = false;
            duringVideoContent = true;
        }

        if (duringPause) {
            duringVideoContent = true;
            if (pulseSession != null) {
                pulseSession.contentStarted();
            }

            if (pauseImageView.getVisibility() == View.VISIBLE) {
                pauseImageView.setVisibility(View.INVISIBLE);
                if (duringPause) {
                    currentPulsePauseAd = null;
                }
            }
            duringPause = false;
        }
    }

    public void onGoingIntoBackground() {
        if (playAd) {
            Log.d(TAG, "Ad Paused");
            duringAd = false;
            adPaused = true;
            //Report ad paused to Pulse SDK.
            currentPulseVideoAd.adPaused();
        }
        if (playVideoContent) {
            duringVideoContent = false;
            Log.d(TAG, "Content Paused");
            duringAd = false;
            duringPause = true;
            if (pulseSession != null) {
                pulseSession.contentPaused();
            }
        }
    }

    public void onComingIntoForeground() {
        if (playAd) {
            onAdPlay();
        } else if (playVideoContent) {
            onContentPlay();
            Log.d(TAG, "Content Started");
        }
    }

    private void initPlayerVolumeControl() {

        playerVolumeController.setProgress(100);
        playerVolume = 1f;
        playerVolumeController.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                playerVolume = (float) progress /100;
                exoPlayerInstance.setVolume(playerVolume);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playAd) {
                    currentPulseVideoAd.playerVolumeChanged(playerVolume);
                }
            }
        });
    }

    class ExoPlayerEventListener implements Player.Listener {

        private final String TAG = ExoPlayerEventListener.class.getName();

        @Override
        public void onPlayerStateChanged(boolean playWhenReady,
                                         int playbackState) {
            String stateString;
            switch (playbackState) {
                case Player.STATE_IDLE:
                    stateString = "ExoPlayer.STATE_IDLE      -";
                    break;
                case Player.STATE_BUFFERING:
                    stateString = "ExoPlayer.STATE_BUFFERING -";
                    break;
                case Player.STATE_READY:
                    stateString = "ExoPlayer.STATE_READY     -";
                    if (!playWhenReady) {
                        if (playAd) {
                            Log.d(TAG, "Ad Paused");
                            duringAd = false;
                            adPaused = true;
                            //Report ad paused to Pulse SDK.
                            currentPulseVideoAd.adPaused();
                        }
                        if (playVideoContent) {
                            duringVideoContent = false;
                            Log.d(TAG, "Content Paused");
                            duringAd = false;
                            duringPause = true;
                            if (pulseSession != null) {
                                pulseSession.contentPaused();
                            }
                        }
                    } else {
                        if (playAd) {
                            onAdPlay();
                        } else if (playVideoContent) {
                            onContentPlay();
                            Log.d(TAG, "Content Started");
                        }
                    }
                    break;
                case Player.STATE_ENDED:
                    stateString = "ExoPlayer.STATE_ENDED     -";
                    if (playAd) {
                        playAd = false;
                        duringAd = false;
                        Log.i(TAG, "Ad playback completed.");
                        //Report Ad completion to Pulse SDK.
                        skipBtn.setVisibility(View.INVISIBLE);
                        adStarted = false;
                        adPaused = false;
                        removeCallback(contentProgressHandler);
                        currentAdProgress = 0;
                        currentPulseVideoAd.adFinished();
                        //remove next ad thumbnail when current ad is finished.
                        nextAdPreloaded = false;
                        nextAdThumbnail.setVisibility(View.INVISIBLE);
                        nextAdThumbnail.setImageBitmap(null);
                    } else if (playVideoContent) {
                        //Inform Pulse SDK about content completion.
                        Log.i(TAG, "Content playback completed.");
                        if (pulseSession != null) {
                            pulseSession.contentFinished();
                        }
                        duringVideoContent = false;
                        playVideoContent = false;
                    }

                    break;
                default:
                    stateString = "UNKNOWN_STATE             -";
                    break;
            }
            Log.d(TAG, "changed state to " + stateString
                    + " playWhenReady: " + playWhenReady);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            final String what;
            Log.e(TAG, error.getMessage() == null ? "" : error.getMessage());
            what = error.getLocalizedMessage();
            switch (error.errorCode) {
                case ExoPlaybackException.TYPE_SOURCE:
                    Log.e(TAG, "TYPE_SOURCE: " + what);
                    if (playAd) {
                        currentPulseVideoAd.adFailed(PulseAdError.REQUEST_FAILED);
                    } else if (playVideoContent) {
                        Log.i(TAG, "unknown media playback error");
                    }
                    break;

                case ExoPlaybackException.TYPE_RENDERER:
                    Log.e(TAG, "TYPE_RENDERER: " + what);
                    if (playAd) {
                        currentPulseVideoAd.adFailed(PulseAdError.REQUEST_TIMED_OUT);
                    } else if (playVideoContent) {
                        Log.i(TAG, "server connection died");
                    }
                    break;

                case ExoPlaybackException.TYPE_UNEXPECTED:
                    Log.e(TAG, "TYPE_UNEXPECTED: " + what);
                    if (playAd) {
                        currentPulseVideoAd.adFailed(PulseAdError.COULD_NOT_PLAY);
                    } else if (playVideoContent) {
                        Log.i(TAG, "generic audio playback error");
                    }
                    break;
            }
            if (playAd) {
                playAd = false;
                duringAd = false;
                playbackHandler.removeCallbacks(playbackRunnable);
            } else if (playVideoContent) {
                playVideoContent = false;
                duringVideoContent = false;
            }
        }
    }
}