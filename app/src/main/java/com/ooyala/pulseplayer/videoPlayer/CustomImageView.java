package com.ooyala.pulseplayer.videoPlayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.DownloadImageTask;

import java.net.URL;

/**
 * CustomImageView class containing a custom imageView with callbacks for asynchronous image loading.
 */
public class CustomImageView extends RelativeLayout implements DownloadImageTask.OnImageLoaderListener {

    private ImageView imageView,closeBtnImgView;
    private TextView splashResumeTxtView;
    private Context context;

    private CustomImgViewListener mListener;

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
    //Enable Setting a listener for loading image events and closing the view events.
    public void setCustomImgViewListener(CustomImgViewListener listener) {
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

    //An interface created to report events related to image.
    public interface CustomImgViewListener {
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

