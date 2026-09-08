package com.lucas.rollinghorizon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String UPDATE_INFO_URL = "https://raw.githubusercontent.com/yujiacheng1208-del/boll/main/update.json";
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateReceiver;

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
        checkForUpdate();
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection)new URL(UPDATE_INFO_URL).openConnection();
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder body = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close(); connection.disconnect();
                JSONObject info = new JSONObject(body.toString());
                int versionCode = info.optInt("versionCode", 0);
                String apkUrl = info.optString("apkUrl", "");
                if (versionCode > BuildConfig.VERSION_CODE && !apkUrl.isEmpty()) runOnUiThread(() -> showUpdate(info, apkUrl));
            } catch (Exception ignored) { }
        }).start();
    }

    private void showUpdate(JSONObject info, String apkUrl) {
        new AlertDialog.Builder(this).setTitle("发现新版本 " + info.optString("versionName", ""))
                .setMessage(info.optString("notes", "优化游戏体验"))
                .setNegativeButton("稍后", null).setPositiveButton("立即更新", (d, w) -> downloadUpdate(apkUrl)).show();
    }

    private void downloadUpdate(String apkUrl) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("天机滚珠更新"); request.setDescription("正在下载新版本");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "tianji-rolling-ball-update.apk");
        DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        updateDownloadId = manager.enqueue(request);
        Toast.makeText(this, "正在下载更新", Toast.LENGTH_SHORT).show();
        if (updateReceiver == null) {
            updateReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != updateDownloadId) return;
                    Uri apk = ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).getUriForDownloadedFile(updateDownloadId);
                    if (apk != null) {
                        Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(apk, "application/vnd.android.package-archive")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(install);
                    }
                }
            };
            registerReceiver(updateReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
    }

    @Override protected void onDestroy() {
        if (updateReceiver != null) unregisterReceiver(updateReceiver);
        super.onDestroy();
    }
}
