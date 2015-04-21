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

exports.register = function () {
  if ('serviceWorker' in navigator) {
    return Promise.resolve(navigator.serviceWorker.register('push-worker.js'))
      .then(function initialize (serviceWorkerRegistration) {
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

        // Do we already have a push message subscription?
        return serviceWorkerRegistration.pushManager
          .getSubscription()
          .then(function(subscription) {
            if (!subscription) {
              return subscribe(serviceWorkerRegistration);
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

function subscribe (serviceWorkerRegistration) {
  return serviceWorkerRegistration.pushManager
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
