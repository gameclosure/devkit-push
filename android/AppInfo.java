package com.gameclosure.devkitPushPlugin;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.app.Activity;


// http://steveliles.github.io/is_my_android_app_currently_foreground_or_background.html
// Modified to not throw exceptions on uninitialized get since we only need to
// know if the app is showing or not
public class AppInfo implements Application.ActivityLifecycleCallbacks {

    private static AppInfo instance;
    private boolean isOpen;

    public static void init(Application app) {
        if (instance == null) {
            instance = new AppInfo();
            app.registerActivityLifecycleCallbacks(instance);
        }
    }

    public static AppInfo get(Application application) {
        if (instance == null) {
            init(application);
        }
        return instance;
    }

    public static AppInfo get(Context ctx) {
        if (instance == null) {
            Context appCtx = ctx.getApplicationContext();
            if (appCtx instanceof Application) {
                init((Application)appCtx);
            }
        }
        return instance;
    }

    public static AppInfo get() {
        if (instance == null) {
            instance = new AppInfo();
            instance.setIsOpen(false);
        }
        return instance;
    }
    public AppInfo() {}

    public boolean isOpen() {
        return isOpen;
    }

    public boolean isBackground() {
        return !isOpen;
    }

    public void setIsOpen(boolean isOpen) {
        isOpen = isOpen;
    }

    public void onActivityPaused(Activity activity) {
        isOpen = false;
    }

    public void onActivityResumed(Activity activity) {
        isOpen = true;
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {}
    public void onActivityDestroyed(Activity activity) {
        isOpen = false;
    }
    public void onActivityStopped(Activity activity) {
        isOpen = false;
    }
    public void onActivityStarted(Activity activity) {}
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}
}
