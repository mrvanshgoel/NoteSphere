package com.notesphere.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefManager {
    private static final String PREF_NAME = "NoteSpherePrefs";
    private static final String KEY_TOKEN = "auth_token";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_AVATAR = "user_avatar";
    private static final String KEY_ONBOARDING_SHOWN = "onboarding_shown";
    private static final String KEY_STUDY_STREAK = "study_streak";
    private static final String KEY_LAST_ACTIVE_DATE = "last_active_date";
    private static final String KEY_STORAGE_PATH = "storage_path";

    private static SharedPrefManager instance;
    private final Context context;

    private SharedPrefManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────

    public void saveToken(String token) {
        prefs().edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs().getString(KEY_TOKEN, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void logout() {
        prefs().edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_AVATAR)
            .apply();
    }

    // ─── User Info ───────────────────────────────────────────────────────────

    public void saveUserInfo(String name, String email, String avatarUrl) {
        prefs().edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_AVATAR, avatarUrl)
            .apply();
    }

    public void saveAvatarUrl(String url) {
        prefs().edit().putString(KEY_USER_AVATAR, url).apply();
    }

    public String getAvatarUrl() {
        return prefs().getString(KEY_USER_AVATAR, "");
    }

    /** @deprecated Use getAvatarUrl() */
    @Deprecated
    public String getUserAvatar() {
        return getAvatarUrl();
    }

    public String getUserName() {
        return prefs().getString(KEY_USER_NAME, "Student");
    }

    public String getUserEmail() {
        return prefs().getString(KEY_USER_EMAIL, null);
    }

    public String getUserId() {
        return prefs().getString("user_id", null);
    }

    public void saveUserId(String userId) {
        prefs().edit().putString("user_id", userId).apply();
    }

    // ─── Onboarding ──────────────────────────────────────────────────────────

    public boolean isOnboardingShown() {
        return prefs().getBoolean(KEY_ONBOARDING_SHOWN, false);
    }

    public void setOnboardingShown() {
        prefs().edit().putBoolean(KEY_ONBOARDING_SHOWN, true).apply();
    }

    // ─── Study Streak ────────────────────────────────────────────────────────

    public int getStudyStreak() {
        return prefs().getInt(KEY_STUDY_STREAK, 0);
    }

    public void updateStudyStreak() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
            java.util.Locale.getDefault()).format(new java.util.Date());
        String lastActive = prefs().getString(KEY_LAST_ACTIVE_DATE, "");

        if (today.equals(lastActive)) return; // Already counted today

        java.util.Calendar yesterday = java.util.Calendar.getInstance();
        yesterday.add(java.util.Calendar.DATE, -1);
        String yesterdayStr = new java.text.SimpleDateFormat("yyyy-MM-dd",
            java.util.Locale.getDefault()).format(yesterday.getTime());

        int streak = lastActive.equals(yesterdayStr)
            ? getStudyStreak() + 1
            : 1; // Reset if gap

        prefs().edit()
            .putInt(KEY_STUDY_STREAK, streak)
            .putString(KEY_LAST_ACTIVE_DATE, today)
            .apply();
    }

    // ─── Storage Path ────────────────────────────────────────────────────────

    public String getStoragePath() {
        return prefs().getString(KEY_STORAGE_PATH, null);
    }

    public void setStoragePath(String path) {
        prefs().edit().putString(KEY_STORAGE_PATH, path).apply();
    }
}
