package com.ooyala.pulseplayer;

import android.app.Application;

import com.ooyala.pulse.Pulse;

public class PulseApplication  extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Pulse.setPulseHost("https://pulse-demo.videoplaza.tv", null, null);
    }
}
