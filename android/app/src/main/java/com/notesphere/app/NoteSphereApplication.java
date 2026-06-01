package com.notesphere.app;

import android.app.Application;
import android.content.Context;

public class NoteSphereApplication extends Application {
    private static Context instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Context getAppContext() {
        return instance;
    }
}
