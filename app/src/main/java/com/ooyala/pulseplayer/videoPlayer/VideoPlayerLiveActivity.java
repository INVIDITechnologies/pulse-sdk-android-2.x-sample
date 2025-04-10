package com.ooyala.pulseplayer.videoPlayer;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import com.ooyala.pulse.FriendlyObstruction;
import com.ooyala.pulseplayer.PulseManager.PulseManagerLive;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class VideoPlayerLiveActivity extends AppCompatActivity {
    static final int OPEN_BROWSER_REQUEST = 1365;
    public static PulseManagerLive pulseManagerLive;
    private PlayerView playerView;
    private Dialog mFullScreenDialog;
    private ImageView mFullScreenIcon;
    private FrameLayout mFullScreenButton;
    private View adView;
    private Button skipButton;
    private ImageView nextAdThumbnail;
    List<FriendlyObstruction> friendlyObs = new ArrayList<>();
    private boolean mExoPlayerFullscreen = false;
    private static final String TAG = VideoPlayerVODActivity.class.getName();
    private   UiModeManager uiMode;

    @UnstableApi
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_player);

        //Get the selected videoItem from the bundled information.
        final VideoItem videoItem = getSelectedVideoItem();
        skipButton = (Button) findViewById(R.id.skipBtn);
        nextAdThumbnail = (ImageView) findViewById(R.id.nextAdThumbnail);
        skipButton.setVisibility(View.INVISIBLE);
        playerView = findViewById(R.id.exoplayer);
        playerView.showController();
        playerView.setControllerShowTimeoutMs(-1);
        uiMode = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        adView = findViewById(R.id.exo_content_frame);

        if(uiMode.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            skipButton.setFocusableInTouchMode(true);
            skipButton.setFocusable(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                skipButton.setFocusedByDefault(true);
            }
            skipButton.requestFocus();
        } else {
        }

        //Instantiate Pulse manager with selected data.
        pulseManagerLive = new PulseManagerLive(videoItem, playerView, adView, skipButton, this, this);

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
}
