package com.example.aistudyassistant.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SharedPrefManager {
    private static final String SHARED_PREF_NAME = "AIStudyAssistantPrefs";
    private static SharedPrefManager instance;
    private Context context;

    private SharedPrefManager(Context context) {
        this.context = context;
    }

    public static synchronized SharedPrefManager getInstance(Context context) {
        if (instance == null) {
            instance = new SharedPrefManager(context);
        }
        return instance;
    }

    public void saveToken(String token) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.TOKEN_KEY, token);
        editor.apply();
    }

    public String getToken() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(Constants.TOKEN_KEY, null);
    }

    public void saveUserInfo(String name, String email, String avatarUrl) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.USER_NAME_KEY, name);
        editor.putString(Constants.USER_EMAIL_KEY, email);
        editor.putString(Constants.USER_AVATAR_KEY, avatarUrl);
        editor.apply();
    }

    public String getUserAvatar() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(Constants.USER_AVATAR_KEY, null);
    }

    public String getUserName() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(Constants.USER_NAME_KEY, "Student");
    }

    public String getUserEmail() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        return sharedPreferences.getString(Constants.USER_EMAIL_KEY, null);
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public void logout() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();
    }
}
