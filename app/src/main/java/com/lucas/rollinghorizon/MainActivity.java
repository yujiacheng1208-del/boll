package com.lucas.rollinghorizon;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(0xFF08111A);
        // This flag set works from Android 4.1 onward, including older phones that do
        // not implement the newer WindowInsets controller consistently.
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        try {
            setContentView(new RollingGameView(this));
        } catch (Throwable ignored) {
            TextView fallback = new TextView(this);
            fallback.setText("天机滚珠");
            fallback.setTextSize(30);
            fallback.setTextColor(0xFFFFFFFF);
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setBackgroundColor(0xFF08111A);
            setContentView(fallback);
        }
    }
}
