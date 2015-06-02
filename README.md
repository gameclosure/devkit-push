# Game Closure DevKit Plugin: Push Notifications

This plugin supports iOS via APNs and both Android and chrome browser
notifications via GCM.


## Devkit Support

The Devkit Push plugin requires devkit version 3+ and devkit-core 3+.


## Overview

Push notifications allow you to interact with your users by sending remote
messages from an external server that appear in the phone's status area,
even if the app is in the background or closed.

Before an application can receive notifications, it must generate a push
token and submit it to an external server (you'll need to implement your
own push server or use a third party service). On some platforms (iOS, browser),
this requires asking the user for permission to show notifications, so you
may wish to delay asking for a token until the user is fully engaged (although
plenty of successful games request notification permission on startup).

Once a push token has been generated for a device, a push server with the
matching credentials can send notifications to that device.


## Installation

Install the Devkit Push module using the standard devkit install process:

~~~
devkit install https://github.com/gameclosure/devkit-push
~~~


## GCM Setup

For android and chrome browser notifications, you will need to add your GCM
sender ID to your manifest under `modules.push.gcmSenderId` as shown below.
Visit the [GCM docs](https://developers.google.com/cloud-messaging/) for more
info.

manifest.json
~~~
  "modules": {
    "push": {
      "gcmSenderId": "xxxxxxxxxxxxxxx"
    }
  }
~~~

## APNs Setup

APNs requires no additional setup on the client side.


## Usage

You can import the `devkit.push` object anywhere in your application:

~~~
import devkit.push;
~~~

Request a push token by using `devkit.push.register`, which returns a promise
that will be resolved with an object with keys `pushToken` and `platform`, where
platform is 'gcm', 'apns', 'browser', or 'unsupported'.

~~~
var onRegister = devkit.push.register();
if (onRegister) {
    onRegister
        .then(function (data) {
            logger.log(
                "Received push token - ",
                "Token: ", data.pushToken,
                "Platform: ", data.platform
            );
        })
}
~~~

Listen for push notifications by setting a notification handler on the
devkit.push plugin. This will be called whenever a push notification is
delivered while your app is open or if your app is started via the user
clicking a notification in their status bar.

~~~
devkit.push.setNotificationHandler(function (err, res) {
  this.showMessage(
    res.title,
    res.message
    // res.fromStatusBar ? "Launched App" : void 0
    // res.jsonExtras
  );
  if (!err) {
    logger.log(
      "Received Notification - ",
      "Title:", res.title,
      "Message:", res.message,
      "fromStatusBar:", res.fromStatusBar, // true if launched app
      "jsonExtra:", res.jsonExtras // extra data sent from server
    );
  }
});
~~~

### Chrome Notifications - ADVANCED

Chrome push notifications do not yet have the ability to transfer data, so
to use them effectively you will likely need to query an external server
for your notification data and display notifications accordingly. This will
change in the future, but for now, this is pretty much your only option.


If you just want to see chrome notifications without having to set a custom
handler and fetching notifications from a server, you can call the following,
which will show a demo notification every time a push is received.

NOTE: this will get set back to false whenever the push worker restart - this
is ONLY for demonstrating notifications.

~~~
devkit.push.useDemoPushHandler(true);
~~~


To show real notification content, you can inject an external script into the
web worker that adds an event listener for the `push` event and performs
whatever actions you need in order to create a notification.

Specify this script (*after* the register promise resolves) by calling
`devkit.push.updateSettings` with the `pushHandler` parameter. Additional
parameters will be stored using db.js and accessible via the `getSetting`
function. For example, here is an `updateSettings` call that stores a server
endpoint as well.

~~~
devkit.push.updateSettings({
  notificationFetchEndpoint: endpoint,
  pushHandler: 'custom-push-worker.js'
});
~~~

`custom-push-worker.js` will need to add an event listener for `push` events and
show notifications manually. Here is a completely untested example that could
work, including reading the `notificationFetchEndpoint` url set in the code
above, querying a server, and showing notifications.

~~~
self.addEventListener('push', function (event) {
  console.log('received push');
  event.waitUntil(showPendingPush(event));
});

function showPendingPush(event) {
  return getDb().then(function (server) {
    return getPendingNotifications(server)
      .then(function (notifications) {
        return Promise.all(notifications.map(showIfUnseen.bind(this, server)));
      });
  });
}

function getPendingNotifications(server) {
  return getSetting(server, 'notificationFetchEndpoint')
    .then(function (notificationFetchEndpoint) {
      return fetch(notificationFetchEndpoint, {credentials: 'include'});
    })
    .then(function (response) {
      return response.text();
    })
    .then(function (body) {
      var response = JSON.parse(body);
      var success = response && response[0];
      var notifications = (response &&
        response.length > 0 &&
        response[1] &&
        response[1].notifications);

      if (!response || !success || !notifications || !notifications.length) {
        console.error('no notifications found!');
        throw new Error('No notifications found');
      }

      return notifications;
    });
}
~~~



## Demo Application

Check out the [devkit push demo
application](https://github.com/gameclosure/demoDevkitPush) for an example
working implementation.
