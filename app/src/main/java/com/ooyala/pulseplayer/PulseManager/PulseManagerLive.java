package com.ooyala.pulseplayer.PulseManager;

import static com.google.android.exoplayer2.source.MediaSource.*;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSourceFactory;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.ui.PlayerView;

import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Util;
import com.ooyala.pulse.ContentMetadata;
import com.ooyala.pulse.Error;
import com.ooyala.pulse.FriendlyObstruction;
import com.ooyala.pulse.Pulse;
import com.ooyala.pulse.PulseLiveAdBreak;
import com.ooyala.pulse.PulseLiveSession;
import com.ooyala.pulse.PulseLiveSessionListener;
import com.ooyala.pulse.PulsePauseAd;
import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulse.RequestSettings;
import com.ooyala.pulse.ResponseHeader;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;
import com.ooyala.pulseplayer.videoPlayer.CustomCompanionBannerView;
import com.ooyala.pulseplayer.videoPlayer.CustomImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@UnstableApi
public class PulseManagerLive implements PulseLiveSessionListener {

    private PulseLiveSession pulseLiveSession;
    private PulseLiveAdBreak adBreak;
    private List<PulseVideoAd> pulseVideoAdList;
    private PlayerView playerView;
    private View adView;
    private ImageView nextAdThumbnail;
    private ExoPlayer exoPlayer;
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
    private PulseManager.ClickThroughCallback clickThroughCallback;
    private List<FriendlyObstruction> friendlyObs;

    private int currentWindow = 0;
    private boolean playWhenReady = true;
    private static final String TAG = "Pulse Demo Player Live";
    static final String USER_AGENT = "PulsePlayerLive";
    private DataSource.Factory mediaSourceFactory;

    public PulseManagerLive(VideoItem videoItem, PlayerView playerView, View adView, Button skipButton, Activity activity, Context context) {
        this.videoItem = videoItem;
        this.playerView = playerView;
        this.skipBtn = skipButton;
        this.adView = adView;

        this.activity = activity;
        this.context = context;

        playerVolumeController = activity.findViewById(R.id.volume_controller);
        initPlayerVolumeControl();
        // Create and start a pulse session
        pulseLiveSession = Pulse.createLiveSession(getContentMetadata(), getRequestSettings(), this);
        adBreak = pulseLiveSession.getAdBreak(RequestSettings.AdBreakType.PREROLL);
        //pulseVideoAdList = adBreak.getAllLinearAds();

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
        mediaSourceFactory = new DefaultDataSourceFactory(activity.getApplicationContext(), null,
                new DefaultHttpDataSource.Factory().setUserAgent(userAgent));

        DashMediaSource videoSource = new DashMediaSource.Factory(mediaSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoItem.getContentUrl()));

        exoPlayer.setMediaSource(videoSource);
        exoPlayer.prepare();
        exoPlayer.play();
        exoPlayer.setPlayWhenReady(true);
    }


    private void initPlayerVolumeControl() {

        playerVolumeController.setProgress(100);
        playerVolume = 1f;
        playerVolumeController.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                playerVolume = (float) progress /100;
                exoPlayer.setVolume(playerVolume);
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
    public void illegalOperationOccurred(Error error) {

    }
}
