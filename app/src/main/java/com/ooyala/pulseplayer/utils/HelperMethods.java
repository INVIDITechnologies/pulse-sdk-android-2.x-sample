package com.ooyala.pulseplayer.utils;

import android.content.Context;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Toast;

public class HelperMethods {

    public static void showCustomTextSizeToast(Context context, String message, int textSizeSp) {
        Toast toast = new Toast(context);
        TextView textView = new TextView(context);
        textView.setText(message);
        textView.setTextSize(textSizeSp);
        textView.setPadding(35, 25, 35, 25);
        textView.setBackgroundResource(android.R.drawable.toast_frame);
        textView.setTextColor(Color.BLACK);
        toast.setView(textView);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

}
