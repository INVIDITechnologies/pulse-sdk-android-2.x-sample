package com.ooyala.pulseplayer.videoPlayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.MediaController;

import com.ooyala.pulse.PulseVideoAd;
import com.ooyala.pulseplayer.PulseManager.PulseManager;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.VideoItem;

/**
 * An activity for playing ad video and content. This activity employs a PulseManager instance to manage the Pulse session.
 */
public class VideoPlayerActivity extends AppCompatActivity {
    public static PulseManager pulseManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        //Get the selected videoItem from the bundled information.
        final VideoItem videoItem = getSelectedVideoItem();

        //Create an instance of CustomVideoView that is responsible for displaying both ad video and content.
        CustomVideoView player = (CustomVideoView) findViewById(R.id.player) ;
        MediaController controllBar = new MediaController(this);

        //Instantiate Pulse manager with selected data.
        pulseManager = new PulseManager(videoItem, player, controllBar, this);

        //Assign a clickThroughCallback to manage opening the browser when an Ad is clicked.
        pulseManager.setOnClickThroughCallback(new PulseManager.ClickThroughCallback() {
            @Override
            public void onClicked(PulseVideoAd ad) {
                if(ad.getClickThroughURL() != null){
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(ad.getClickThroughURL().toString()));
                    startActivityForResult(intent, 1365);
                } else{
                    pulseManager.returnFromClickThrough();
                }
            }
        });
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        pulseManager.removeCallback(PulseManager.contentProgressHandler);
    }

    @Override
    public void onStop(){
        super.onStop();
        pulseManager.removeCallback(PulseManager.contentProgressHandler);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((resultCode == RESULT_OK || resultCode == RESULT_CANCELED) && requestCode == 1365) {
            pulseManager.returnFromClickThrough();
        }
    }

    /**
     * Create a VideoItem from the bundled information send to this activity.
     * @return The created {@link VideoItem}.
     */
    public VideoItem getSelectedVideoItem() {
        VideoItem selectedVideoItem = new VideoItem();

        selectedVideoItem.setTags(getIntent().getExtras().getStringArray("contentMetadataTags"));
        selectedVideoItem.setMidrollPossition(getIntent().getExtras().getIntArray("midrollPositions"));
        selectedVideoItem.setContentTitle(getIntent().getExtras().getString("contentTitle"));
        selectedVideoItem.setContentId(getIntent().getExtras().getString("contentId"));
        selectedVideoItem.setContentUrl(getIntent().getExtras().getString("contentUrl"));
        selectedVideoItem.setCategory(getIntent().getExtras().getString("category"));

        return selectedVideoItem;
    }
}
