package com.lucas.rollinghorizon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Window;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String UPDATE_INFO_URL = "https://api.github.com/repos/yujiacheng1208-del/boll/contents/update.json?ref=main";
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateReceiver;
    private String pendingUpdateUrl = "";
    private int pendingUpdateVersion = 0;
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private boolean installerOpened = false;
    private RollingGameView gameView;

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
            gameView = new RollingGameView(this);
            setContentView(gameView);
        } catch (Throwable ignored) {
            TextView fallback = new TextView(this);
            fallback.setText("天机滚珠");
            fallback.setTextSize(30);
            fallback.setTextColor(0xFFFFFFFF);
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setBackgroundColor(0xFF08111A);
            setContentView(fallback);
        }
        checkForUpdate(false);
    }

    public void scanForUpdate() { checkForUpdate(true); }

    private void checkForUpdate(boolean manual) {
        new Thread(() -> {
            try {
                String separator = UPDATE_INFO_URL.contains("?") ? "&" : "?";
                HttpURLConnection connection = (HttpURLConnection)new URL(UPDATE_INFO_URL + separator + "t=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(5000); connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache");
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder body = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close(); connection.disconnect();
                JSONObject info = new JSONObject(body.toString());
                // GitHub's contents API returns a base64 file body. It is less prone
                // to serving an old branch copy than the Raw endpoint.
                if (info.has("content")) {
                    String encoded = info.optString("content", "").replace("\n", "");
                    info = new JSONObject(new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8"));
                }
                final JSONObject updateInfo = info;
                int versionCode = updateInfo.optInt("versionCode", 0);
                String apkUrl = updateInfo.optString("apkUrl", "");
                if (versionCode > BuildConfig.VERSION_CODE && !apkUrl.isEmpty()) {
                    runOnUiThread(() -> showUpdate(updateInfo, apkUrl));
                } else if (manual) {
                    runOnUiThread(() -> Toast.makeText(this, "当前已是最新版本", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception ignored) { }
        }).start();
    }

    private void showUpdate(JSONObject info, String apkUrl) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("发现新版本 " + info.optString("versionName", ""))
                .setMessage(info.optString("notes", "优化游戏体验"))
                .setNegativeButton("稍后", null).setPositiveButton("立即更新", (d, w) -> downloadUpdate(apkUrl, info.optInt("versionCode", 0))).create();
        dialog.setOnShowListener(ignored -> styleUpdateDialog(dialog));
        dialog.show();
    }

    private void styleUpdateDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xFFD7F0F1));
        if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF071C22);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF071C22);
    }

    private void downloadUpdate(String apkUrl, int versionCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateUrl = apkUrl;
            pendingUpdateVersion = versionCode;
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("允许安装更新")
                    .setMessage("请允许天机滚珠安装下载的新版本，然后会自动继续更新。")
                    .setNegativeButton("取消", null).setPositiveButton("前往授权", (d, w) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    }).create();
            dialog.setOnShowListener(ignored -> styleUpdateDialog(dialog));
            dialog.show();
            return;
        }
        ensureUpdateReceiver();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.addRequestHeader("Cache-Control", "no-cache");
        request.setTitle("天机滚珠更新"); request.setDescription("正在下载新版本");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        // Every version gets a distinct destination so Android never reuses a
        // previous APK with the same file name.
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                "tianji-rolling-ball-update-" + Math.max(1, versionCode) + ".apk");
        DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        updateDownloadId = manager.enqueue(request);
        installerOpened = false;
        // Hand the wait screen to Android's own download centre immediately. It
        // shows real progress and remains reliable even while this app is in the
        // background; completion still returns straight to the installer below.
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (RuntimeException ignored) {
            Toast.makeText(this, "正在下载更新", Toast.LENGTH_SHORT).show();
        }
        watchUpdateDownload();
    }

    private void watchUpdateDownload() {
        updateHandler.postDelayed(() -> {
            if (updateDownloadId < 0L || installerOpened) return;
            DownloadManager manager = (DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            android.database.Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(updateDownloadId));
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                cursor.close();
                if (status == DownloadManager.STATUS_SUCCESSFUL) { openDownloadedInstaller(); return; }
                if (status == DownloadManager.STATUS_FAILED) {
                    Toast.makeText(this, "更新下载失败，请稍后重试", Toast.LENGTH_LONG).show(); return;
                }
            } else cursor.close();
            watchUpdateDownload();
        }, 700L);
    }

    private void openDownloadedInstaller() {
        if (installerOpened) return;
        installerOpened = true;
        Uri apk = ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).getUriForDownloadedFile(updateDownloadId);
        if (apk != null) {
            try {
                Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(apk, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(install);
            } catch (RuntimeException error) {
                Toast.makeText(this, "无法打开安装程序", Toast.LENGTH_LONG).show();
            }
        } else Toast.makeText(this, "更新文件无法打开", Toast.LENGTH_LONG).show();
    }

    private void ensureUpdateReceiver() {
        if (updateReceiver != null) return;
        updateReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != updateDownloadId) return;
                openDownloadedInstaller();
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(updateReceiver, filter);
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resumeBackgroundMusic();
        if (!pendingUpdateUrl.isEmpty() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) {
            String url = pendingUpdateUrl; int version = pendingUpdateVersion;
            pendingUpdateUrl = ""; pendingUpdateVersion = 0; downloadUpdate(url, version);
        }
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.pauseBackgroundMusic();
        super.onPause();
    }

    @Override protected void onStop() {
        // Some devices leave audio alive past onPause while switching apps.
        // Stop it again at the definitive background transition.
        if (gameView != null) gameView.pauseBackgroundMusic();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (updateReceiver != null) unregisterReceiver(updateReceiver);
        updateHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
