package com.ooyala.pulseplayer.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import com.ooyala.pulse.PulseAdError;

import java.io.InputStream;

/**
 * Created by Mehdi on 14/06/16.
 */
public class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;
        OnImageLoaderListener listener;

        public DownloadImageTask(ImageView bmImage, OnImageLoaderListener listener) {
            this.bmImage = bmImage;
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

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
            listener.imageLoaded();
        }
    }
