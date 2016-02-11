package com.ooyala.pulseplayer.List;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.ooyala.adtech.LogItem;
import com.ooyala.adtech.LogListener;
import com.ooyala.pulse.Pulse;
import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.utils.VideoItem;
import com.ooyala.pulseplayer.videoPlayer.VideoPlayerActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    Map<String, VideoItem> selectionMap;
    ArrayAdapter<String> selectionAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Load json file containing VideoItems from resource.
        String videoJSonString = loadJSONFile(getResources().getIdentifier("raw/library", "raw", getPackageName()));

        //Set a listener to receive low-level log messages about errors, warnings and the like.
        Pulse.setLogListener(new LogListener() {
            @Override
            public void onLog(LogItem logItem) {
                Log.i("PulseManager", logItem.toString());
            }
        });

        // Initialize the Pulse SDK with "setPulseHost(Host, Device Container, Persistent Id)"
        // Host:
        //     Your Pulse account host
        // Device Container:
        //     Device container in Ooyala Pulse is used for targeting and
        //     reporting purposes. This device container attribute is only used
        //     if you want to override the Pulse device detection algorithm on the
        //     Pulse ad server. This should only be set if normal device detection
        //     does not work and only after consulting Ooyala personnel. An incorrect
        //     device container value can result in no ads being served or incorrect
        //     ad delivery and reports.
        // Persistent Id:
        //     The persistent identifier is used to identify the end user and is the
        //     basis for frequency capping, uniqueness, DMP targeting information and
        //     more. Use Apple's advertising identifier (IDFA), or your own unique
        //     user identifier here.
        // Refer to:
        //     http://support.ooyala.com/developers/ad-documentation/oadtech/ad_serving/dg/integration_sdk_parameter.html
        Pulse.setPulseHost("http://pulse-demo.videoplaza.tv", null, null);

        if (videoJSonString != null) {


            JSONArray videoContentsJSON = null;
            try {
                videoContentsJSON = new JSONArray(videoJSonString);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            selectionMap = new LinkedHashMap<String, VideoItem>();

            loadPlaybackList(videoContentsJSON, selectionMap);

            setContentView(R.layout.activity_main);

            selectionAdapter = new ArrayAdapter<String>(this, R.layout.list_of_ad_item);

            for (String key : selectionMap.keySet()) {
                selectionAdapter.add(key);
            }
            selectionAdapter.notifyDataSetChanged();
            ListView selectionListView = (ListView) findViewById(R.id.mainActivityListView);
            selectionListView.setAdapter(selectionAdapter);
            selectionListView.setOnItemClickListener(this);

        } else {
            setContentView(R.layout.activity_resource_not_found);
        }
    }

    /**
     * Create a Map of VideoItem from the provided Json Array.
     * @param videoContentsJSON A Json Array containing the VideoItems.
     * @param selectionMap The created Map containing the videoItems and their titles as their keys.
     */
    public void loadPlaybackList(JSONArray videoContentsJSON, Map selectionMap) {
        for (int i = 0; i < videoContentsJSON.length(); i++) {
            try {
                JSONObject videoJson = videoContentsJSON.getJSONObject(i);
                VideoItem videoItem = getVideoItem(videoJson);
                selectionMap.put(videoItem.getContentTitle(), videoItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Load the json file from the provided path.
     * @param resourceIdentifier The path to the Json file.
     * @return the Json file containing the required information for configuring the session.
     */
    public String loadJSONFile(int resourceIdentifier) {
        if (resourceIdentifier != 0) {
            InputStream input = getResources().openRawResource(resourceIdentifier);
            java.util.Scanner s = new java.util.Scanner(input).useDelimiter("\\A");
            return s.hasNext() ? s.next() : null;
        } else {
            return null;
        }
    }

    /**
     * Create a {@link VideoItem} from the selected row of playback list.
     * @param videoJson The json object of the selected row.
     * @return the created {@link VideoItem}
     */
    public VideoItem getVideoItem(JSONObject videoJson) {
        VideoItem videoItem = new VideoItem();
        if (videoJson.has("content-id")) {
            videoItem.setContentId(getString(videoJson, "content-id"));
        } else {
            videoItem.setContentId("");
        }

        if (videoJson.has("content-title")) {
            videoItem.setContentTitle(getString(videoJson, "content-title"));

        } else {
            videoItem.setContentTitle("");
        }
        String[] tags = null;
        int[] midrollPosition = null;
        try {
            if (videoJson.has("tags")) {
                JSONArray tagArray = videoJson.getJSONArray("tags");
                tags = new String[tagArray.length()];
                for (int i = 0; i < tagArray.length(); i++) {
                    tags[i] = tagArray.getString(i);
                }
            } else {
                tags = new String[0];
            }

            if (videoJson.has("midroll-positions")) {
                JSONArray midrollPositionArray = videoJson.getJSONArray("midroll-positions");
                midrollPosition = new int[midrollPositionArray.length()];
                for (int i = 0; i < midrollPositionArray.length(); i++) {
                    midrollPosition[i] = midrollPositionArray.getInt(i);
                }
            } else {
                midrollPosition = new int[0];
            }

        } catch (JSONException e) {
            Log.i("Pulse Demo Player", "Error occurred: "+ e.getClass());
        }
        videoItem.setTags(tags);
        videoItem.setMidrollPossition(midrollPosition);
        if (videoJson.has("category")) {
            videoItem.setCategory(getString(videoJson, "category"));
        } else {
            videoItem.setCategory("");
        }
        if (videoJson.has("content-url")) {
            videoItem.setContentUrl(getString(videoJson, "content-url"));
        } else {
            videoItem.setContentUrl("");
        }
        return videoItem;
    }

    /**
     * Assign a value to the requested key.
     * @param source a Json Object containing a key/value pair.
     * @param field the expected key parameter for the key/value pair.
     * @return A value assigned to the key parameter.
     */
    static String getString(JSONObject source, String field) {
        try {
            return source.getString(field);
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * Create the bundle data from the selected list item and start VideoPlayerActivity.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        VideoItem selection = selectionMap.get(selectionAdapter.getItem(position));

        Intent intent = new Intent(this, VideoPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        intent.putExtra("contentMetadataTags", selection.getTags());
        intent.putExtra("midrollPositions", selection.getMidrollPositions());
        intent.putExtra("contentTitle", selection.getContentTitle());
        intent.putExtra("contentId", selection.getContentId());
        intent.putExtra("contentUrl", selection.getContentUrl());
        intent.putExtra("category", selection.getCategory());

        startActivity(intent);
    }
}
