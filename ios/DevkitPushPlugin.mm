#import "DevkitPushPlugin.h"
#import "platform/log.h"
#import "PluginManager.h"

@implementation DevkitPushPlugin




- (id) init {
    self = [super init];
    if (!self) {
        return nil;
    }

    self.nextID = 0;
    return self;
}

- (void)application:(UIApplication *)application didRegisterUserNotificationSettings:(UIUserNotificationSettings *)notificationSettings {
    NSLOG(@"{devkit.push} didRegisterUserNotificationSettings");

}

- (void) didFailToRegisterForRemoteNotificationsWithError:(NSError *)error application:(UIApplication *)app {
    NSLOG(@"{devkit.push} Failed to register for remote notifications");
    [[PluginManager get] dispatchJSEvent:@{
                                           @"name": @"DevkitPushRegisterEvent",
                                           @"type": @"apns",
                                           @"error": @true
                                           }];
}

- (void) didRegisterForRemoteNotificationsWithDeviceToken:(NSData *)deviceToken application:(UIApplication *)app {
    NSLOG(@"{devkit.push} Registering for remote notifications");
    NSString *deviceTokenString = [[[deviceToken description]
                stringByTrimmingCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@"<>"]] stringByReplacingOccurrencesOfString:@" " withString: @""];
    dispatch_time_t t = dispatch_time(DISPATCH_TIME_NOW, 1000000000 * 15);
    dispatch_after(t, dispatch_get_main_queue(), ^{
            NSLOG(@"{devkit.push} Registering for remote notifications");
        [[PluginManager get] dispatchJSEvent:@{
                                        @"name": @"DevkitPushRegisterEvent",
                                        @"token": deviceTokenString,
                                        @"type": @"apns"
                                        }];
    });
}

- (NSDictionary*) getNotificationEventWithID: (int) id andTitle: (NSString *) title isLocal: (bool) isLocal isFromStatusBar: (bool) fromStatusBar {
    NSString *idString = [NSString stringWithFormat:@"%i", id];
    return @{
             @"name": @"DevkitPushNotification",
             @"id": idString,
             @"title": title,
             @"isLocal": isLocal ? @true : @false,
             @"fromStatusBar": fromStatusBar ? @true : @false};
}

- (void) didReceiveRemoteNotification:(NSDictionary *)userInfo application:(UIApplication *)app {
    NSLOG(@"{devkit.push} Received remote notification");
    bool didLaunch = app.applicationState == UIApplicationStateInactive ||
           app.applicationState == UIApplicationStateBackground;

    NSString *title = [[userInfo objectForKey:@"aps"] objectForKey:@"alert"];
    [[PluginManager get] dispatchJSEvent: [self getNotificationEventWithID: self.nextID andTitle: title isLocal:false isFromStatusBar:didLaunch]];
    self.nextID++;
}

- (void) onPause {
    NSLOG(@"{devkit.push} Paused: Clearing icon badge counter");
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
}

- (void) onResume {
    NSLOG(@"{devkit.push} Resumed: Clearing icon badge counter");
    [[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
}

- (void) getPushToken:(NSDictionary *)jsonObject {
    NSLOG(@"{devkit.push} Requesting notification permission");
    // TODO: can we check if we already have permission?
    if ([[[UIDevice currentDevice] systemVersion] floatValue] >= 8.0) {
        [[UIApplication sharedApplication] registerUserNotificationSettings:
            [UIUserNotificationSettings settingsForTypes:
            (UIUserNotificationTypeSound | UIUserNotificationTypeAlert | UIUserNotificationTypeBadge) categories:nil]];
        [[UIApplication sharedApplication] registerForRemoteNotifications];
    } else {
        [[UIApplication sharedApplication] registerForRemoteNotificationTypes:
            (UIRemoteNotificationTypeBadge | UIRemoteNotificationTypeSound | UIRemoteNotificationTypeAlert)];
    }
}

- (void) applicationDidBecomeActive:(UIApplication *)app {
}

- (void) initializeWithManifest:(NSDictionary *)manifest appDelegate:(TeaLeafAppDelegate *)appDelegate {
    @try {
        TeaLeafAppDelegate *app = (TeaLeafAppDelegate *)[[UIApplication sharedApplication] delegate];
    }
    @catch (NSException *exception) {
        NSLOG(@"{devkit.push} WARNING: Exception during initialization: %@", exception);
    }
}


@end
