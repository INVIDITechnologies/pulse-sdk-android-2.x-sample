package com.ooyala.pulseplayer.utils;

import com.ooyala.pulse.PulseAdError;

/**
 * Created by Mehdi on 15/06/16.
 */
public interface OnImageLoaderListener {
    void imageLoaded();

    void imageLoadingFailed(PulseAdError error);
}
