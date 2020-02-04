#Test OpenMeasurementSDK Integration:
In order to verify integration of OMSDK with our Android SDK, we have executed many tests using this sample app. 
Below mentioned test sets can be considered as a reference for clients who wants to use OMSDK integrated with our Android SDK.

###1. Test cases mentioned in below document that are applicable for Video Ads can be executed to verify the integration of Open Measurement with Android SDK.
<https://s3-us-west-2.amazonaws.com/omsdk-files/docs/Open+Measurement+SDK+Integration+Test+Cases+1.2.pdf>

###2. Below are the scenarios executed to verify the "PercentageInView" value for "Impression" and "goemetryChanges" events:

- 2.1 From the content list appeared on starting application, select "OM AdVerification with skipAd as Obstruction". 
As "SKIP AD" button blocks some percentage of the ad view. So, "PercentageInView" should be less than 100%.

- 2.2 From the content list appeared on starting application, select "OM AdVerification with skipAd as Friendly Obstruction". 
In this case "SKIP AD" button has been added as friendly obstruction when creating the Omid Ad Session. So, "PercentageInView" should be 100% unless there are any other obstruction on the adView.

- 2.3 From the content list appeared on starting application, select "OM AdVerification skipAd as Friendly Obstruction after ad started".
In this case we are not registering "skip ad" as friendly obstruction while creating the Omid Ad Session, but adding it as friendly obstruction later when the ad starts to play.
So, "PercentageInView" should be 100% after the "start" event.

###3. Verification of public Apis: To verify public API calls and expected behavior, we have simulated them in below way:

- 3.1 registerAdView(View adView): When entering into fullscreen [openFullscreenDialog()](app/src/main/java/com/ooyala/pulseplayer/videoPlayer/VideoPlayerActivity.java) method is called where we are calling this api to register different adview.
 Viewability calculations will be based on the new view then.

- 3.2 addFriendlyObstructions(List<View> friendlyObstructions) : same as mentioned in step 2.2 above

- 3.3 removeFriendlyObstructions() : When entering into fullscreen [openFullscreenDialog()](app/src/main/java/com/ooyala/pulseplayer/videoPlayer/VideoPlayerActivity.java) method is called where we are calling this api to remove all registered friendly obstructions.

- 3.4 removeAllFriendlyObstructions(List<View> friendlyObstructions) : This api can be called same way as mentioned in step 3.3 by passing specific list of friendly obstructions that have been registered before.  