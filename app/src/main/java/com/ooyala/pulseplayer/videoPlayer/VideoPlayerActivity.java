package com.ooyala.pulseplayer.videoPlayer;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;

import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;

import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulseplayer.PulseManager.PulseManager;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.VideoItem;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;


/**
 * An activity for playing ad video and content. This activity employs a PulseManager instance to manage the Pulse session.
 */
public class VideoPlayerActivity extends AppCompatActivity {
    static final int OPEN_BROWSER_REQUEST = 1365;
    public static PulseManager pulseManager;
    private PlayerView playerView;
    private Dialog mFullScreenDialog;
    private ImageView mFullScreenIcon;
    private FrameLayout mFullScreenButton;
    private View adView;
    List<View> friendlyObs = new ArrayList<>();
    private boolean mExoPlayerFullscreen = false;
    private static final String TAG = VideoPlayerActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video_player);


        //Get the selected videoItem from the bundled information.
        final VideoItem videoItem = getSelectedVideoItem();
        Button skipButton = (Button) findViewById(R.id.skipBtn);
        skipButton.setVisibility(View.INVISIBLE);

        playerView = findViewById(R.id.exoplayer);
        playerView.showController();
        playerView.setControllerShowTimeoutMs(-1);

        adView = findViewById(R.id.exo_content_frame);


        friendlyObs.add(findViewById(R.id.skipBtn));
        friendlyObs.add(findViewById(R.id.exo_controller));

        initFullscreenDialog();
        initFullscreenButton();

        //Create an instance of CustomImageView that is responsible for displaying pause ad.
        CustomImageView imageView = (CustomImageView) findViewById(R.id.pauseAdLayout);

        //Create two instances of CustomCompanionBannerView that are responsible for displaying companion banner ads.
        CustomCompanionBannerView companionBannerViewTop = (CustomCompanionBannerView) findViewById(R.id.companionTop);
        CustomCompanionBannerView companionBannerViewBottom = (CustomCompanionBannerView) findViewById(R.id.companionBottom);


        //Instantiate Pulse manager with selected data.
        pulseManager = new PulseManager(videoItem, playerView, adView, friendlyObs, skipButton, imageView, companionBannerViewTop, companionBannerViewBottom, this, this);

        //Assign a clickThroughCallback to manage opening the browser when an Ad is clicked.

        pulseManager.setOnClickThroughCallback(new PulseManager.ClickThroughCallback() {
            @Override
            public void onClicked(PulseVideoAd ad) {
                if(ad.getClickthroughURL() != null){
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(ad.getClickthroughURL().toString()));
                    startActivityForResult(intent, OPEN_BROWSER_REQUEST);
                } else{
                    pulseManager.returnFromClickThrough();
                }
            }

            @Override
            public void onPauseAdClicked(URL clickThroughUrl) {
                if (clickThroughUrl != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(clickThroughUrl.toString()));
                    startActivityForResult(intent, OPEN_BROWSER_REQUEST);
                } else {
                    pulseManager.returnFromClickThrough();
                }
            }

            @Override
            public void onCompanionAdClicked(URL clickThroughUrl) {
                if (clickThroughUrl != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(clickThroughUrl.toString()));
                    startActivityForResult(intent, OPEN_BROWSER_REQUEST);
                } else {
                    pulseManager.returnFromClickThrough();
                }
            }
        });
    }

/*    @Override
    protected void onStart() {
        super.onStart();
        pulseManager.initializePlayer();
            }*/
    @Override
    public void onPause() {
        super.onPause();
        if (mFullScreenDialog != null)
            mFullScreenDialog.dismiss();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        pulseManager.removeCallback(PulseManager.contentProgressHandler);
       pulseManager.releasePlayer();
    }

    @Override
    public void onStop(){
        super.onStop();
        pulseManager.removeCallback(PulseManager.contentProgressHandler);
        pulseManager.releasePlayer();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((resultCode == RESULT_OK || resultCode == RESULT_CANCELED) && requestCode == OPEN_BROWSER_REQUEST) {
            pulseManager.returnFromClickThrough();
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        initFullscreenDialog();
        initFullscreenButton();
        pulseManager.initializePlayer();
        if (mExoPlayerFullscreen) {
            ((ViewGroup) playerView.getParent()).removeView(playerView);
            mFullScreenDialog.addContentView(playerView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            mFullScreenIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.exo_controls_fullscreen_exit));
            mFullScreenDialog.show();
        }
 //       pulseManager.setCallBackHandler(PulseManager.contentProgressHandler);
    }

    /**
     * Create a VideoItem from the bundled information send to this activity.
     * @return The created {@link VideoItem}.
     */
    public VideoItem getSelectedVideoItem() {
        VideoItem selectedVideoItem = new VideoItem();

        selectedVideoItem.setTags(getIntent().getExtras().getStringArray("contentMetadataTags"));
        selectedVideoItem.setMidrollPosition(getIntent().getExtras().getIntArray("midrollPositions"));
        selectedVideoItem.setContentTitle(getIntent().getExtras().getString("contentTitle"));
        selectedVideoItem.setContentId(getIntent().getExtras().getString("contentId"));
        selectedVideoItem.setContentUrl(getIntent().getExtras().getString("contentUrl"));
        selectedVideoItem.setCategory(getIntent().getExtras().getString("category"));

        return selectedVideoItem;
    }

    protected void initFullscreenDialog() {

        mFullScreenDialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {
            public void onBackPressed() {
                if (mExoPlayerFullscreen)
                    closeFullscreenDialog();
                super.onBackPressed();
            }
        };
    }
    private void openFullscreenDialog() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        ((ViewGroup) playerView.getParent()).removeView(playerView);
        mFullScreenDialog.addContentView(playerView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mFullScreenIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.exo_controls_fullscreen_exit));
        mExoPlayerFullscreen = true;
        mFullScreenDialog.show();
        pulseManager.sendEnterFullScreenEvent();
    }

    private void closeFullscreenDialog() {

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        ((ViewGroup) playerView.getParent()).removeView(playerView);
        ((FrameLayout) findViewById(R.id.main_media_frame)).addView(playerView);
        mExoPlayerFullscreen = false;
        mFullScreenDialog.dismiss();
        mFullScreenIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.exo_controls_fullscreen_enter));
        pulseManager.sendExitFullScreenEvent();
    }


    protected void initFullscreenButton() {

        PlayerControlView controlView = playerView.findViewById(R.id.exo_controller);
        mFullScreenIcon = controlView.findViewById(R.id.exo_fullscreen_icon);
        mFullScreenButton = controlView.findViewById(R.id.exo_fullscreen_button);
        mFullScreenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mExoPlayerFullscreen)
                    openFullscreenDialog();
                else
                    closeFullscreenDialog();
            }
        });
    }
}
