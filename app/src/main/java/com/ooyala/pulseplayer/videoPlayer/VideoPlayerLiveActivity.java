package com.ooyala.pulseplayer.videoPlayer;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import com.ooyala.pulseplayer.PulseManager.PulseManagerLive;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;

@UnstableApi public class VideoPlayerLiveActivity extends AppCompatActivity {
    public static PulseManagerLive pulseManagerLive;
    private PlayerView playerView;
    private Button skipButton;
    private Button fetchNextBreakBtn;
    private Button showAdsBtn;
    private Button extendSessionBtn;
    private ImageButton exoPlayBtn;
    private ImageButton exoPauseBtn;
    private UiModeManager uiMode;

    @OptIn(markerClass = UnstableApi.class)
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        try {
            setContentView(R.layout.activity_video_player_live);
        } catch (Exception e) {
            Log.e("Alpha", "Layout inflation error", e);
            for (Throwable t = e; t != null; t = t.getCause()) {
                Log.e("Alpha", "Caused by: ", t);
            }
            throw e; // This ensures the full error appears in Logcat
        }
        //Get the selected videoItem from the bundled information.
        final VideoItem videoItem = getSelectedVideoItem();
        playerView = findViewById(R.id.exoPlayerView);

        skipButton = findViewById(R.id.skipBtn);
        fetchNextBreakBtn = findViewById(R.id.adBreak);
        showAdsBtn = findViewById(R.id.showAds);
        extendSessionBtn = findViewById(R.id.extendSession);
        exoPlayBtn = findViewById(R.id.exo_play);
        exoPauseBtn = findViewById(R.id.exo_pause);


        playerView.showController();
        playerView.setControllerShowTimeoutMs(-1);
        uiMode = (UiModeManager) getSystemService(UI_MODE_SERVICE);

        if (uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            skipButton.setFocusableInTouchMode(true);
            skipButton.setFocusable(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                skipButton.setFocusedByDefault(true);
            }
            skipButton.requestFocus();
        } else {
        }

        //Instantiate Pulse manager with selected data.
        pulseManagerLive = new PulseManagerLive(videoItem, playerView,this, skipButton, fetchNextBreakBtn, showAdsBtn, extendSessionBtn, exoPlayBtn, exoPauseBtn);

        //Assign a clickThroughCallback to manage opening the browser when an Ad is clicked.

    }

    public VideoItem getSelectedVideoItem() {
        VideoItem selectedVideoItem = new VideoItem();

        selectedVideoItem.setTags(getIntent().getExtras().getStringArray("contentMetadataTags"));
        selectedVideoItem.setMidrollPosition(getIntent().getExtras().getIntArray("midrollPositions"));
        selectedVideoItem.setContentTitle(getIntent().getExtras().getString("contentTitle"));
        selectedVideoItem.setContentUrl(getIntent().getExtras().getString("contentUrl"));

        return selectedVideoItem;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(pulseManagerLive != null){
            pulseManagerLive.releasePlayer();
            pulseManagerLive = null;
        }
    }
}
