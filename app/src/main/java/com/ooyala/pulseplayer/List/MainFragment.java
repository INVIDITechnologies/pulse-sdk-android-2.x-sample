package com.ooyala.pulseplayer.List;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.leanback.app.BackgroundManager;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HeaderItem;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.Row;
import androidx.leanback.widget.RowPresenter;
import androidx.core.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.ooyala.pulseplayer.R;
import com.ooyala.pulseplayer.model.VideoItem;
import com.ooyala.pulseplayer.utils.CardPresenter;
import com.ooyala.pulseplayer.videoPlayer.VideoPlayerActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BrowseSupportFragment {

    private static final String TAG = "MainFragment";

    private static final int GRID_ITEM_WIDTH = 200;
    private static final int GRID_ITEM_HEIGHT = 200;
    private DisplayMetrics mMetrics;
    private BackgroundManager mBackgroundManager;
    List<VideoItem> selectionMap;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onActivityCreated(savedInstanceState);

//        prepareBackgroundManager();

        setupUIElements();

        //Load json file containing VideoItems from resource.
        String videoJSonString = loadJSONFile(getResources().getIdentifier("raw/library", "raw", getActivity().getPackageName()));
        if (videoJSonString != null) {
            JSONArray videoContentsJSON = null;
            try {
                videoContentsJSON = new JSONArray(videoJSonString);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            selectionMap = new ArrayList<VideoItem>();
            loadPlaybackList(videoContentsJSON, selectionMap);
            loadRows();
        }

        setupEventListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void loadRows() {

        List<VideoItem> list = selectionMap;
        ArrayObjectAdapter rowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        CardPresenter cardPresenter = new CardPresenter();

        int i;
        for (i = 0; i < list.size(); i++) {
            ArrayObjectAdapter listRowAdapter = new ArrayObjectAdapter(cardPresenter);
            listRowAdapter.add(list.get(i));
            HeaderItem header = new HeaderItem(i, list.get(i).getContentTitle());
            rowsAdapter.add(new ListRow(header, listRowAdapter));
        }

        GridItemPresenter mGridPresenter = new GridItemPresenter();
        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        rowsAdapter.add(new ListRow(gridRowAdapter));
        setAdapter(rowsAdapter);
    }

    private void prepareBackgroundManager() {

        mBackgroundManager = BackgroundManager.getInstance(getActivity());
        mBackgroundManager.attach(getActivity().getWindow());

        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private void setupUIElements() {
        setTitle(getString(R.string.browse_title)); // Badge, when set, takes precedent
        // over title
        setHeadersState(HEADERS_ENABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(ContextCompat.getColor(getContext(), R.color.fastlane_background));
        // set search icon color
//        setSearchAffordanceColor(ContextCompat.getColor(getContext(), R.color.transparent));
    }

    private void setupEventListeners() {
//        setOnSearchClickedListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View view) {
//                Toast.makeText(getActivity(), "Implement your own in-app search", Toast.LENGTH_LONG)
//                        .show();
//            }
//        });

        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }


    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof VideoItem) {
                VideoItem videoItem = (VideoItem) item;
                Intent intent = new Intent(getActivity(), VideoPlayerActivity.class);
                intent.putExtra("contentMetadataTags", videoItem.getTags());
                intent.putExtra("midrollPositions", videoItem.getMidrollPositions());
                intent.putExtra("contentTitle", videoItem.getContentTitle());
                intent.putExtra("contentId", videoItem.getContentId());
                intent.putExtra("contentUrl", videoItem.getContentUrl());
                intent.putExtra("category", videoItem.getCategory());
                getActivity().startActivity(intent);
            } else if (item instanceof String) {
                if (((String) item).contains(getString(R.string.error_fragment))) {
                    Intent intent = new Intent(getActivity(), BrowseErrorActivity.class);
                    startActivity(intent);
                } else {
                    Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(
                Presenter.ViewHolder itemViewHolder,
                Object item,
                RowPresenter.ViewHolder rowViewHolder,
                Row row) {
//            if (item instanceof VideoItem) {
//                mBackgroundUri = ((VideoItem) item).getBackgroundImageUrl();
//                startBackgroundTimer();
//            }
        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(
                    ContextCompat.getColor(getContext(), R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
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
     * Create a Map of VideoItem from the provided Json Array.
     * @param videoContentsJSON A Json Array containing the VideoItems.
     * @param selectionMaps The created Map containing the videoItems and their titles as their keys.
     */
    public void loadPlaybackList(JSONArray videoContentsJSON, List<VideoItem> selectionMaps) {
        for (int i = 0; i < videoContentsJSON.length(); i++) {
            try {
                JSONObject videoJson = videoContentsJSON.getJSONObject(i);
                VideoItem videoItem = getVideoItem(videoJson);
                selectionMap.add(videoItem);
            } catch (JSONException e) {
                e.printStackTrace();
            }
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
        videoItem.setMidrollPosition(midrollPosition);
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
     * Assign a value to the requested key.
     * @param source a Json Object containing a key/value pair.
     * @param field the expected key parameter for the key/value pair.
     * @return A value assigned to the key parameter.
     */
    static boolean getBoolean(JSONObject source, String field) {
        try {
            return source.getBoolean(field);
        } catch (JSONException e) {
            return false;
        }
    }
}