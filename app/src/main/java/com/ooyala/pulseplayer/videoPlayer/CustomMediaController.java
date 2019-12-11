package com.ooyala.pulseplayer.videoPlayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.VideoView;

import com.ooyala.pulseplayer.PulseManager.PulseManager;
import com.ooyala.pulseplayer.R;

import java.net.URL;

/**
 * CustomMediaController class created to add a listener to the default video player for fullscreen enter and exit.
 */
public class CustomMediaController extends MediaController {
    private ImageButton fullScreen;
    private String isFullScreen;

    public CustomMediaController(Context context) {
        super(context);
    }

    public CustomMediaController(Context context, AttributeSet attr) {
        super(context, attr);
    }

    @Override
    public void setAnchorView(View view) {

        super.setAnchorView(view);

        //image button for full screen to be added to media controller
        fullScreen = new ImageButton (super.getContext());

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);

        params.gravity = Gravity.RIGHT;
        params.rightMargin = 80;
        params.topMargin = 25;
        addView(fullScreen, params);

        //fullscreen indicator from intent
        isFullScreen =  ((Activity)getContext()).getIntent().
                getStringExtra("fullScreenInd");

        if("y".equals(isFullScreen)){
            fullScreen.setImageResource(R.drawable.ic_fullscreen_exit);
        } else {
            fullScreen.setImageResource(R.drawable.ic_fullscreen);
        }


        //add listener to image button to handle full screen and exit full screen events
        fullScreen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent videointent = new Intent(getContext(), FullScreenVideoPlayerActivity.class);
                videointent.putExtra("currenttime", PulseManager.getInstance().getVideoPlayer().getCurrentPosition());
                videointent.putExtra("Url", PulseManager.getInstance().getVideoPlayer().getVideoURI().toString());
                if("y".equals(isFullScreen)){
                    videointent.putExtra("fullScreenInd", "");
                }else{
                    videointent.putExtra("fullScreenInd", "y");
                }
                ((Activity)getContext()).startActivity(videointent);
            }
        });
    }

}
