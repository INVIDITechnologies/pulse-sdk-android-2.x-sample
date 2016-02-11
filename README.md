# Ooyala Pulse SDK 2.x sample integration for Android

This project demonstrates a simple video player that requests and shows ads using the Ooyala Pulse SDK.

This project is a sample intended **only** to give a brief introduction to the Pulse SDK and help developers get started with their Android integration.

This is absolutely **not** intended to be used in production or to outline best practices, but rather a simplified way of developing your integration.


## Building

1. Download the Ooyala android SDK [here](http://support.ooyala.com/resources/mobile-and-client-sdks).

2. Copy the SDK files to the [libs folder](app/libs).

3. Open the build.gradle file in android studio.

4. Update the dependencies in [build.gradle](app/build.gradle) to match the version of your downloaded SDK.

5. Build


## Project structure

The Pulse SDK is initialized in the [PulseManager](app/src/main/java/com/ooyala/pulseplayer/PulseManager/PulseManager.java).

A [List Activity](app/src/main/java/com/ooyala/pulseplayer/List/MainActivity.java) shows a list of available videos, along with some [metadata](app/src/main/java/com/ooyala/pulseplayer/utils/VideoItem.java). When a video is selected it is opened in a [VideoPlayerActivity](app/src/main/java/com/ooyala/pulseplayer/videoPlayer/VideoPlayerActivity.java).

The VideoPlayerActivity creates an instance of [PulseSession](http://pulse-sdks.ooyala.com/android_2/latest/com/ooyala/pulse/PulseSession.html) using the [Pulse](http://pulse-sdks.ooyala.com/android_2/latest/index.html?com/ooyala/pulse/Pulse.html) class. This PulseSession informs the PulseManager through the [PulseSessionListener](http://pulse-sdks.ooyala.com/android_2/latest/com/ooyala/pulse/PulseSessionListener.html) protocol when it is time to play ads or the content.

## Demo Pulse account

This integration sample uses the following Pulse account:
```
https://pulse-demo.videoplaza.tv
```

This account is configured with a set of ad campaigns that help with testing an application that integrates with Ooyala Pulse.

You may use this account in the testing of your application. Refer to the [content library](app/src/main/res/raw/library.json) used in this sample for useful tags and categories.


## Useful information

- [The Ooyala Pulse SDK documentation](http://pulse-sdks.ooyala.com/android_2/latest/)
- [Ooyala Ad Products SDK Parameter Reference](http://support.ooyala.com/developers/ad-documentation/oadtech/ad_serving/dg/integration_sdk_parameter.html)
