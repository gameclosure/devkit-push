import device;

/*
import Promise;
from devkit.errors import createErrorClass;

// thrown if browser does not support push
exports.NotSupportedError = createErrorClass('NotSupportedError', 'NOT_SUPPORTED');

// thrown if user did not allow push
//   - if the user previously blocked push, code is BLOCKED_BY_USER
//   - if the user just blocked push, code is PERMISSION_DENIED
exports.BlockedByUserError = createErrorClass('BlockedByUserError', 'BLOCKED_BY_USER');

// thrown if an unknown error occurred during subscription
//   - err.internalError may contain additional details
exports.SubscriptionFailedError = createErrorClass('SubscriptionFailedError', 'UNKNOWN_ERROR');
*/
exports.hasNativeRegistration = GLOBAL.NATIVE && NATIVE.events;
var nativeSendEvent = NATIVE && NATIVE.plugins && bind(NATIVE.plugins, 'sendEvent') || function () {};

var _nativeRegistration;
if (exports.hasNativeRegistration) {
  exports.senderId = null;
  exports.platform = null;
  if (device.isIOS) {
    exports.platform = 'apns';
  } else if (device.isAndroid) {
    exports.platform = 'gcm';

    if (CONFIG &&
        CONFIG.modules &&
        CONFIG.modules.devkitpush &&
        CONFIG.modules.devkitpush.gcmSenderId) {

      exports.senderId = CONFIG.modules.devkitpush.gcmSenderId;
    }
  } else {
    exports.platform = 'unsupported';
  }
  // TODO: support web accurately

  NATIVE.events.registerHandler('DevkitPushRegisterEvent', bind(this, function (data) {
    logger.log("{devkitpush} DevkitPushRegisterEvent received");

    var err = !data || data.error;
    if (err) {
      logger.log("{devkitpush} Failed to register push token");
    }
    // call register callback if it exists
    if (this._registrationCallback) {
      this._registrationCallback(err, data);
      this._registrationResponse = null;
    } else {
      // store response if no callback
      this._registrationResponse = {err: err, res: data};
    }
  }));

  NATIVE.events.registerHandler('DevkitPushNotification', bind(this, function (data) {
    var err = !data || data.error;
    if (err) {
      logger.log("{devkitpush} error processing push notification");
    }

    logger.log("{devkitpush} push notification!", data);
    if (this._notificationCallback) {
      this._notificationCallback(null, data);
      this._notificationResponse = null;
    } else {
      // store most recent notification if no callback?
      this._notificationResponse = {err: null, res: data};
    }
  }));

  // tell native we are listening in case it is waiting
  nativeSendEvent("DevkitPushPlugin", "readyForPush", "{}");
}

/**
 * getPushToken
 *
 * Generate a push token (and request permission if necessary.
 */
exports.getPushToken = function () {
    if (exports.hasNativeRegistration) {
      // permissions are only required on ios
      if (device.isIOS) {
        nativeSendEvent("DevkitPushPlugin", "getPushToken", "{}");
      } else {
        nativeSendEvent(
          "DevkitPushPlugin",
          "getPushToken",
          JSON.stringify({senderId: this.senderId})
        );
      }
  }
};

/**
 * set a callback for push tokens
 *
 * Push tokens are generated automatically on android or in response to a
 * permission request in ios.
 *
 * Callback is called with err, res where upon success res
 * is an object in the form:
 * {
 *   type: ['apns'|'gcm'],
 *   token: 'pushtoken'
 * }
 */
exports.setRegistrationCallback = function (cb) {
  logger.log("{devkitpush} setting push token registration callback");
  this._registrationCallback = cb;

  // if response already cached, call it immediately
  if (this._registrationResponse) {
    logger.log("{devkitpush} token found - firing callback immediately");
    this.registrationCallback(
      this._registrationResponse.err,
      this._registrationResponse.res
    );
    this._registrationResponse = null;
  }
};

/**
 * set a callback for push notifications
 *
 * Callback is called with err, res where upon success res
 * is an object in the form:
 * {
 *   id: 'push notification id',
 *   title: 'push title',
 *   message: 'push message',
 *   fromStatusBar: true/false if app was opened from notification,
 *   jsonExtras: {}  (optional object with additional push data)
 * }
 */
exports.setNotificationCallback = function (cb) {
  this._notificationCallback = cb;

  // if a notification already came in, send it now
  if (this._notificationResponse) {
    this._notificationCallback(
      this._notificationResponse.err,
      this._notificationResponse.res
    );
    this._notificationResponse = null;
  }
};




var _worker;

exports.register = function () {
  if ('serviceWorker' in navigator) {

    var browserPromise;
    try {
      browserPromise = navigator.serviceWorker.register('push-worker.js');
    } catch (e) {
      return Promise.reject(e);
    }

    return Promise.resolve(browserPromise)
      .then(function initialize (registration) {
        if (!('showNotification' in ServiceWorkerRegistration.prototype)) {
          throw new exports.NotSupportedError('Notifications aren\'t supported.');
        }

        // Check the current Notification permission. If its denied, it's a
        // permanent block until the user changes the permission
        if (Notification.permission === 'denied') {
          throw new exports.BlockedByUserError('The user has blocked notifications.');
        }

        // Check if push messaging is supported
        if (!('PushManager' in window)) {
          throw new exports.NotSupportedError('Push messaging isn\'t supported.');
        }

        if (registration.installing) {
          logger.log('push worker: installing');
        } else if (registration.waiting) {
          logger.log('push worker: waiting (close and reopen tab)');
        } else if (registration.active) {
          logger.log('push worker: ready');
        } else {
          logger.error('push worker: unknown status???');
        }

        // try to grab the just-registered worker to send it the cache message
        _worker = registration.installing || registration.waiting || registration.active;

        // set the default target window to open when notification is clicked
        exports.updateSettings({
          href: location.toString()
        });

        // Do we already have a push message subscription?
        return registration.pushManager
          .getSubscription()
          .then(function(subscription) {
            if (!subscription) {
              return subscribe(registration);
            } else {
              return subscription.subscriptionId;
            }
          })
      })
      .catch(function(err) {
        logger.warn(err);
        throw err;
      });
  }
};

exports.updateSettings = function (settings) {
  _worker.postMessage({
    command: 'settings',
    settings: settings
  });
};

function subscribe (registration) {
  return registration.pushManager
    .subscribe()
    .then(function(subscription) {
      return subscription.subscriptionId;
    })
    .catch(function(e) {
      // wrap the error in either BlockedByUserError or SubscriptionFailedError
      var err;
      if (Notification.permission === 'denied') {
        // The user denied the notification permission which means we failed to
        // subscribe and the user will need to manually change the notification
        // permission to subscribe to push messages
        err = new exports.BlockedByUserError('Permission for Notifications was denied', 'PERMISSION_DENIED');
      } else {
        // A problem occurred with the subscription; common reasons include
        // network errors, and lacking gcm_sender_id and/or
        // gcm_user_visible_only in the manifest.
        err = new exports.SubscriptionFailedError('Unable to subscribe to push.');
      }

      err.internalError = e;
      throw err;
    });
}
