/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gameclosure.devkitPushPlugin;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.app.ActivityManager;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.net.Uri;
import java.util.ArrayList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONException;
import org.json.JSONObject;

import com.tealeaf.logger;
import com.tealeaf.plugin.plugins.DevkitPushPlugin;


/**
 * This {@code IntentService} does the actual handling of the GCM message.
 * {@code GcmBroadcastReceiver} (a {@code WakefulBroadcastReceiver}) holds a
 * partial wake lock for this service while the service does its work. When the
 * service is finished, it calls {@code completeWakefulIntent()} to release the
 * wake lock.
 */
public class GcmIntentService extends IntentService {
    private NotificationManager mNotificationManager;
    NotificationCompat.Builder builder;

    private static final int PUSH_GROUP_ID = 0;
    public static String TITLE = "TITLE";
    public static String MESSAGE = "MESSAGE";
    public static String IS_LOCAL = "IS_LOCAL";
    public static String ID = "ID";
    public static String SOURCE = "SOURCE";
    public static String TYPE = "TYPE";
    public static String DATA = "DATA";
    public static String EXTRAS = "EXTRAS";

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        logger.log("{devkit.push} GcmIntentService handling notification intent");

        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM will be
             * extended in the future with new message types, just ignore any message types you're
             * not interested in, or that you don't recognize.
             */
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                logger.log("{devkit.push} GCM Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                logger.log("{devkit.push} Deleted messages on GCM server: " + extras.toString());
            // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                Context context = getApplicationContext();

                String id = extras.getString("id");
                String title = extras.getString("title");
                String message = extras.getString("message");
                String appName = extras.getString("app_name");
                String data = extras.getString("data");


                final BasicNotificationInfo basicInfo = new BasicNotificationInfo();
                basicInfo.id = id;
                basicInfo.title = title;
                basicInfo.message = message;
                basicInfo.type = "push_notification";
                basicInfo.data = null;

                try {
                    basicInfo.data = new JSONObject(data);
                } catch (Exception e) {}

                basicInfo.count = 0;
                basicInfo.isLocal = false;
                basicInfo.extras = null;
                basicInfo.packageName = context.getPackageName();
                basicInfo.referrer = null;

                boolean isOpen = AppInfo.get().isOpen();
                if (isOpen) {
                    logger.log("{devkit.push} app is open - sending broadcast intent");
                    // TODO: if already running in foreground, send intent
                    // instead of putting message in status bar
                    Intent broadcastIntent = new Intent();
                    broadcastIntent.setAction(DevkitPushPlugin.PUSH_NOTIFICATION_INTENT_ACTION);
                    updateIntentWithBasicInfo(broadcastIntent, basicInfo);
                    sendBroadcast(broadcastIntent);
                } else {
                    logger.log("{devkit.push} app is in background - adding notification to status bar");
                    // put notification in status bar
                    showNotificationInStatusBar(context, basicInfo);
                }
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    public void updateIntentWithBasicInfo(Intent intent, BasicNotificationInfo basicInfo) {
        String dataStr = (basicInfo.data != null) ? basicInfo.data.toString() : null;
        intent.putExtra(ID, basicInfo.id);
        intent.putExtra(SOURCE, basicInfo.source);
        intent.putExtra(TITLE, basicInfo.title);
        intent.putExtra(MESSAGE, basicInfo.message);
        intent.putExtra(IS_LOCAL, basicInfo.isLocal);
        intent.putExtra(TYPE, basicInfo.type);
        intent.putExtra(DATA, dataStr);

        if (basicInfo.extras != null) {
            intent.putExtra(EXTRAS, basicInfo.extras);
        }
    }

    // shows a notification in the status bar
    public void showNotificationInStatusBar(Context context, BasicNotificationInfo basicInfo) {
        NotificationCompat.Builder mBuilder = null;
        if (basicInfo != null && (basicInfo.title != null || basicInfo.message != null)) {
            logger.log("{devkit.push} showNotificationInStatusBar:" , basicInfo.title);
            mBuilder = new NotificationCompat.Builder(context)
                .setAutoCancel(true)
                .setSmallIcon(context.getResources().getIdentifier("icon", "drawable", context.getPackageName()))
                .setLargeIcon(basicInfo.largeIcon)
                .setContentTitle(basicInfo.title)
                .setContentText(basicInfo.message)
                .setTicker(basicInfo.title + ": " + basicInfo.message)
                .setNumber(basicInfo.count)
                .setOnlyAlertOnce(false)
                .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);

        } else {
            //we need to have basic info, if not, fail...
            logger.log("{devkit.push} Error - Failed to show notification - missing required information");
            return;
        }

        //build the intent for clicking on the basic notification
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        updateIntentWithBasicInfo(intent, basicInfo);

        PendingIntent pending = PendingIntent.getActivity(context, PUSH_GROUP_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        //set content intent
        mBuilder.setContentIntent(pending);
        Notification notification = mBuilder.build();

        if (basicInfo.count > 1) {
            notification.number = basicInfo.count;
        }

        ((android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(PUSH_GROUP_ID, notification);
    }

    /* helper classes for showing notifications */

    //holds basic notification info essential for showing any notification
    private static class BasicNotificationInfo {
        String id;
        String title;
        String message;
        String source;
        String type;
        JSONObject data;
        int count;
        boolean isLocal;
        Bundle extras;
        Bitmap largeIcon;
        String packageName;
        String referrer;
    }
}
