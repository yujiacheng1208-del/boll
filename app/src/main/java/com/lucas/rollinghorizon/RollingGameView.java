package com.lucas.rollinghorizon;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlertDialog;
import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** A stationary rolling ball while the narrow track streams toward the camera. */
public final class RollingGameView extends View {
    private static final int FAIL_NONE = 0;
    private static final int FAIL_WRONG_COLOUR = 1;
    private static final int FAIL_VOID = 2;
    private static final int MODE_NONE = 0;
    private static final int MODE_LEVEL = 1;
    private static final int MODE_ENDLESS = 2;
    private static final int EASY = 0;
    private static final int MEDIUM = 1;
    private static final int HARD = 2;
    private static final String PREFS = "tianji_players";
    private static final String ACCOUNTS = "accounts";
    private static final String ACTIVE_USER = "active_user";
    private static final int CENTRE_EDGE = 0xFF55D9E7;
    private static final int RED_EDGE = 0xFFFF5F70;
    private static final int ORANGE_EDGE = 0xFFFFA34D;
    private static final int PINK_EDGE = 0xFFFF77C9;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final SharedPreferences preferences;
    private final float density;
    private MediaPlayer ambientMusic;
    private float lanePosition = 0f;
    private float targetLane = 0f;
    private float touchDownX;
    private float dragStartLane;
    private int ballTint = CENTRE_EDGE;
    private boolean colourChosen = false;
    private int requiredColour = CENTRE_EDGE;
    private boolean failed = false;
    private int failReason = FAIL_NONE;
    private boolean completed = false;
    private boolean falling = false;
    private long fallStartedAt = 0L;
    private long lastLanding = 0L;
    private long gameStartedAt = -1L;
    private long frozenElapsed = 0L;
    private long gameElapsed = 0L;
    private long lastFrameAt = -1L;
    private long countdownEndsAt = 0L;
    private long landingEffectAt = 0L;
    private int landingEffectLane = 0;
    private int landingEffectColor = CENTRE_EDGE;
    private int score = 0;
    private boolean settingsOpen = false;
    private float sensitivity = 1f;
    private float musicVolume = .24f;
    private float effectsVolume = .50f;
    private int gameMode = MODE_NONE;
    private boolean colourChoiceOpen = false;
    private boolean difficultyChoiceOpen = false;
    private int levelDifficulty = MEDIUM;
    private String currentUser = "";
    private int pendingMode = MODE_NONE;
    private boolean leaderboardOpen = false;
    private boolean confirmHomeOpen = false;

    public RollingGameView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        currentUser = preferences.getString(ACTIVE_USER, "");
        try {
            ambientMusic = MediaPlayer.create(context, R.raw.background_music);
            if (ambientMusic != null) { ambientMusic.setLooping(true); ambientMusic.setVolume(musicVolume, musicVolume); }
        } catch (RuntimeException ignored) { }
        startMusic();
    }

    private void startMusic() {
        if (ambientMusic != null && !ambientMusic.isPlaying()) ambientMusic.start();
    }

    private void applyMusicVolume() {
        if (ambientMusic != null) ambientMusic.setVolume(musicVolume, musicVolume);
    }

    private void pauseMusic() {
        if (ambientMusic != null && ambientMusic.isPlaying()) ambientMusic.pause();
    }

    private void stopMusic() {
        if (ambientMusic != null) {
            pauseMusic();
            try { ambientMusic.seekTo(0); } catch (IllegalStateException ignored) { }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                dragStartLane = targetLane;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (settingsOpen) {
                    updateSettingsSlider(event.getX(), event.getY());
                    return true;
                }
                if (gameStartedAt >= 0L && !failed && !completed && !falling && !settingsOpen && !colourChoiceOpen && !confirmHomeOpen && countdownEndsAt == 0L) {
                    float movement = (event.getX() - touchDownX) / (getWidth() * .30f / sensitivity);
                    // No invisible side walls: dragging beyond the outer lanes is allowed.
                    targetLane = Math.max(-2f, Math.min(2f, dragStartLane + movement));
                }
                return true;
            case MotionEvent.ACTION_UP:
                // The small profile chip is available both on the home screen and in play.
                if (!currentUser.isEmpty() && event.getX() < 190f*density && event.getY() < 72f*density) {
                    if (gameStartedAt < 0L) showProfileDialog();
                    return true;
                }
                if (gameStartedAt < 0L) {
                    if (leaderboardOpen) {
                        leaderboardOpen = false;
                    } else if (difficultyChoiceOpen) {
                        float y = event.getY();
                        if (y > getHeight()*.70f) {
                            difficultyChoiceOpen = false;
                        } else if (y >= getHeight()*.37f && y < getHeight()*.49f) {
                            levelDifficulty = EASY;
                            startMode(MODE_LEVEL);
                        } else if (y >= getHeight()*.49f && y < getHeight()*.60f) {
                            levelDifficulty = MEDIUM;
                            startMode(MODE_LEVEL);
                        } else if (y >= getHeight()*.60f && y <= getHeight()*.70f) {
                            levelDifficulty = HARD;
                            startMode(MODE_LEVEL);
                        }
                    } else if (event.getX() < 190f*density && event.getY() > 70f*density && event.getY() < 116f*density) {
                        leaderboardOpen = true;
                    } else if (event.getY() < getHeight()*.59f) {
                        difficultyChoiceOpen = true;
                    } else if (event.getY() < getHeight()*.67f) {
                        startMode(MODE_ENDLESS);
                    } else if (currentUser.isEmpty() && event.getY() < getHeight()*.77f) {
                        currentUser = "";
                        preferences.edit().remove(ACTIVE_USER).apply();
                        if (event.getX() < getWidth()*.5f) showRegisterDialog(MODE_NONE);
                        else showLoginDialog(MODE_NONE);
                    } else if (event.getY() > getHeight()*.78f && event.getY() < getHeight()*.88f && getContext() instanceof Activity) {
                        ((Activity)getContext()).finishAndRemoveTask();
                    }
                    return true;
                }
                if (failed || completed) {
                    if (event.getY() > getHeight() * .665f) returnToHome(); else beginGame();
                    return true;
                }
                if (confirmHomeOpen) {
                    if (event.getY() > getHeight()*.51f && event.getY() < getHeight()*.62f) {
                        if (event.getX() < getWidth()*.5f) confirmHomeOpen = false; else returnToHome();
                    }
                    return true;
                }
                if (colourChoiceOpen) {
                    if (event.getY() > getHeight()*.665f) {
                        returnToHome();
                        return true;
                    }
                    float choiceY = getHeight()*.55f;
                    if (Math.abs(event.getY() - choiceY) < 48f*density) {
                        float x = event.getX(), w = getWidth();
                        int chosen = x < w*.4f ? RED_EDGE : x > w*.6f ? PINK_EDGE : ORANGE_EDGE;
                        requiredColour = chosen;
                        ballTint = chosen;
                        colourChosen = true;
                        colourChoiceOpen = false;
                        lastFrameAt = SystemClock.elapsedRealtime();
                        startMusic();
                    }
                    return true;
                }
                if (event.getX() > getWidth() - 72f*density && event.getY() < 72f*density) {
                    if (settingsOpen) {
                        settingsOpen = false;
                        countdownEndsAt = SystemClock.elapsedRealtime() + 3000L;
                    } else if (countdownEndsAt == 0L) {
                        settingsOpen = true;
                    }
                    return true;
                }
                if (settingsOpen) {
                    if (event.getY() > getHeight()*.60f && event.getY() < getHeight()*.69f) {
                        if (event.getX() < getWidth()*.5f) {
                            confirmHomeOpen = true;
                        } else {
                            settingsOpen = false;
                            countdownEndsAt = SystemClock.elapsedRealtime() + 3000L;
                        }
                        return true;
                    }
                    updateSettingsSlider(event.getX(), event.getY());
                    return true;
                }
                if (countdownEndsAt != 0L) return true;
                targetLane = Math.round(targetLane);
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void startMode(int mode) {
        if (currentUser == null || currentUser.trim().isEmpty()) {
            pendingMode = mode;
            showAccountChoiceDialog(mode);
        } else beginGame(mode);
    }

    private void showAccountChoiceDialog(final int mode) {
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("天机账户")
                .setMessage("选择一种方式继续")
                .setNegativeButton("注册", null).setPositiveButton("登录", null).create();
        dialog.setOnShowListener(ignored -> {
            styleAccountDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss(); showRegisterDialog(mode);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss(); showLoginDialog(mode);
            });
        });
        dialog.show();
    }

    private LinearLayout accountFields(EditText name, EditText password) {
        name.setHint("请输入名称"); name.setSingleLine(true); name.setTextColor(0xFF071C22); name.setHintTextColor(0xFF527078);
        password.setHint("请输入密码"); password.setSingleLine(true); password.setInputType(0x81);
        password.setTextColor(0xFF071C22); password.setHintTextColor(0xFF527078);
        TextView nameLabel = new TextView(getContext()); nameLabel.setText("名称"); nameLabel.setTextSize(13); nameLabel.setTextColor(0xFF071C22);
        TextView passwordLabel = new TextView(getContext()); passwordLabel.setText("密码"); passwordLabel.setTextSize(13); passwordLabel.setTextColor(0xFF071C22);
        LinearLayout fields = new LinearLayout(getContext()); fields.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(22 * density); fields.setPadding(pad, pad / 2, pad, pad / 3);
        fields.addView(nameLabel); fields.addView(name); fields.addView(passwordLabel); fields.addView(password);
        return fields;
    }

    /** A soft aqua panel keeps account screens within the game's blue-black palette. */
    private void styleAccountDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0xFFD7F0F1));
        int[] buttons = {AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE};
        for (int button : buttons) {
            if (dialog.getButton(button) != null) dialog.getButton(button).setTextColor(0xFF071C22);
        }
    }

    private void showRegisterDialog(final int mode) {
        final EditText name = new EditText(getContext());
        final EditText password = new EditText(getContext());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("注册账号")
                .setView(accountFields(name, password)).setNegativeButton("返回", null)
                .setPositiveButton("注册", null).create();
        dialog.setOnShowListener(ignored -> {
            styleAccountDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String user = name.getText().toString().trim();
                String pass = password.getText().toString();
                Set<String> accounts = new HashSet<>(preferences.getStringSet(ACCOUNTS, Collections.<String>emptySet()));
                if (user.isEmpty()) { name.setError("请输入用户名"); return; }
                if (accounts.contains(user)) { name.setError("该用户名已经被使用"); return; }
                accounts.add(user);
                preferences.edit().putStringSet(ACCOUNTS, accounts).putString("password_" + user, pass)
                        .putString(ACTIVE_USER, user).apply();
                currentUser = user; dialog.dismiss();
                if (mode != MODE_NONE) beginGame(mode);
            });
        });
        dialog.show();
    }

    private void showLoginDialog(final int mode) {
        final EditText name = new EditText(getContext());
        final EditText password = new EditText(getContext());
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("登录账号")
                .setView(accountFields(name, password)).setNegativeButton("返回", null)
                .setPositiveButton("登录", null).create();
        dialog.setOnShowListener(ignored -> {
            styleAccountDialog(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String user = name.getText().toString().trim();
            String pass = password.getText().toString();
            if (!preferences.getStringSet(ACCOUNTS, Collections.<String>emptySet()).contains(user)) { name.setError("用户不存在"); return; }
            if (!pass.equals(preferences.getString("password_" + user, ""))) { password.setError("密码不正确"); return; }
            preferences.edit().putString(ACTIVE_USER, user).apply();
            currentUser = user; dialog.dismiss();
            if (mode != MODE_NONE) beginGame(mode);
            });
        });
        dialog.show();
    }

    private void showAvatarDialog() {
        showAvatarDialog(null);
    }

    private void showAvatarDialog(final Runnable afterChanged) {
        final String[] names = {"青曜", "赤焰", "金砂", "紫晶"};
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("更换头像")
                .setItems(names, (itemDialog, which) -> {
                    preferences.edit().putInt("avatar_" + currentUser, which).apply();
                    if (afterChanged != null) post(afterChanged);
                })
                .setNegativeButton("取消", null).create();
        dialog.setOnShowListener(ignored -> styleAccountDialog(dialog));
        dialog.show();
    }

    private GradientDrawable profileShape(int color, float cornerDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(cornerDp * density);
        shape.setStroke((int)(1.1f * density), 0xFF93C2C8);
        return shape;
    }

    private TextView profileAction(String label) {
        TextView action = new TextView(getContext());
        action.setText(label); action.setTextSize(15); action.setTextColor(0xFF071C22);
        int inset = (int)(12 * density);
        action.setPadding(inset, inset, inset, inset);
        action.setGravity(android.view.Gravity.CENTER);
        action.setBackground(profileShape(0xFFEAF8F8, 15f));
        return action;
    }

    private TextView profileCard(String title, String value) {
        TextView card = new TextView(getContext());
        card.setText(title + "\n" + value);
        card.setTextSize(15); card.setTextColor(0xFF071C22);
        int inset = (int)(15 * density); card.setPadding(inset, inset, inset, inset);
        card.setLineSpacing(5f * density, 1f);
        card.setBackground(profileShape(0xFFF0FAFA, 17f));
        return card;
    }

    private void addProfileItem(LinearLayout parent, TextView item) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int)(8 * density), 0, 0);
        parent.addView(item, params);
    }

    private void showProfileDialog() {
        LinearLayout page = new LinearLayout(getContext());
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * density); page.setPadding(pad, pad, pad, pad);
        int[] avatarColours = {CENTRE_EDGE, RED_EDGE, 0xFFFFC65B, 0xFFB98CFF};
        int avatarIndex = preferences.getInt("avatar_" + currentUser, 0) % avatarColours.length;
        TextView portrait = new TextView(getContext());
        portrait.setText(currentUser.substring(0, 1)); portrait.setTextSize(25); portrait.setTextColor(Color.WHITE);
        portrait.setGravity(android.view.Gravity.CENTER);
        GradientDrawable portraitBg = profileShape(avatarColours[avatarIndex], 50f);
        portraitBg.setShape(GradientDrawable.OVAL); portrait.setBackground(portraitBg);
        LinearLayout.LayoutParams portraitParams = new LinearLayout.LayoutParams((int)(70*density), (int)(70*density));
        portraitParams.gravity = android.view.Gravity.CENTER_HORIZONTAL; page.addView(portrait, portraitParams);
        TextView userTitle = new TextView(getContext());
        userTitle.setText(currentUser + "\n天机玩家资料"); userTitle.setGravity(android.view.Gravity.CENTER);
        userTitle.setTextSize(17); userTitle.setTextColor(0xFF071C22); userTitle.setLineSpacing(5f*density, 1f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2); titleParams.setMargins(0, (int)(10*density), 0, (int)(6*density));
        page.addView(userTitle, titleParams);
        TextView nameCard = profileCard("名称  ·  点击修改", currentUser); addProfileItem(page, nameCard);
        TextView passwordCard = profileCard("密码  ·  点击修改", "••••••••"); addProfileItem(page, passwordCard);
        TextView avatarCard = profileAction("更换头像"); addProfileItem(page, avatarCard);
        TextView switchCard = profileAction("切换账号");
        addProfileItem(page, switchCard);
        TextView logoutCard = profileAction("退出登录");
        logoutCard.setTextColor(0xFF8C2432);
        addProfileItem(page, logoutCard);
        final AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("玩家资料")
                .setView(page).setPositiveButton("关闭", null).create();
        nameCard.setOnClickListener(v -> showChangeNameDialog());
        passwordCard.setOnClickListener(v -> showChangePasswordDialog());
        avatarCard.setOnClickListener(v -> showAvatarDialog(() -> { dialog.dismiss(); showProfileDialog(); }));
        switchCard.setOnClickListener(v -> showSwitchConfirm(dialog));
        logoutCard.setOnClickListener(v -> showLogoutConfirm(dialog));
        dialog.setOnShowListener(ignored -> styleAccountDialog(dialog));
        dialog.show();
    }

    private void showLogoutConfirm(final AlertDialog profileDialog) {
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("退出登录？")
                .setMessage("确认后将返回主页面。")
                .setNegativeButton("取消", null).setPositiveButton("确认退出", null).create();
        dialog.setOnShowListener(ignored -> { styleAccountDialog(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            currentUser = ""; preferences.edit().remove(ACTIVE_USER).apply();
            dialog.dismiss(); profileDialog.dismiss();
        }); });
        dialog.show();
    }

    private void showSwitchConfirm(final AlertDialog profileDialog) {
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("切换账号？")
                .setMessage("当前账号会保留登录记录，选择新账号后才会切换。")
                .setNegativeButton("取消", null).setPositiveButton("继续", null).create();
        dialog.setOnShowListener(ignored -> { styleAccountDialog(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss(); profileDialog.dismiss(); showDeviceAccounts();
        }); });
        dialog.show();
    }

    private void showDeviceAccounts() {
        ArrayList<String> known = new ArrayList<>(preferences.getStringSet(ACCOUNTS, Collections.<String>emptySet()));
        Collections.sort(known);
        final String[] choices = new String[known.size() + 1];
        for (int i = 0; i < known.size(); i++) choices[i] = known.get(i);
        choices[choices.length - 1] = "其他账号（登录）";
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("本设备登录过的账号")
                .setItems(choices, null).setNegativeButton("取消", null).create();
        dialog.setOnShowListener(ignored -> {
            styleAccountDialog(dialog);
            dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
                if (position == choices.length - 1) {
                    dialog.dismiss(); showLoginDialog(MODE_NONE);
                } else {
                    currentUser = choices[position];
                    preferences.edit().putString(ACTIVE_USER, currentUser).apply();
                    dialog.dismiss();
                }
            });
        });
        dialog.show();
    }

    private LinearLayout editFields(String firstLabel, EditText first, String secondLabel, EditText second) {
        first.setSingleLine(true); first.setTextColor(0xFF071C22); first.setHintTextColor(0xFF527078);
        second.setSingleLine(true); second.setTextColor(0xFF071C22); second.setHintTextColor(0xFF527078); second.setInputType(0x81);
        TextView firstTitle = new TextView(getContext()); firstTitle.setText(firstLabel); firstTitle.setTextColor(0xFF071C22);
        TextView secondTitle = new TextView(getContext()); secondTitle.setText(secondLabel); secondTitle.setTextColor(0xFF071C22);
        LinearLayout layout = new LinearLayout(getContext()); layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20*density); layout.setPadding(pad, pad/2, pad, pad/2);
        layout.addView(firstTitle); layout.addView(first); layout.addView(secondTitle); layout.addView(second);
        return layout;
    }

    private void showChangeNameDialog() {
        EditText newName = new EditText(getContext()); newName.setText(currentUser);
        EditText verifyPassword = new EditText(getContext()); verifyPassword.setHint("输入当前密码以确认");
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("修改名称")
                .setView(editFields("新名称", newName, "身份验证", verifyPassword))
                .setNegativeButton("取消", null).setPositiveButton("确认修改", null).create();
        dialog.setOnShowListener(ignored -> { styleAccountDialog(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldUser = currentUser, updated = newName.getText().toString().trim();
            if (!verifyPassword.getText().toString().equals(preferences.getString("password_" + oldUser, ""))) { verifyPassword.setError("当前密码不正确"); return; }
            if (updated.isEmpty()) { newName.setError("请输入名称"); return; }
            Set<String> accounts = new HashSet<>(preferences.getStringSet(ACCOUNTS, Collections.<String>emptySet()));
            if (!updated.equals(oldUser) && accounts.contains(updated)) { newName.setError("该用户名已经被使用"); return; }
            if (!updated.equals(oldUser)) {
                accounts.remove(oldUser); accounts.add(updated);
                preferences.edit().putStringSet(ACCOUNTS, accounts)
                        .putString("password_" + updated, preferences.getString("password_" + oldUser, ""))
                        .putInt("endless_" + updated, preferences.getInt("endless_" + oldUser, 0))
                        .putInt("level_" + updated, preferences.getInt("level_" + oldUser, 0))
                        .putInt("avatar_" + updated, preferences.getInt("avatar_" + oldUser, 0))
                        .remove("password_" + oldUser).remove("endless_" + oldUser).remove("level_" + oldUser).remove("avatar_" + oldUser)
                        .putString(ACTIVE_USER, updated).apply();
                currentUser = updated;
            }
            dialog.dismiss();
        }); }); dialog.show();
    }

    private void showChangePasswordDialog() {
        EditText oldPassword = new EditText(getContext()); oldPassword.setHint("输入当前密码");
        EditText newPassword = new EditText(getContext()); newPassword.setHint("输入新密码");
        AlertDialog dialog = new AlertDialog.Builder(getContext()).setTitle("修改密码")
                .setView(editFields("当前密码", oldPassword, "新密码", newPassword))
                .setNegativeButton("取消", null).setPositiveButton("确认修改", null).create();
        dialog.setOnShowListener(ignored -> { styleAccountDialog(dialog); dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!oldPassword.getText().toString().equals(preferences.getString("password_" + currentUser, ""))) { oldPassword.setError("当前密码不正确"); return; }
            preferences.edit().putString("password_" + currentUser, newPassword.getText().toString()).apply(); dialog.dismiss();
        }); }); dialog.show();
    }

    private void savePlayerProgress(int progress) {
        if (currentUser == null || currentUser.isEmpty()) return;
        SharedPreferences.Editor edit = preferences.edit();
        if (gameMode == MODE_ENDLESS) {
            int best = preferences.getInt("endless_" + currentUser, 0);
            if (score > best) edit.putInt("endless_" + currentUser, score);
        } else {
            int best = preferences.getInt("level_" + currentUser, 0);
            if (progress > best) edit.putInt("level_" + currentUser, progress);
        }
        edit.apply();
    }

    private void beginGame() {
        beginGame(gameMode == MODE_NONE ? MODE_LEVEL : gameMode);
    }

    private void beginGame(int mode) {
        gameMode = mode;
        gameStartedAt = SystemClock.elapsedRealtime();
        frozenElapsed = 0L;
        gameElapsed = 0L;
        lastFrameAt = gameStartedAt;
        countdownEndsAt = 0L;
        lanePosition = 0f;
        targetLane = 0f;
        ballTint = CENTRE_EDGE;
        colourChosen = false;
        requiredColour = CENTRE_EDGE;
        failed = false;
        failReason = FAIL_NONE;
        completed = false;
        falling = false;
        fallStartedAt = 0L;
        lastLanding = 0L;
        score = 0;
        settingsOpen = false;
        colourChoiceOpen = true;
        difficultyChoiceOpen = false;
        confirmHomeOpen = false;
        // Music begins as soon as a game session opens, including the colour-choice screen.
        startMusic();
    }

    private void returnToHome() {
        gameStartedAt = -1L;
        gameMode = MODE_NONE;
        gameElapsed = 0L;
        frozenElapsed = 0L;
        countdownEndsAt = 0L;
        failed = false;
        completed = false;
        falling = false;
        settingsOpen = false;
        colourChoiceOpen = false;
        difficultyChoiceOpen = false;
        confirmHomeOpen = false;
        score = 0;
        lanePosition = 0f;
        targetLane = 0f;
        ballTint = CENTRE_EDGE;
    }

    private void drawButton(Canvas c, float cx, float cy, String label) {
        float buttonWidth = 142f * density, buttonHeight = 48f * density;
        p.setShader(new LinearGradient(cx-buttonWidth*.5f, cy, cx+buttonWidth*.5f, cy,
                new int[]{0xFF126275, 0xFF1B93A2, 0xFF126275}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(cx-buttonWidth*.5f, cy-buttonHeight*.5f, cx+buttonWidth*.5f,
                cy+buttonHeight*.5f, 24f*density, 24f*density, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f*density); p.setColor(0xFF78EEF7);
        c.drawRoundRect(cx-buttonWidth*.5f, cy-buttonHeight*.5f, cx+buttonWidth*.5f,
                cy+buttonHeight*.5f, 24f*density, 24f*density, p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(18f*density); p.setColor(Color.WHITE);
        c.drawText(label, cx, cy + 6f*density, p);
    }

    private void drawSecondaryButton(Canvas c, float cx, float cy, String label) {
        float buttonWidth = 142f * density, buttonHeight = 40f * density;
        p.setColor(0xFF0E2833);
        c.drawRoundRect(cx-buttonWidth*.5f, cy-buttonHeight*.5f, cx+buttonWidth*.5f,
                cy+buttonHeight*.5f, 20f*density, 20f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f*density); p.setColor(0xFF6F9FA9);
        c.drawRoundRect(cx-buttonWidth*.5f, cy-buttonHeight*.5f, cx+buttonWidth*.5f,
                cy+buttonHeight*.5f, 20f*density, 20f*density, p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(15f*density); p.setColor(0xFFE5FBFF);
        c.drawText(label, cx, cy + 5f*density, p);
    }

    private void drawStartOverlay(Canvas c, float w, float h) {
        float cx = w*.5f;
        p.setColor(0xB8000000); c.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(cx, h*.39f, w*.48f,
                new int[]{0x45287388, 0x0D10223A, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, h*.39f, w*.48f, p); p.setShader(null);
        float top = h*.19f, bottom = h*.89f;
        p.setColor(0xE70A1420); c.drawRoundRect(w*.10f, top, w*.90f, bottom, 26f*density, 26f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.3f*density); p.setColor(0xFF6BE8F5);
        c.drawRoundRect(w*.10f, top, w*.90f, bottom, 26f*density, 26f*density, p); p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF6BE8F5); c.drawRoundRect(cx-40f*density, h*.23f, cx+40f*density, h*.233f, 2f*density, 2f*density, p);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(31f*density); p.setColor(Color.WHITE);
        c.drawText("天机滚珠", cx, h*.32f, p);
        p.setTextSize(14f*density); p.setColor(0xFFB9D6DD);
        c.drawText("选择模式，踏上你的颜色之路", cx, h*.375f, p);
        p.setTextSize(12f*density); p.setColor(0xFF6F9BA6);
        c.drawText(currentUser.isEmpty() ? "登录或注册后保存你的战绩" : "当前玩家 · " + currentUser, cx, h*.415f, p);
        drawButton(c, cx, h*.53f, "关卡模式");
        drawSecondaryButton(c, cx, h*.62f, "无尽模式");
        if (currentUser.isEmpty()) {
            drawSecondaryButton(c, w*.30f, h*.72f, "注册");
            drawSecondaryButton(c, w*.70f, h*.72f, "登录");
        }
        drawSecondaryButton(c, cx, h*.82f, "退出游戏");
        drawSecondaryButton(c, 91f*density, 93f*density, "积分榜");
    }

    private void drawLeaderboard(Canvas c, float w, float h) {
        float cx = w*.5f;
        p.setColor(0xC8000000); c.drawRect(0, 0, w, h, p);
        p.setColor(0xF00A1420); c.drawRoundRect(w*.10f, h*.15f, w*.90f, h*.80f, 26f*density, 26f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.3f*density); p.setColor(0xFF6BE8F5);
        c.drawRoundRect(w*.10f, h*.15f, w*.90f, h*.80f, 26f*density, 26f*density, p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(24f*density); p.setColor(Color.WHITE);
        c.drawText("无尽积分榜", cx, h*.25f, p);
        ArrayList<String> users = new ArrayList<>(preferences.getStringSet(ACCOUNTS, Collections.<String>emptySet()));
        Collections.sort(users, (a, b) -> Integer.compare(preferences.getInt("endless_" + b, 0), preferences.getInt("endless_" + a, 0)));
        if (users.isEmpty()) {
            p.setTextSize(15f*density); p.setColor(0xFF95B6C0); c.drawText("暂无玩家记录", cx, h*.44f, p);
        } else {
            int shown = Math.min(5, users.size());
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(17f*density);
            for (int i = 0; i < shown; i++) {
                float y = h*(.34f + i*.075f);
                p.setColor(i == 0 ? 0xFFFFC65B : Color.WHITE);
                c.drawText((i+1) + ".  " + users.get(i), w*.22f, y, p);
                p.setTextAlign(Paint.Align.RIGHT); c.drawText(String.valueOf(preferences.getInt("endless_" + users.get(i), 0)), w*.78f, y, p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }
        drawSecondaryButton(c, cx, h*.72f, "返回");
    }

    private void drawColourChoice(Canvas c, float w, float h) {
        float cx = w*.5f, top = h*.23f, bottom = h*.77f;
        p.setColor(0xC9000000); c.drawRect(0, 0, w, h, p);
        p.setColor(0xF00B1623); c.drawRoundRect(w*.09f, top, w*.91f, bottom, 24f*density, 24f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.3f*density); p.setColor(0xFF79EAF7);
        c.drawRoundRect(w*.09f, top, w*.91f, bottom, 24f*density, 24f*density, p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(22f*density); p.setColor(Color.WHITE);
        c.drawText("选择你的颜色", cx, h*.34f, p);
        p.setTextSize(13f*density); p.setColor(0xFFB5D3DB);
        c.drawText("后续只能踩同色踏块，青色安全", cx, h*.39f, p);
        int[] colours = {RED_EDGE, ORANGE_EDGE, PINK_EDGE};
        String[] names = {"红", "橙", "粉"};
        for (int i = 0; i < 3; i++) {
            float x = w * (.28f + i*.22f), y = h*.55f;
            p.setColor(dim(colours[i], .28f)); c.drawCircle(x, y, 34f*density, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f*density); p.setColor(colours[i]);
            c.drawCircle(x, y, 34f*density, p); p.setStyle(Paint.Style.FILL);
            p.setTextSize(18f*density); p.setColor(Color.WHITE); c.drawText(names[i], x, y+6f*density, p);
        }
        p.setTextSize(11f*density); p.setColor(0xFF7298A4);
        c.drawText("选择后，约 2 秒抵达第一排彩色踏块", cx, h*.64f, p);
        drawSecondaryButton(c, cx, h*.71f, "返回主页");
    }

    private void drawProfile(Canvas c) {
        if (currentUser == null || currentUser.isEmpty()) return;
        int[] colours = {CENTRE_EDGE, RED_EDGE, 0xFFFFC65B, 0xFFB98CFF};
        int avatar = preferences.getInt("avatar_" + currentUser, 0) % colours.length;
        float x = 34f*density, y = 34f*density, r = 18f*density;
        p.setColor(0xB80A1821); c.drawRoundRect(10f*density, 10f*density, 174f*density, 58f*density, 24f*density, 24f*density, p);
        p.setColor(colours[avatar]); c.drawCircle(x, y, r, p);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(17f*density); p.setColor(Color.WHITE);
        c.drawText(currentUser.substring(0, 1), x, y + 6f*density, p);
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(13f*density); p.setColor(Color.WHITE);
        c.drawText(currentUser, 61f*density, y + 5f*density, p);
    }

    private void drawHomeConfirm(Canvas c, float w, float h) {
        float cx = w * .5f;
        p.setColor(0xB9000000); c.drawRect(0, 0, w, h, p);
        p.setColor(0xF20A1420); c.drawRoundRect(w*.11f, h*.32f, w*.89f, h*.66f, 24f*density, 24f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f*density); p.setColor(0xFFFFC65B);
        c.drawRoundRect(w*.11f, h*.32f, w*.89f, h*.66f, 24f*density, 24f*density, p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(20f*density); p.setColor(Color.WHITE);
        c.drawText("返回主页？", cx, h*.41f, p);
        p.setTextSize(13f*density); p.setColor(0xFFB9D6DD);
        c.drawText("将丢失当前关卡的进度", cx, h*.46f, p);
        drawSecondaryButton(c, w*.32f, h*.56f, "取消");
        drawButton(c, w*.68f, h*.56f, "确定");
    }

    private void drawDifficultyChoice(Canvas c, float w, float h) {
        float cx = w*.5f;
        p.setColor(0xB8000000); c.drawRect(0, 0, w, h, p);
        p.setColor(0xEE0A1420); c.drawRoundRect(w*.10f, h*.16f, w*.90f, h*.80f, 26f*density, 26f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.3f*density); p.setColor(0xFF6BE8F5);
        c.drawRoundRect(w*.10f, h*.16f, w*.90f, h*.80f, 26f*density, 26f*density, p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(24f*density); p.setColor(Color.WHITE);
        c.drawText("选择关卡难度", cx, h*.26f, p);
        String[] names = {"简单", "中等", "困难"};
        String[] notes = {"约 70 秒 · 平稳节奏", "约 60 秒 · 标准节奏", "约 50 秒 · 高速节奏"};
        int[] colours = {0xFF74E8A4, 0xFFFFC65B, 0xFFFF7586};
        for (int i = 0; i < 3; i++) {
            float y = h * (.43f + i*.11f);
            p.setColor(dim(colours[i], .22f)); c.drawRoundRect(w*.20f, y-29f*density, w*.80f, y+29f*density, 15f*density, 15f*density, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.1f*density); p.setColor(colours[i]);
            c.drawRoundRect(w*.20f, y-29f*density, w*.80f, y+29f*density, 15f*density, 15f*density, p); p.setStyle(Paint.Style.FILL);
            p.setTextSize(17f*density); p.setColor(Color.WHITE); c.drawText(names[i], cx, y-3f*density, p);
            p.setTextSize(11f*density); p.setColor(0xFFB5D4DC); c.drawText(notes[i], cx, y+16f*density, p);
        }
        drawSecondaryButton(c, cx, h*.74f, "返回");
    }

    private void drawResultOverlay(Canvas c, float w, float h, boolean won, int reason) {
        float cx = w*.5f, top = h*.20f, bottom = h*.78f;
        int accent = won ? 0xFF77E8A2 : reason == FAIL_VOID ? 0xFFAA8CFF : 0xFFFF7886;
        String title = won ? "天机已成" : reason == FAIL_VOID ? "坠入虚空" : "颜色错位";
        String note = won ? "抵达终点" : reason == FAIL_VOID ? "没有落在任何踏块上" : "踩到了非目标颜色";
        p.setColor(won ? 0xB7072012 : reason == FAIL_VOID ? 0xB4100925 : 0xB8250710); c.drawRect(0, 0, w, h, p);
        p.setColor(0xED0A1420); c.drawRoundRect(w*.12f, top, w*.88f, bottom, 26f*density, 26f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f*density); p.setColor(accent);
        c.drawRoundRect(w*.12f, top, w*.88f, bottom, 26f*density, 26f*density, p); p.setStyle(Paint.Style.FILL);
        if (won) {
            // The frozen track ends at this small finish flag before the celebration card.
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3f*density); p.setColor(accent);
            c.drawLine(cx-16f*density, h*.285f, cx-16f*density, h*.365f, p); p.setStyle(Paint.Style.FILL);
            path.reset(); path.moveTo(cx-15f*density, h*.288f); path.lineTo(cx+27f*density, h*.304f);
            path.lineTo(cx-15f*density, h*.327f); path.close(); c.drawPath(path, p);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(11f*density); p.setColor(0xFFBFEFD0);
            c.drawText("终点", cx+8f*density, h*.35f, p);
        } else {
            p.setColor(accent); c.drawCircle(cx, h*.335f, 20f*density, p);
        }
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(26f*density); p.setColor(Color.WHITE);
        c.drawText(title, cx, h*.415f, p);
        p.setTextSize(12f*density); p.setColor(0xFF9AB9C2); c.drawText(note, cx, h*.458f, p);
        p.setTextSize(12f*density); p.setColor(0xFF9AB9C2); c.drawText("本次得分", cx, h*.493f, p);
        p.setTextSize(34f*density); p.setColor(Color.WHITE); c.drawText(String.valueOf(score), cx, h*.555f, p);
        drawButton(c, cx, h*.63f, won ? "再来一局" : "重新挑战");
        drawSecondaryButton(c, cx, h*.71f, "返回主页");
    }

    private void drawSettings(Canvas c, float w, float h) {
        float cx = w*.5f, top = h*.12f, bottom = h*.72f;
        p.setColor(0xF014202B);
        c.drawRoundRect(w*.14f, top, w*.86f, bottom, 18f*density, 18f*density, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f*density); p.setColor(0xFF63D9E7);
        c.drawRoundRect(w*.14f, top, w*.86f, bottom, 18f*density, 18f*density, p); p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER); p.setTextSize(19f*density); p.setColor(Color.WHITE);
        c.drawText("设置", cx, h*.19f, p);
        drawSlider(c, w, h, "操控灵敏度", (sensitivity-.5f)/1.5f, h*.29f, String.format(java.util.Locale.US, "%.1f×", sensitivity));
        drawSlider(c, w, h, "音乐", musicVolume, h*.41f, Math.round(musicVolume*100) + "%");
        drawSlider(c, w, h, "音效", effectsVolume, h*.53f, Math.round(effectsVolume*100) + "%");
        p.setTextSize(11f*density); p.setColor(0xFF8DB4BD);
        c.drawText("左右拖动滑杆调整 · 游戏已暂停", cx, h*.585f, p);
        drawSecondaryButton(c, w*.33f, h*.65f, "返回主页");
        drawSecondaryButton(c, w*.67f, h*.65f, "返回游戏");
    }

    private void drawSlider(Canvas c, float w, float h, String title, float value, float y, String amount) {
        float left = w*.27f, right = w*.73f;
        p.setTextAlign(Paint.Align.LEFT); p.setTextSize(14f*density); p.setColor(Color.WHITE); c.drawText(title, left, y-15f*density, p);
        p.setTextAlign(Paint.Align.RIGHT); p.setTextSize(13f*density); p.setColor(0xFFAED6DD); c.drawText(amount, right, y-15f*density, p);
        p.setColor(0xFF193D49); c.drawRoundRect(left, y-4f*density, right, y+4f*density, 4f*density, 4f*density, p);
        float knobX = left + (right-left)*Math.max(0f, Math.min(1f, value));
        p.setColor(0xFF63D9E7); c.drawRoundRect(left, y-4f*density, knobX, y+4f*density, 4f*density, 4f*density, p);
        p.setColor(0xFFE7FEFF); c.drawCircle(knobX, y, 9f*density, p);
    }

    private void updateSettingsSlider(float x, float y) {
        float h = getHeight(), w = getWidth();
        float value = Math.max(0f, Math.min(1f, (x-w*.27f)/(w*.46f)));
        if (Math.abs(y-h*.29f) < 30f*density) sensitivity = .5f + value*1.5f;
        else if (Math.abs(y-h*.41f) < 30f*density) { musicVolume = value; applyMusicVolume(); }
        else if (Math.abs(y-h*.53f) < 30f*density) effectsVolume = value;
    }

    private static int mix(int from, int to, float amount) {
        amount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                (int)(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                (int)(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                (int)(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private static int dim(int color, float factor) {
        return Color.rgb((int)(Color.red(color) * factor), (int)(Color.green(color) * factor),
                (int)(Color.blue(color) * factor));
    }

    private static final int ROW_CYAN = 0;
    private static final int ROW_COLOUR = 1;
    private static final int ROW_OPTIONAL_SWITCH = 2;

    /** A group is one colour row plus two cyan rows; some groups add a third cyan row. */
    private static long groupStartFor(long tileId) {
        long start = 4L, group = 0L;
        while (tileId >= start) {
            long hash = group * 1103515245L + 12345L;
            boolean extended = Math.floorMod(hash ^ (hash >>> 13), 4L) == 0L;
            long next = start + (extended ? 4L : 3L);
            if (tileId < next) return start;
            start = next; group++;
        }
        return -1L;
    }

    private static int rowKind(long tileId) {
        if (tileId < 4L) return ROW_CYAN;
        long start = groupStartFor(tileId);
        if (tileId == start) return ROW_COLOUR;
        long group = 0L, probe = 4L;
        while (probe < start) {
            long hash = group * 1103515245L + 12345L;
            probe += Math.floorMod(hash ^ (hash >>> 13), 4L) == 0L ? 4L : 3L;
            group++;
        }
        long hash = group * 1103515245L + 12345L;
        boolean extended = Math.floorMod(hash ^ (hash >>> 13), 4L) == 0L;
        // In a three-cyan group, the second cyan row has the optional middle tile.
        return extended && tileId == start + 2L ? ROW_OPTIONAL_SWITCH : ROW_CYAN;
    }

    private static int optionalColour(long tileId, int currentColour) {
        int[] colours = {RED_EDGE, ORANGE_EDGE, PINK_EDGE};
        int current = 0;
        for (int i = 0; i < colours.length; i++) if (colours[i] == currentColour) current = i;
        long hash = tileId * 1103515245L + 67891L;
        return colours[(current + 1 + (int)Math.floorMod(hash >>> 9, 2L)) % 3];
    }

    private static int tileColor(long tileId, int lane, int currentColour) {
        int kind = rowKind(tileId);
        if (kind == ROW_CYAN) return CENTRE_EDGE;
        if (kind == ROW_OPTIONAL_SWITCH) return lane == 0 ? optionalColour(tileId, currentColour) : CENTRE_EDGE;
        long colourRowIndex = groupStartFor(tileId) - 4L;
        long hash = colourRowIndex * 1103515245L + 67891L;
        hash ^= hash >>> 16;
        int laneOrder = lane + 1;
        if ((hash & 1L) != 0L) laneOrder = 2 - laneOrder;
        int variant = (int)((laneOrder + Math.floorMod(hash >>> 8, 3L)) % 3L);
        if (variant == 0) return RED_EDGE;
        if (variant == 1) return ORANGE_EDGE;
        return PINK_EDGE;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        try {
            drawScene(c);
        } catch (Throwable ignored) {
            // Never let a graphics-driver quirk turn a simple game screen into a crash.
            c.drawColor(0xFF08111A);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(22 * density);
            p.setColor(Color.WHITE);
            c.drawText("天机滚珠", getWidth() * .5f, getHeight() * .5f, p);
        }
        postInvalidateOnAnimation();
    }

    private void drawScene(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w * .5f, horizon = h * .055f;
        float trackEnd = h;
        long now = SystemClock.elapsedRealtime();
        long elapsed = 0L;
        if (gameStartedAt >= 0L) {
            if (countdownEndsAt != 0L && now >= countdownEndsAt) {
                countdownEndsAt = 0L;
                lastFrameAt = now;
                startMusic();
            }
            if (!failed && !completed && !falling && !settingsOpen && !colourChoiceOpen && !confirmHomeOpen && countdownEndsAt == 0L) gameElapsed += now - lastFrameAt;
            lastFrameAt = now;
            elapsed = (failed || completed) ? frozenElapsed : gameElapsed;
        }
        float t = (elapsed % 5000L) / 5000f;
        float elapsedSeconds = elapsed / 1000f;
        int activeDifficulty = gameMode == MODE_LEVEL ? levelDifficulty : MEDIUM;
        float levelSeconds = activeDifficulty == EASY ? 70f : activeDifficulty == HARD ? 50f : 60f;
        float progressRatio = Math.min(1f, elapsedSeconds / (gameMode == MODE_LEVEL ? levelSeconds : 90f));
        float speedRamp = gameMode == MODE_LEVEL ? progressRatio : Math.min(.9f, elapsedSeconds / 150f);
        float startRate = activeDifficulty == EASY ? 1.75f : activeDifficulty == HARD ? 2.3f : 2f;
        float endRate = activeDifficulty == EASY ? 3.1f : activeDifficulty == HARD ? 4.7f : 4.0f;
        // The jump cycle also drives tile travel, so ball rhythm and the oncoming route
        // accelerate together without changing landing alignment.
        float cycleCount = elapsedSeconds * (startRate + (endRate - startRate) * .5f * speedRamp);
        long jumpCount = (long)Math.floor(cycleCount);
        float jumpPhase = cycleCount - jumpCount;
        int progress = Math.min(100, (int)(progressRatio * 100f));
        if (gameMode == MODE_LEVEL && gameStartedAt >= 0L && !failed && !completed && progress >= 100) {
            completed = true;
            frozenElapsed = elapsed;
            savePlayerProgress(100);
        }

        // Pure black space keeps the focus on the line of travel.
        c.drawColor(Color.BLACK);
        // A sparse, low-contrast star field stays only in the far distance.
        if (gameStartedAt >= 0L) {
            for (int i = 0; i < 13; i++) {
                float starX = ((i * 73 + 19) % 101) / 100f * w;
                float starY = h * (.075f + ((i * 31) % 29) / 100f);
                float glow = .45f + .35f * (float)Math.sin(t * Math.PI * 2 + i * 1.4f);
                p.setColor(Color.argb((int)(65 * glow), 185, 220, 245));
                c.drawCircle(starX, starY, (i % 5 == 0 ? 1.05f : .55f) * density, p);
            }
        }

        // Individual floating tiles replace the continuous track. Their world-space
        // spacing matches one jump, so the ball lands at a tile's centre.
        float groundBallY = h*.72f;
        float landingZ = (float)Math.sqrt((groundBallY - horizon) / (trackEnd - horizon));
        float radius = w * .098f;
        // Each landing tile is 0.7 ball diameters long and 0.7 ball diameters wide.
        float tileHalfWidthAtLanding = radius * .7f;
        float laneOffsetAtLanding = tileHalfWidthAtLanding * 3.25f;
        float depthSlopeAtLanding = 2f * (trackEnd - horizon) * landingZ;
        float tileHalfDepth = radius * .7f / depthSlopeAtLanding;
        float tileSpacing = tileHalfDepth * 2f * 1.32f;
        // Repeating exactly one spacing per jump is seamless: the next tile takes
        // the previous tile's place, so the path never runs out over time.
        float tileTravel = jumpPhase * tileSpacing;
        for (int i=-28; i<=28; i++) {
            float zCenter = landingZ + i * tileSpacing + tileTravel;
            float zTop = zCenter - tileHalfDepth;
            float zBottom = zCenter + tileHalfDepth;
            if (zTop <= 0f || zBottom >= 1f) continue;
            float yTop = horizon + (trackEnd - horizon) * zTop * zTop;
            float yBottom = horizon + (trackEnd - horizon) * zBottom * zBottom;
            float halfTop = tileHalfWidthAtLanding * zTop / landingZ;
            float halfBottom = tileHalfWidthAtLanding * zBottom / landingZ;
            for (int lane = -1; lane <= 1; lane++) {
                float xTop = cx + lane * laneOffsetAtLanding * zTop / landingZ;
                float xBottom = cx + lane * laneOffsetAtLanding * zBottom / landingZ;
                path.reset(); path.moveTo(xTop-halfTop, yTop); path.lineTo(xTop+halfTop, yTop);
                path.lineTo(xBottom+halfBottom, yBottom); path.lineTo(xBottom-halfBottom, yBottom); path.close();
                long tileId = jumpCount - i;
                int edge = tileColor(tileId, lane, requiredColour);
                p.setColor(dim(edge, .22f)); c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f*density); p.setColor(edge);
                c.drawPath(path, p); p.setStyle(Paint.Style.FILL);
            }
        }

        // A gentle, repeating jump keeps the ball above the same lower-middle landing point.
        lanePosition += (targetLane - lanePosition) * .22f;
        float ballX = cx + lanePosition * laneOffsetAtLanding;
        // The two gaps between the three lanes are protected by invisible air walls.
        // Only travelling beyond either outside lane can send the ball into the void.
        int currentLane = Math.round(Math.max(-1f, Math.min(1f, lanePosition)));
        int landingColour = tileColor(jumpCount, currentLane, requiredColour);
        // Only a completed jump counts. The opening colour tile selects the rule;
        // later cyan tiles are safe, while another colour must match the selection.
        if (gameStartedAt >= 0L && !failed && !completed && !colourChoiceOpen && jumpCount > 0L && jumpCount != lastLanding) {
            lastLanding = jumpCount;
            boolean isAboveTile = lanePosition > -1.5f && lanePosition < 1.5f;
            if (!isAboveTile) {
                falling = true;
                fallStartedAt = now;
                frozenElapsed = elapsed;
            } else if (rowKind(jumpCount) == ROW_OPTIONAL_SWITCH && currentLane == 0) {
                // The middle tile on an extended cyan group is optional: stepping on
                // it changes the active colour, while passing it keeps the old one.
                requiredColour = landingColour;
                ballTint = landingColour;
            } else if (landingColour != CENTRE_EDGE) {
                if (!colourChosen) {
                    colourChosen = true;
                    requiredColour = landingColour;
                    ballTint = landingColour;
                } else if (landingColour != requiredColour) {
                    failed = true;
                    failReason = FAIL_WRONG_COLOUR;
                    frozenElapsed = elapsed;
                } else {
                    ballTint = landingColour;
                }
            }
            if (!failed && !falling) {
                score++;
                savePlayerProgress(progress);
                landingEffectAt = now;
                landingEffectLane = currentLane;
                landingEffectColor = ballTint;
            }
        }
        float jumpAmount = (float)Math.sin(jumpPhase * Math.PI) * h * .105f;
        float ballY = groundBallY - jumpAmount;
        if (falling) {
            float fall = Math.min(1f, (now - fallStartedAt) / 500f);
            ballY = groundBallY + fall * fall * h * .48f;
        }

        // The struck tile fractures: dark cracks flash, then small pieces scatter
        // outward along its surface. The ball itself receives no landing glow.
        float effectAge = (now - landingEffectAt) / 300f;
        if (effectAge >= 0f && effectAge < 1f && !falling) {
            float effectX = cx + landingEffectLane * laneOffsetAtLanding;
            float effectY = groundBallY + radius*.53f;
            int alpha = (int)(190 * (1f - effectAge));
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f * density);
            p.setColor(Color.argb(alpha, 4, 14, 18));
            for (int i = 0; i < 5; i++) {
                double angle = -2.7 + i * 1.35;
                float crackX = effectX + (float)Math.cos(angle) * radius * .72f;
                float crackY = effectY + (float)Math.sin(angle) * radius * .20f;
                c.drawLine(effectX, effectY, crackX, crackY, p);
            }
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3.0 + .22;
                float travel = radius * (.18f + effectAge * .92f);
                float shardX = effectX + (float)Math.cos(angle) * travel;
                float shardY = effectY + (float)Math.sin(angle) * travel * .33f - effectAge*effectAge*radius*.18f;
                float shardSize = radius * (.15f - effectAge * .055f);
                path.reset();
                path.moveTo(shardX, shardY - shardSize);
                path.lineTo(shardX + shardSize*.72f, shardY + shardSize*.52f);
                path.lineTo(shardX - shardSize*.62f, shardY + shardSize*.42f);
                path.close();
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.argb(alpha, Color.red(landingEffectColor), Color.green(landingEffectColor), Color.blue(landingEffectColor)));
                c.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(density);
                p.setColor(Color.argb(alpha, 8, 24, 30)); c.drawPath(path, p);
            }
            p.setStyle(Paint.Style.FILL);
        }

        // A small, tight energy trail sits directly behind the ball.
        float tailEndY = ballY + h * .095f;
        path.reset();
        path.moveTo(ballX - radius*.52f, ballY + radius*.18f);
        path.lineTo(ballX - radius*.10f, tailEndY);
        path.lineTo(ballX + radius*.10f, tailEndY);
        path.lineTo(ballX + radius*.52f, ballY + radius*.18f);
        path.close();
        p.setShader(new LinearGradient(ballX, ballY, ballX, tailEndY,
                new int[]{Color.argb(136, Color.red(ballTint), Color.green(ballTint), Color.blue(ballTint)),
                        Color.argb(40, Color.red(ballTint), Color.green(ballTint), Color.blue(ballTint)),
                        Color.argb(0, Color.red(ballTint), Color.green(ballTint), Color.blue(ballTint))}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p); p.setShader(null);

        // The shadow remains on the track, contracting and fading while the ball is airborne.
        float heightRatio = falling ? 1f : jumpAmount / (h * .105f);
        float shadowY = groundBallY + radius*.78f;
        float shadowHalfWidth = radius * (1.18f - .55f * heightRatio);
        float shadowHalfHeight = radius * (.22f - .10f * heightRatio);
        int shadowAlpha = (int)(102 * (1f - .62f * heightRatio));
        p.setShader(new RadialGradient(ballX, shadowY, shadowHalfWidth*1.35f,
                new int[]{Color.argb(shadowAlpha, 0, 0, 0),0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawOval(ballX-shadowHalfWidth, shadowY-shadowHalfHeight, ballX+shadowHalfWidth, shadowY+shadowHalfHeight, p);
        p.setShader(null);
        // Plain, steady ball: its only motion accent is the trail behind it.
        p.setColor(ballTint); c.drawCircle(ballX, ballY, radius, p);

        if (gameStartedAt >= 0L) {
            p.setColor(Color.WHITE); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(25*density);
            c.drawText(String.valueOf(score), cx, h*.115f, p);
            p.setTextAlign(Paint.Align.LEFT); p.setTextSize(16*density);
            c.drawText(gameMode == MODE_ENDLESS ? "∞" : progress + "%", 20f*density, h*.075f, p);
        }

        // Minimal gear icon in the upper-right corner.
        float gearX = w - 36f*density, gearY = 36f*density;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.2f*density); p.setColor(0xFFD6FBFF);
        c.drawCircle(gearX, gearY, 10f*density, p); c.drawCircle(gearX, gearY, 3f*density, p);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float x1 = gearX + (float)Math.cos(a) * 12f*density, y1 = gearY + (float)Math.sin(a) * 12f*density;
            float x2 = gearX + (float)Math.cos(a) * 15f*density, y2 = gearY + (float)Math.sin(a) * 15f*density;
            c.drawLine(x1, y1, x2, y2, p);
        }
        p.setStyle(Paint.Style.FILL);

        if (gameStartedAt < 0L) {
            if (leaderboardOpen) drawLeaderboard(c, w, h);
            else if (difficultyChoiceOpen) drawDifficultyChoice(c, w, h);
            else drawStartOverlay(c, w, h);
        } else if (failed) {
            drawResultOverlay(c, w, h, false, failReason);
        } else if (completed) {
            drawResultOverlay(c, w, h, true, FAIL_NONE);
        }
        if (falling && now - fallStartedAt >= 500L) {
            falling = false;
            failed = true;
            failReason = FAIL_VOID;
            frozenElapsed = elapsed;
        }
        if (settingsOpen && !failed && !completed && gameStartedAt >= 0L) drawSettings(c, w, h);
        if (confirmHomeOpen && settingsOpen && gameStartedAt >= 0L) drawHomeConfirm(c, w, h);
        if (colourChoiceOpen && gameStartedAt >= 0L) drawColourChoice(c, w, h);
        if (countdownEndsAt != 0L && gameStartedAt >= 0L) {
            int count = (int)Math.ceil((countdownEndsAt - now) / 1000.0);
            p.setColor(0x99000000); c.drawRect(0, 0, w, h, p);
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(54*density); p.setColor(Color.WHITE);
            c.drawText(String.valueOf(Math.max(1, count)), cx, h*.5f, p);
        }
        if (gameStartedAt < 0L && !leaderboardOpen && !difficultyChoiceOpen) drawProfile(c);

    }
}
