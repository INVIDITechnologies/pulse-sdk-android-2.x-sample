package com.ooyala.pulseplayer.videoPlayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.DownloadImageTask;
import com.ooyala.pulseplayer.utils.OnImageLoaderListener;

import java.net.URL;

/**
 * Created by Mehdi on 20/06/16.
 */
public class CustomImageView extends RelativeLayout implements OnImageLoaderListener, View.OnClickListener {

    private ImageView imageView,closeBtnImgView;
    private TextView splashResumeTxtView;
    private Context context;

    private CustomeImgViewListener mListener;

    public CustomImageView(Context context) {
        super(context);
        this.context = context;
    }

    public CustomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public CustomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;

    }

    public void setCustomeImgViewListener (CustomeImgViewListener listener) {
        mListener = listener;
    }

    public void init() {
        View v = inflate(context, R.layout.customimageview, this);
        RelativeLayout relativeLayout = (RelativeLayout) v.findViewById(R.id.pauseAdRelativeLayout);
        splashResumeTxtView = (TextView) relativeLayout.getChildAt(0);
        splashResumeTxtView.setText(getResources().getString(R.string.Splash_Resume_Message));

        closeBtnImgView = (ImageView) relativeLayout.getChildAt(1);
        closeBtnImgView.setOnClickListener(null);

        closeBtnImgView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                closeBtnImgView = null;
                imageView = null;
                mListener.onCloseBtnCLicked();
                //
            }
        });

        imageView = (ImageView) relativeLayout.getChildAt(2);
        imageView.setOnClickListener(null);

        imageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onPauseAdClicked();
            }
        });

    }

    @Override
    public void onClick(View v) {

        Log.i("Demo", "Onclosed Called : "+ v.getId());
    }

    public interface CustomeImgViewListener {
        void onCloseBtnCLicked();
        void onPauseAdClicked();
        void onImageDisplayed();
        void onImageLoadingFailed(PulseAdError error);
    }

    public void loadImage(URL url){
        new DownloadImageTask(imageView, this)
                .execute(url.toString());
    }

    @Override
    public void imageLoaded() {
        closeBtnImgView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.VISIBLE);
        mListener.onImageDisplayed();
    }

    @Override
    public void imageLoadingFailed(PulseAdError error) {
        mListener.onImageLoadingFailed(error);
    }
}

