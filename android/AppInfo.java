package com.gameclosure.devkitPushPlugin;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.app.Activity;


// http://steveliles.github.io/is_my_android_app_currently_foreground_or_background.html
public class AppInfo implements Application.ActivityLifecycleCallbacks {

    private static AppInfo instance;
    private boolean isOpen;

    public static void init(Application app){
        if (instance == null){
            instance = new AppInfo();
            app.registerActivityLifecycleCallbacks(instance);
        }
    }

    public static AppInfo get(Application application){
        if (instance == null) {
            init(application);
        }
        return instance;
    }

    public static AppInfo get(Context ctx){
        if (instance == null) {
            Context appCtx = ctx.getApplicationContext();
            if (appCtx instanceof Application) {
                init((Application)appCtx);
            }
            throw new IllegalStateException(
                "AppInfo is not initialised and " +
                "cannot obtain the Application object");
        }
        return instance;
    }

    public static AppInfo get(){
        if (instance == null) {
            instance = new AppInfo();
            instance.setIsOpen(false);
            // throw new IllegalStateException(
            //     "AppInfo is not initialised - invoke " +
            //     "at least once with parameterised init/get");
        }
        return instance;
    }
    public AppInfo(){}

    public boolean isOpen(){
        return isOpen;
    }

    public boolean isBackground(){
        return !isOpen;
    }

    public void setIsOpen(boolean isOpen) {
        isOpen = isOpen;
    }

    public void onActivityPaused(Activity activity){
        isOpen = false;
    }

    public void onActivityResumed(Activity activity){
        isOpen = true;
    }

    public void onActivityCreated(Activity activity, Bundle bundle) {}
    public void onActivityDestroyed(Activity activity) {}
    public void onActivityStopped(Activity activity) {}
    public void onActivityStarted(Activity activity) {}
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {}
}
