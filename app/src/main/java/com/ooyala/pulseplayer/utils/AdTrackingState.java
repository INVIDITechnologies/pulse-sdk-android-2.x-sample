package com.ooyala.pulseplayer.utils;

public class AdTrackingState {
    public boolean firstQuartile = false; // 25%
    public boolean midpoint = false;  // 50%
    public boolean thirdQuartile = false;   // 75%
    public boolean complete = false;   //100%

    public void reset() {
        firstQuartile = false;
        midpoint = false;
        thirdQuartile = false;
        complete = false;
    }
}