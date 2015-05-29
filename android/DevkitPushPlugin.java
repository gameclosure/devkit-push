package com.tealeaf.plugin.plugins;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;


import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.util.DisplayMetrics;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageInfo;
import android.content.BroadcastReceiver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.RemoteViews;
import com.tealeaf.EventQueue;
import com.tealeaf.event.Event;
import com.tealeaf.logger;
import com.tealeaf.plugin.IPlugin;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.gameclosure.devkitPushPlugin.AppInfo;

/*
 * Creating from the android push example app. Tries to stay as close to the
 * original as possible for easy reference.
 * https://github.com/google/gcm/blob/master/gcm-client/GcmClient/src/main/java/com/google/android/gcm/demo/app/DemoActivity.java
 *
 */

public class DevkitPushPlugin extends BroadcastReceiver implements IPlugin {

    private static HashSet<String> eventIds = new HashSet<String>();
    private Activity _activity;
    protected Context _context;

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    public static final String PUSH_NOTIFICATION_INTENT_ACTION = "com.gameclosure.devkitPushPlugin.PushNotification";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    GoogleCloudMessaging gcm;
    String regid;
    String senderId;
    DevkitPushNotificationEvent launchNotification;

    public void onCreateApplication(Context applicationContext) {
        _context = applicationContext;

        // listen for intents when push notifications arrive
        IntentFilter filter = new IntentFilter();
        filter.addAction(PUSH_NOTIFICATION_INTENT_ACTION);
        _context.registerReceiver(this, filter);
    }

    public void onReceive(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        String type = extras.getString("TYPE");
        if (type.equals("push_notification")) {
            logger.log("{devkit.push} Received notification intent while running");
            EventQueue.pushEvent(new DevkitPushNotificationEvent(extras, false));
        }
    }


    public void onCreate(Activity activity, Bundle savedInstanceState) {
        _activity = activity;

        AppInfo.get(_activity);

        Bundle extras = activity.getIntent().getExtras();
        String type = extras.getString("TYPE");
        if (type.equals("push_notification")) {
            logger.log("{devkit.push} Application started from push notification");
            // store until requested
            launchNotification = new DevkitPushNotificationEvent(extras, true);
        }
    }

    public void onResume() {
        AppInfo.get().setIsOpen(true);
        // Check device for Play Services APK.
        checkPlayServices();
    }

    public void getPushToken(String jsonData) {

        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            senderId = jsonObject.getString("senderId");
            logger.log("{devkit.push} Generating push token with sender id:", senderId);
        } catch (Exception e) {
            logger.log("{devkit.push} senderId required to generate push token", e);
        }

        // Check device for Play Services APK. If check succeeds, proceed with GCM registration.
        if (senderId != "" && checkPlayServices()) {
            gcm = GoogleCloudMessaging.getInstance(_context);
            regid = getRegistrationId(_context);

            if (regid.isEmpty()) {
                registerInBackground();
            } else {
                logger.log("{devkit.push} existing registration id found:", regid);
                submitPushToken(false, regid);
            }
        } else {
            logger.log("{devkit.push} No valid Google Play Services APK found.");
        }
    }

    public void readyForPush(String jsonData) {
        // if launch message exists, send it now
        if (launchNotification != null) {
            EventQueue.pushEvent(launchNotification);
            launchNotification = null;
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(_context);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, _activity,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                logger.log("{devkit.push} This device is not supported with Google Play Services");
                // finish();
            }
            return false;
        }
        return true;
    }

     /**
     * Stores the registration ID and the app versionCode in the application's
     * {@code SharedPreferences}.
     *
     * @param context application's context.
     * @param regId registration ID
     */
    private void storeRegistrationId(Context context, String regId) {
        final SharedPreferences prefs = getGcmPreferences(context);
        int appVersion = getAppVersion(context);
        logger.log("{devkit.push} Saving push token on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }

    /**
     * Gets the current registration ID for application on GCM service, if there is one.
     * If result is empty, the app needs to register.
     *
     * @return registration ID, or empty string if there is no existing
     *         registration ID.
     */
    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGcmPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            logger.log("{devkit.push} Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            logger.log("{devkit.push} App version changed.");
            return "";
        }
        return registrationId;
    }

    /**
     * Registers the application with GCM servers asynchronously.
     *
     * Stores the registration ID and the app versionCode in the application's
     * shared preferences.
     */
    private void registerInBackground() {
        logger.log("{devkit.push} registering for push token in background");
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                String msg = "";
                try {
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(_context);
                    }
                    regid = gcm.register(senderId);
                    msg = "Device registered, registration ID=" + regid;

                    // You should send the registration ID to your server over HTTP, so it
                    // can use GCM/HTTP or CCS to send messages to your app.
                    sendRegistrationIdToBackend();

                    // For this demo: we don't need to send it because the device will send
                    // upstream messages to a server that echo back the message using the
                    // 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(_context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            @Override
            protected void onPostExecute(String msg) {
                // logger.log("{devkit.push} " + msg);
            }
        }.execute(null, null, null);
    }

     /**
     * @return Application's version code from the {@code PackageManager}.
     */
    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }

    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGcmPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return _context.getSharedPreferences(
                DevkitPushPlugin.class.getSimpleName(),
                Context.MODE_PRIVATE
            );
    }
    /**
     * Sends the registration ID to your server over HTTP, so it can use GCM/HTTP or CCS to send
     * messages to your app.
     */
    private void sendRegistrationIdToBackend() {
      submitPushToken(false, regid);
    }

    private void submitPushToken(boolean newToken, String token) {
        logger.log("{devkit.push} Push Token: " + token);
        EventQueue.pushEvent(new DevkitPushRegisterEvent(token, newToken));
    }

    public void onNewIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        String type = extras.getString("TYPE");
        if (type.equals("push_notification")) {
            logger.log("{devkit.push} Running application resumed from push notification");
            EventQueue.pushEvent(new DevkitPushNotificationEvent(extras, true));
        }
    }

    public static class DevkitPushRegisterEvent extends Event {
        protected String token;
        protected String type;
        protected boolean newToken;

        public DevkitPushRegisterEvent(String token, boolean newToken) {
            super("DevkitPushRegisterEvent");
            this.token = token;
            this.type = "gcm";
            this.newToken = newToken;
        }
    }

    public static class DevkitPushNotificationEvent extends Event {
        protected String id;
        protected String source;
        protected String title;
        protected String message;
        protected boolean isLocal;
        protected boolean fromStatusBar;
        protected String type;
        protected String jsonExtras;

        public DevkitPushNotificationEvent(String id, String source, String title, String message, boolean isLocal, boolean fromStatusBar, String type, String jsonExtras) {
            super("DevkitPushNotification");
            this.id = id;
            this.source = source;
            this.title = title;
            this.message = message;
            this.isLocal = isLocal;
            this.fromStatusBar = fromStatusBar;
            this.type = type;
            this.jsonExtras = jsonExtras;
        }

        public DevkitPushNotificationEvent(Bundle extras, boolean fromStatusBar) {
            super("DevkitPushNotification");
            this.id = extras.getString("ID");
            this.source = "";
            this.title = extras.getString("TITLE");
            this.message = extras.getString("MESSAGE");
            this.isLocal = false;
            this.fromStatusBar = fromStatusBar;
            this.type = "";
            this.jsonExtras = extras.getString("DATA");
        }
    }

    public void setInstallReferrer(String referrer) {}
    public void onActivityResult(Integer request, Integer resultCode, Intent data) {}
    public void onDestroy() {}
    public void onStop() {
        AppInfo.get().setIsOpen(false);
    };
    public void onStart() {
        AppInfo.get().setIsOpen(true);
    };
    public void onPause() {
        AppInfo.get().setIsOpen(false);
    };
}

