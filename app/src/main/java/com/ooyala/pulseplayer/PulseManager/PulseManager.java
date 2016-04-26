package com.ooyala.pulseplayer.PulseManager;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;

import com.ooyala.adtech.ContentMetadata;
import com.ooyala.adtech.MediaFile;
import com.ooyala.adtech.RequestSettings;
import com.ooyala.pulse.Pulse;
import com.ooyala.pulse.PulseAdBreak;
import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulse.PulseSession;
import com.ooyala.pulse.PulseSessionListener;
import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulseplayer.BuildConfig;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.VideoItem;
import com.ooyala.pulseplayer.videoPlayer.CustomVideoView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



/**
 * A manager class responsible for communicating with Pulse SDK through implementation of PulseSessionListener.
 */
public class PulseManager implements PulseSessionListener {
    private PulseSession pulseSession;
    private PulseVideoAd currentPulseVideoAd;

    private CustomVideoView videoPlayer;
    private Uri videoContentUri;
    private MediaController controlBar;
    private Button skipBtn;
    private long currentContentProgress = 0;
    private boolean duringVideoContent = false, duringAd = false;
    private boolean contentStarted = false;
    private boolean adPaused = false;
    private boolean adStarted = false;
    private Activity activity;
    private VideoItem videoItem = new VideoItem();
    public ClickThroughCallback clickThroughCallback;
    private long adPlaybackTimeout = 0;
    private float currentAdProgress = 0;
    private String skipBtnText = "Skip ad in ";
    private boolean skipEnabled = false;

    public static Handler contentProgressHandler;
    public static Handler playbackHandler = new Handler();

    public PulseManager(VideoItem videoItem, CustomVideoView videoPlayer, MediaController controllBar, Button skipButton, Activity activity) {
        this.videoItem = videoItem;
        this.videoPlayer = videoPlayer;
        this.controlBar = controllBar;
        this.skipBtn = skipButton;
        this.activity = activity;

        // Create and start a pulse session
        pulseSession = Pulse.createSession(getContentMetadata(), getRequestSettings());
        pulseSession.startSession(this);

        controlBar.setMediaPlayer(videoPlayer);
        videoPlayer.setMediaController(controlBar);
        videoContentUri = Uri.parse(videoItem.getContentUrl());

        //Initiating the handler to track the progress of content/ad playback.
        contentProgressHandler = new Handler();
        contentProgressHandler.post(onEveryTimeInterval);
    }

    /////////////////////PulseSessionListener methods////////////

    /**
     * Pulse SDK calls this method when content should be played/resumed.
     */
    @Override
    public void startContentPlayback() {
        //Setup video player for content playback.
        videoPlayer.setVideoURI(videoContentUri);
        controlBar.setVisibility(View.VISIBLE);
        videoPlayer.setMediaStateListener(null);
        videoPlayer.setOnTouchListener(null);
        videoPlayer.setOnPreparedListener(null);
        videoPlayer.setOnCompletionListener(null);
        contentStarted = true;

        playVideoContent();
    }

    /**
     * Pulse SDK calls this method to signal an AdBreak.
     */
    @Override
    public void startAdBreak(PulseAdBreak pulseAdBreak) {
        //Pause the content playback and remove the player listener.
        Log.i("Pulse Demo Player", "Ad break started.");
        duringAd = false;
        videoPlayer.pause();
        videoPlayer.setMediaStateListener(null);
        videoPlayer.setOnPreparedListener(null);
        videoPlayer.setOnCompletionListener(null);
        videoPlayer.setOnErrorListener(null);
        duringVideoContent = false;
    }

    /**
     * Pulse SDK calls this method to signal the ad playback.
     *
     * @param pulseVideoAd The {@link PulseVideoAd} that should be displayed.
     * @param timeout      The timeout for displaying the ad.
     */
    @Override
    public void startAdPlayback(PulseVideoAd pulseVideoAd, float timeout) {
        currentPulseVideoAd = pulseVideoAd;
        adPlaybackTimeout = (long) timeout;
        playAdContent(timeout, pulseVideoAd);
    }

    /**
     * Pulse SDK calls method to signal session completion.
     */
    @Override
    public void sessionEnded() {
        Log.i("Pulse Demo Player", "Session ended");
        duringVideoContent = false;
        duringAd = false;
        currentContentProgress = 0;
        removeCallback(contentProgressHandler);
        if (activity != null) {
            activity.finish();
        }
    }

    /**
     * Pulse SDK calls this method to inform an incorrect/out of order reported event.
     *
     * @param error The produced error due to incorrect event report.
     */
    @Override
    public void illegalOperationOccurred(com.ooyala.adtech.Error error) {
        // In debug mode a runtime exception would be thrown in order to find and
        // correct mistakes in the integration.
        if (BuildConfig.DEBUG) {
            throw new RuntimeException(error.getMessage());
        } else {
            // Don't know how to recover from this, stop the session and continue
            // with the content.
            pulseSession.stopSession();
            pulseSession = null;
            startContentPlayback();
        }
    }

    /////////////////////Playback helper////////////////////

    /**
     * Play/resume the selected video content.
     */
    public void playVideoContent() {
        controlBar.setVisibility(View.VISIBLE);
        //Assign a listener to the player to monitor its play/pause event.
        videoPlayer.setMediaStateListener(new CustomVideoView.PlayPauseListener() {
            @Override
            public void onPlay() {
                //contentStarted boolean is used to ensure that contentStarted event is only reported once.
                if (contentStarted) {
                    //Report start of content playback.
                    pulseSession.contentStarted();
                    contentStarted = false;
                    Log.i("Pulse Demo Player", "Content playback started.");
                } else {
                    Log.i("Pulse Demo Player", "Content playback resumed.");
                }
                duringVideoContent = true;
            }

            @Override
            public void onPause() {

            }
        });

        //Assign an onPreparedListener to resume from previous progress.
        videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                videoPlayer.seekTo((int) (currentContentProgress));
                videoPlayer.play();
            }
        });

        //Assign an OnCompletedListener to signal content finish to Pulse SDK.
        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                //Inform Pulse SDK about content completion.
                Log.i("Pulse Demo Player", "Content playback completed.");
                pulseSession.contentFinished();
                duringVideoContent = false;
            }
        });

        //Assign an OnErrorListener to log content playback error.
        videoPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                        Log.i("Pulse Demo Player", "unknown media playback error");
                        break;
                    case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                        Log.i("Pulse Demo Player", "server connection died");
                        break;
                    default:
                        Log.i("Pulse Demo Player", "generic audio playback error");
                        break;
                }
                duringVideoContent = false;
                return true;
            }
        });

    }

    /**
     * Try to play the provided ad.
     *
     * @param timeout The timeout for ad playback.
     * @param pulseVideoAd   The ad video.
     */
    public void playAdContent(float timeout, final PulseVideoAd pulseVideoAd) {
        controlBar.setVisibility(View.INVISIBLE);
        //Configure a handler to monitor playback timeout.
        playbackHandler.postDelayed(playbackRunnable, (long) (timeout * 1000));
        String adUri = selectAppropriateMediaFile(pulseVideoAd.getMediaFiles()).getURI().toString();
        videoPlayer.setVideoURI(Uri.parse(adUri));

        videoPlayer.setMediaStateListener(new CustomVideoView.PlayPauseListener() {
            @Override
            public void onPlay() {
                duringAd = true;
                skipEnabled = false;
                //If the ad is played, remove the timeout handler.
                playbackHandler.removeCallbacks(playbackRunnable);
                //If the ad is resumed after being paused, call resumeAdPlayback.
                if (adPaused) {
                    videoPlayer.setMediaStateListener(null);
                    resumeAdPlayback();
                } else {
                    //If this is the first time this ad is played, report adStarted to Pulse.
                    if (!adStarted) {
                        adStarted = true;
                        currentPulseVideoAd.adStarted();
                    }
                    //If this ad is skippable, update the skip button.
                    if (pulseVideoAd.isSkippable()) {
                        skipBtn.setVisibility(View.VISIBLE);
                        updateSkipButton(0);
                    }
                }
            }

            @Override
            public void onPause() {
                duringAd = false;
                //Report ad paused to Pulse SDK.
                pulseVideoAd.adPaused();
                adPaused = true;
            }
        });

        videoPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                videoPlayer.play();
            }
        });

        //Assign an onTouchListener to player while displaying ad to support click through event.
        videoPlayer.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (duringAd) {
                        //Added to prevent click on the ads that are nor loaded yet which would prevent "ad paused before the ad played" error.
                        videoPlayer.pause();
                        duringAd = false;
                        //Report ad clicked to Pulse SDK.
                        currentPulseVideoAd.adClickThroughTriggered();
                        videoPlayer.setOnTouchListener(null);
                        clickThroughCallback.onClicked(currentPulseVideoAd);
                        Log.i("Pulse Demo Player", "ClickThrough occurred.");
                    }
                }
                return false;
            }
        });

        //Assign an onErrorListener to report the occurred error to Pulse SDK.
        videoPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                        Log.i("Pulse Demo Player", "unknown media playback error");
                        currentPulseVideoAd.adFailed(PulseAdError.NO_SUPPORTED_MEDIA_FILE);

                        break;
                    case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                        Log.i("Pulse Demo Player", "server connection died");
                        currentPulseVideoAd.adFailed(PulseAdError.REQUEST_TIMED_OUT);
                        break;
                    default:
                        Log.i("Pulse Demo Player", "generic audio playback error");
                        currentPulseVideoAd.adFailed(PulseAdError.COULD_NOT_PLAY);
                        break;
                }
                duringAd = false;
                playbackHandler.removeCallbacks(playbackRunnable);
                return true;
            }
        });

        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                duringAd = false;
                Log.i("Pulse Demo Player", "Ad playback completed.");
                //Report Ad completion to Pulse SDK.
                skipBtn.setVisibility(View.INVISIBLE);
                currentPulseVideoAd.adFinished();
                adStarted = false;
                adPaused = false;
            }
        });


    }


    /**
     * This method would be called when user return from a click through page.
     * If the ad video support seeking, it would be resumed otherwise the ad would be played from the beginning.
     */
    public void resumeAdPlayback() {
        contentProgressHandler.post(onEveryTimeInterval);
        if (currentPulseVideoAd != null) {
            videoPlayer.setMediaStateListener(null);
            videoPlayer.setOnTouchListener(null);
            videoPlayer.setOnPreparedListener(null);
            //Report ad resume to Pulse SDK.
            currentPulseVideoAd.adResumed();
            if(!adStarted){
                playbackHandler.postDelayed(playbackRunnable, (adPlaybackTimeout * 1000));
            }
            videoPlayer.seekTo((int) (currentAdProgress));
            videoPlayer.play();


            //If ad is skippable, update the skip button.
            if (currentPulseVideoAd.isSkippable()) {
                skipBtn.setVisibility(View.VISIBLE);
                updateSkipButton(videoPlayer.getCurrentPosition() / 1000);
            }

            videoPlayer.setMediaStateListener(new CustomVideoView.PlayPauseListener() {
                @Override
                public void onPlay() {
                    controlBar.setVisibility(View.INVISIBLE);
                    videoPlayer.setMediaStateListener(null);
                    playbackHandler.removeCallbacks(playbackRunnable);
                    duringAd = true;
                    currentPulseVideoAd.adResumed();
                }

                @Override
                public void onPause() {
                    currentPulseVideoAd.adPaused();
                }
            });
        }
    }


    ///////////////////Helper methods//////////////////////

    /**
     * An ad contains a list of media file with different dimensions and bit rates.
     * In this example this method selects the media file with the highest bit rate
     * but in a production application the best media file should be selected based
     * on resolution/bandwidth/format considerations.
     *
     * @param potentialMediaFiles A list of available mediaFiles.
     * @return the selected media file.
     */
    MediaFile selectAppropriateMediaFile(List<MediaFile> potentialMediaFiles) {
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

    /**
     * Create an instance of RequestSetting from the selected videoItem.
     *
     * @return The created {@link RequestSettings}
     */
    private RequestSettings getRequestSettings() {
        RequestSettings newRequestSettings = new RequestSettings();
        List<RequestSettings.InsertionPointType> filter = new ArrayList<>();
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
        contentMetadata.setCategory(videoItem.getCategory());
        contentMetadata.setTags(new ArrayList<>(Arrays.asList(videoItem.getTags())));
        return contentMetadata;
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
     * A helper method to start a handler by assigning a callback method.
     *
     * @param handler the handler that should be started.
     */
    public void setCallBackHandler(Handler handler){
        if (handler == playbackHandler) {
            playbackHandler.post(playbackRunnable);
        } else if (handler == contentProgressHandler) {
            contentProgressHandler.post(onEveryTimeInterval);
        }
    }

    /**
     * A helper method to update the ad skip button.
     *
     * @param currentAdPlayhead the ad playback progress.
     */
    private void updateSkipButton(int currentAdPlayhead){
        if(currentPulseVideoAd.isSkippable() && !skipEnabled){
            if (skipBtn.getVisibility() == View.VISIBLE){
                int remainingTime = (int)(currentPulseVideoAd.getSkipOffset() - currentAdPlayhead);
                skipBtn.setText(skipBtnText + Integer.toString(remainingTime));
            }
            if((currentPulseVideoAd.getSkipOffset() <= (currentAdPlayhead))){
                skipBtn.setText(R.string.skip_ad);
                skipEnabled = true;
                skipBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        skipBtn.setOnClickListener(null);
                        skipBtn.setVisibility(View.INVISIBLE);
                        currentPulseVideoAd.adSkipped();
                        adStarted = false;
                        adPaused = false;
                    }
                });
            }

        }
    }

    ////////////////////click through related methods///////////
    public void returnFromClickThrough() {
        Log.i("Pulse Demo Player","returnFromClickThrough is called");
        resumeAdPlayback();
    }

    public void setOnClickThroughCallback(ClickThroughCallback callback) {
        clickThroughCallback = callback;
    }

    public interface ClickThroughCallback {
        void onClicked(PulseVideoAd ad);
    }

    /////////////////////Runnable methods//////////////////////
    /**
     * A runnable responsible for monitoring ad playback timeout.
     */
    public Runnable playbackRunnable = new Runnable() {
        @Override
        public void run() {
            // Timeout for ad playback is reached and it should be reported to Pulse SDK.
            Log.i("Pulse Demo Player", "Time out for ad playback is reached");
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
    public Runnable onEveryTimeInterval = new Runnable() {
        @Override
        public void run() {
            //Time interval in milliseconds to check playback progress.
            int timeInterval = 200;
            contentProgressHandler.postDelayed(onEveryTimeInterval, timeInterval);
            if (duringVideoContent) {
                if (videoPlayer.getCurrentPosition() != 0) {
                    currentContentProgress = videoPlayer.getCurrentPosition();
                    //Report content progress to Pulse SDK. This progress would be used to trigger ad break.
                    pulseSession.contentPositionChanged(currentContentProgress / 1000);
                }

            } else if (duringAd) {
                if (videoPlayer.getCurrentPosition() != 0) {
                    currentAdProgress = videoPlayer.getCurrentPosition();
                    //Report ad video progress to Pulse SDK.
                    currentPulseVideoAd.adPositionChanged(currentAdProgress / 1000);
                    updateSkipButton((int)(currentAdProgress / 1000));
                }
            }
        }
    };
}
