package com.ooyala.pulseplayer.videoPlayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.ooyala.pulse.PulseAdError;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.DownloadImageTask;

import java.net.URL;

/**
 * CustomCompanionBannerView class containing a custom imageView with callbacks for asynchronous image loading.
 */
public class CustomCompanionBannerView extends LinearLayout implements DownloadImageTask.OnImageLoaderListener{

  private Context context;
  ImageView imageView;

  private CustomCompanionBannerViewListener mListener;

  public CustomCompanionBannerView(Context context) {
    super(context);
    this.context = context;
  }

  public CustomCompanionBannerView(Context context, AttributeSet attrs) {
    super(context, attrs);
    this.context = context;
  }

  public CustomCompanionBannerView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    this.context = context;
  }

  public void init() {
    View v = inflate(context, R.layout.customcompanionbanner, this);
    LinearLayout linearLayout = (LinearLayout) v.findViewById(R.id.customCompanionBannerLayout);

    imageView = (ImageView) linearLayout.getChildAt(0);
    imageView.setOnClickListener(null);

    imageView.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        mListener.onCompanionBannerClicked();
      }
    });

  }

  //Enable Setting a listener for loading image events and closing the view events.
  public void setCustomCompanionBannerViewListener (CustomCompanionBannerView.CustomCompanionBannerViewListener listener) {
    mListener = listener;
  }

  @Override
  public void imageLoaded() {
    imageView.setVisibility(View.VISIBLE);
    mListener.onCompanionBannerDisplayed();
  }

  @Override
  public void imageLoadingFailed(PulseAdError error) {
    Log.i("Pulse Demo Player", "Loading companion banner's image failed");
  }

  public void loadImage(URL url){
    new DownloadImageTask(imageView, this)
            .execute(url.toString());
  }


  //An interface created to report events related to image.
  public interface CustomCompanionBannerViewListener {
    void onCompanionBannerClicked();
    void onCompanionBannerDisplayed();
  }
}
