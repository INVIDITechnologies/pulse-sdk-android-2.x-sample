package com.ooyala.pulseplayer.videoPlayer;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.WindowManager;
import android.widget.VideoView;

import com.ooyala.pulseplayer.R;

public class FullScreenVideoPlayerActivity extends AppCompatActivity {
    private VideoView videoView;
    int currenttime = 0;
    String Url = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        String fullScreen =  getIntent().getStringExtra("fullScreenInd");
        if("y".equals(fullScreen)){
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);

        }

        Bundle extras = getIntent().getExtras();

        if(null != extras) {
            currenttime = extras.getInt("currenttime", 0);
            Url = extras.getString("Url");
        }

        setContentView(R.layout.fullscreen_activity_viedo_player);
        videoView = findViewById(R.id.playerFullScreen);
        CustomMediaController mediaController = new CustomMediaController(this);
        mediaController.setAnchorView(videoView);
        Uri video = Uri.parse(Url);
        videoView.setMediaController(mediaController);
        videoView.setVideoURI(video);
        if(currenttime > 0) {
            if (null != videoView) {
                videoView.start();
                videoView.seekTo(currenttime);
            }
        }
    }
}