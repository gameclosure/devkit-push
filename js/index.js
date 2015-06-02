import device;
import Promise;
from devkit.errors import createErrorClass;

var hasNativeRegistration = NATIVE && NATIVE.events;
var nativeSendEvent = NATIVE && NATIVE.plugins && bind(NATIVE.plugins, 'sendEvent') || function () {};

var _worker;
var DevkitPush = Class(function(supr) {
  this.senderId;
  this.platform;

  // thrown if browser does not support push
  this.NotSupportedError = createErrorClass('NotSupportedError', 'NOT_SUPPORTED');

  // thrown if user did not allow push
  //   - if the user previously blocked push, code is BLOCKED_BY_USER
  //   - if the user just blocked push, code is PERMISSION_DENIED
  this.BlockedByUserError = createErrorClass('BlockedByUserError', 'BLOCKED_BY_USER');

  // thrown if an unknown error occurred during subscription
  //   - err.internalError may contain additional details
  this.SubscriptionFailedError = createErrorClass('SubscriptionFailedError', 'UNKNOWN_ERROR');

  this.init = function () {
    if (device.isIOS) {
      this.platform = 'apns';
    } else if (device.isAndroid) {
      this.platform = 'gcm';
      if (CONFIG &&
          CONFIG.modules &&
          CONFIG.modules.push &&
          CONFIG.modules.push.gcmSenderId) {
        this.senderId = CONFIG.modules.push.gcmSenderId;
      }
    } else if ('serviceWorker' in navigator) {
      this.platform = 'browser';
    } else {
      this.platform = 'unsupported';
    }

    // if on native, set up handlers and collect startup notifications if needed
    if (hasNativeRegistration) {
      NATIVE.events.registerHandler(
        'DevkitPushRegisterEvent',
        bind(this, 'onRegister')
      );
      NATIVE.events.registerHandler(
        'DevkitPushNotification',
        bind(this, 'onNotification')
      );

      // tell native we are listening in case it has pending notifications
      nativeSendEvent("DevkitPushPlugin", "readyForPush", "{}");
    }
  };

  /**
   * getPushToken
   *
   * Generate a push token (and request permission if necessary).
   * Callback is called with err, res where upon success res
   * is an object in the form:
   * {
   *   type: ['apns'|'gcm'|'browser'],
   *   token: 'pushtoken'
   * }
   */
  this.getPushToken = function (cb) {
    if (device.isMobileNative) {
      nativeSendEvent(
        "DevkitPushPlugin",
        "getPushToken",
        JSON.stringify({senderId: this.senderId})
      );
    }

    this._registrationCallback = cb;
    // if response already cached, call it immediately
    if (this._registrationResponse) {
      this.registrationCallback(
        this._registrationResponse.err,
        this._registrationResponse.res
      );
      this._registrationResponse = null;
    }
  };

  this.onRegister = function (data) {
    logger.log("DevkitPushRegisterEvent received in js");
    var err = !data || data.error;
    if (this._registrationCallback) {
      if (err) {
        logger.log("Failed to register push token");
      }
      this._registrationCallback(err, data);
      this._registrationResponse = null;
    } else {
      // store response if no callback
      this._registrationResponse = {err: err, res: data};
    }
  };

  this.onNotification = function (data) {
    var err = !data || data.error;
    if (err) {
      logger.log("error processing push notification");
    }

    if (this._notificationHandler) {
      this._notificationHandler(null, data);
      this._notificationResponse = null;
    } else {
      // store most recent notification if no handler
      this._notificationResponse = {err: null, res: data};
    }
  };


  /**
   * set a handler function for push notifications
   *
   * Function is called with err, res where upon success res
   * is an object in the form:
   * {
   *   id: 'push notification id',
   *   title: 'push title',
   *   message: 'push message',
   *   fromStatusBar: true/false if app was opened from notification,
   *   jsonExtras: {}  (optional object with additional push data)
   * }
   */
  this.setNotificationHandler = function (fn) {
    this._notificationHandler = fn;

    // if a notification already came in, send it now
    if (this._notificationResponse) {
      this._notificationHandler(
        this._notificationResponse.err,
        this._notificationResponse.res
      );
      this._notificationResponse = null;
    }
  };


  /**
   * register
   *
   * Promise that asks permission to notify the user (if necessary), generates
   * a push token, then resolves with an object in the form:
   * {
   *  pushToken: token,
   *  platform: platform ('gcm'|'apns'|'browser')
   * }
   */
  this.register = function () {
    if (device.isMobileNative) {
      return Promise.promisify(this.getPushToken)
        .bind(this)()
        .then(function(opts) {
          return {
            pushToken: opts.token,
            platform: opts.type
          };
        });
    } else if ('serviceWorker' in navigator) {
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
                return _serviceWorkerSubscribe(registration);
              } else {
                return {
                  pushToken: subscription.subscriptionId,
                  platform: exports.platform
                };
              }
            })
        })
        .catch(function(err) {
          logger.error(err);
          throw err;
        });
    }
  };

  // update worker settings - see web-worker.js for more details
  this.updateSettings = function (settings) {
    _worker && _worker.postMessage({
      command: 'settings',
      settings: settings
    });
  };

  // web worker debugging only - will show a placeholder notification
  this.useDemoPushHandler = function (useDemo) {
    logger.log("enabling demo push handler for web workers");
    _worker && _worker.postMessage({
      command: 'useDemoPushHandler',
      useDemoPushHandler: useDemo
    });
  };


});

exports = new DevkitPush();


// used in service worker notification manager subscription
function _serviceWorkerSubscribe(registration) {
  return registration.pushManager
    .subscribe({userVisibleOnly: true})
    .then(function(subscription) {
      return {
        pushToken: subscription.subscriptionId,
        platform: exports.platform
      };
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
        console.error("Error subscribing to push notifications:", e);
      }

      err.internalError = e;
      throw err;
    });
};
