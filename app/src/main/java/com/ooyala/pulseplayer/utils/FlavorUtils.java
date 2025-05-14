package com.ooyala.pulseplayer.utils;

import androidx.annotation.RawRes;

import com.ooyala.pulseplayer.BuildConfig;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.videoPlayer.VideoPlayerLiveActivity;
import com.ooyala.pulseplayer.videoPlayer.VideoPlayerVODActivity;

public class FlavorUtils {

    /**
     * Returns the appropriate activity class based on the current product flavor.
     *
     * @return Class<?> corresponding to the activity for the current flavor.
     * @throws IllegalStateException if the flavor is unknown.
     */
    public static Class<?> getVideoPlayerActivityClass() {
        switch (BuildConfig.CONTENT_MODE.toUpperCase()) {
            case "VOD":
                return VideoPlayerVODActivity.class;
            case "LIVE":
                return VideoPlayerLiveActivity.class;
            default:
                throw new IllegalStateException("Unknown mode: " + BuildConfig.CONTENT_MODE);
        }
    }

    /**
     * Returns the raw resource ID of the JSON file based on the current product flavor.
     *
     * @return The raw resource ID for the flavor's video library JSON.
     * @throws IllegalStateException if the flavor is unknown.
     */
    @RawRes
    public static int getVideoLibraryRawResId() {
        switch (BuildConfig.CONTENT_MODE.toUpperCase()) {
            case "VOD":
                return R.raw.library;
            case "LIVE":
                return R.raw.library_live;
            default:
                throw new IllegalStateException("Unknown mode: " + BuildConfig.CONTENT_MODE);
        }
    }
}

