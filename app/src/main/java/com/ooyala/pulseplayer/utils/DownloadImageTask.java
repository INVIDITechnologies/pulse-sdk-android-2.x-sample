package com.ooyala.pulseplayer.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import com.ooyala.pulse.PulseAdError;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/**
 * DownloadImageTask class created to provide an asynchronous loading of an image into an imageView.
 */
public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
    private final WeakReference<ImageView> imageViewReference;
    OnImageLoaderListener listener;

    public DownloadImageTask(ImageView bmImage, OnImageLoaderListener listener) {
        // Use a WeakReference to ensure the ImageView can be garbage collected.
        imageViewReference = new WeakReference<ImageView>(bmImage);
        this.listener = listener;
    }

    protected Bitmap doInBackground(String... urls) {
        String urldisplay = urls[0];
        Bitmap mIcon11 = null;
        try {
            InputStream in = new java.net.URL(urldisplay).openStream();
            mIcon11 = BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
            listener.imageLoadingFailed(PulseAdError.NO_SUPPORTED_MEDIA_FILE);
        }
        return mIcon11;
    }

    protected void onPostExecute(Bitmap bitmap) {
        if (imageViewReference != null && bitmap != null) {
            final ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
                listener.imageLoaded();
            }
        }
    }

    //An interface created to report if the image was loaded or failed loading.
    public interface OnImageLoaderListener {
        void imageLoaded();
        void imageLoadingFailed(PulseAdError error);
    }
}

