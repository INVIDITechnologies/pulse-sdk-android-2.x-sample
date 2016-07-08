package com.ooyala.pulseplayer.videoPlayer;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.VideoView;


/**
 * CustomVideoView class created to add a listener to the default video player
 */

public class CustomVideoView extends VideoView {

    private PlayPauseListener mListener;
    private Uri uri;
    private boolean contentPaused = false;


    public CustomVideoView(Context context) {
        super(context);
    }
    public CustomVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    //Enable Setting a listener for play/pause events.
    public void setMediaStateListener(PlayPauseListener listener) {
        mListener = listener;
    }

    @Override
    public void pause() {
        super.pause();
        contentPaused = true;
        if (mListener != null) {
            mListener.onPause();
        }
    }

    @Override
    public void start() {
        super.start();
        if (contentPaused) {
            contentPaused = false;
            if (mListener != null) {
                mListener.onResume();
            }
        }
    }

    @Override
    public void setVideoURI (Uri uri)
    {
        super.setVideoURI(uri);
        this.uri = uri;
    }

    public Uri getVideoURI ()
    {
        return uri;
    }

    //An interface created to report when the content is played, paused, and resumed.
    public interface PlayPauseListener {
        void onPlay();
        void onPause();
        void onResume();
    }

    public void play() {
        start();
        if (mListener != null){
            mListener.onPlay();
        }
    }
}


