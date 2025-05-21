package com.ooyala.pulseplayer;

import android.app.Application;

import com.ooyala.pulse.Pulse;

public class PulseApplication  extends Application {

    @Override
    public void onCreate() {
        super.onCreate();


        // Initialize the Pulse SDK with "setPulseHost(Host, Device Container, Persistent Id) in Application class of your project. (Recommended way, so it can work with lifecycles of all TV and Mobile versions)"
        // Host:
        //     Your Pulse account host
        // Device Container:
        //     Device container in INVIDI Pulse is used for targeting and
        //     reporting purposes. This device container attribute is only used
        //     if you want to override the Pulse device detection algorithm on the
        //     Pulse ad server. This should only be set if normal device detection
        //     does not work and only after consulting INVIDI personnel. An incorrect
        //     device container value can result in no ads being served or incorrect
        //     ad delivery and reports.
        // Persistent Id:
        //     The persistent identifier is used to identify the end user and is the
        //     basis for frequency capping, uniqueness, DMP targeting information and
        //     more. Use Apple's advertising identifier (IDFA), or your own unique
        //     user identifier here.
        // Refer to:
        //     http://support.ooyala.com/developers/ad-documentation/oadtech/ad_serving/dg/integration_sdk_parameter.html

        Pulse.setPulseHost("https://pulse-demo.videoplaza.tv", null, null);
    }
}
