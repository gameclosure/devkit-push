/* global caches: false, self: false, fetch: false, Cache: false, CacheStorage:
 false, importScripts: false, clients: false */

// db.js expects window to be defined
self.window = self;
importScripts('db.js');

self.addEventListener('notificationclick', function (event) {
  console.log('On notification click: ', event.notification.tag);

  // Android doesn't close the notification when you click on it. See:
  // http://crbug.com/463146
  event.notification.close();

  // This looks to see if the current is already open and focuses if it is
  event.waitUntil(
    getDb()
      .then(function (server) {
        return getSetting(server, 'href');
      })
      .then(function (href) {
        href = href || '/';
        return clients
          .matchAll({type: 'window'})
          .then(function(clientList) {
            for (var i = 0; i < clientList.length; i++) {
              var client = clientList[i];
              if (client.url == href && 'focus' in client) {
                return client.focus();
              }
            }

            if (clients.openWindow) {
              return clients.openWindow(href);
            }
          });
      })
  );
});

_useDemoPushHandler = false;
self.addEventListener('push', function (event) {
  demoPushHandler(event);
});

function demoPushHandler(event) {
  if (_useDemoPushHandler) {
    console.log('received push notification');
    // fetch notification content from your server if you need to
    // transfer data via web notifications
    showNotification({
      title: 'Push Notification Received',
      body: 'Demo Push Notification Content'
    });
  }
}


self.addEventListener('message', function (event) {
  switch (event.data.command) {
    case 'settings':
      var res = updateSettings(event.data.settings);
      if (event.waitUntil) {
        event.waitUntil(res);
      }
      break;
    case 'useDemoPushHandler':
      _useDemoPushHandler = !!event.data.useDemoPushHandler;
      break;
  }
});

function getDb() {
  return db.open({
      server: 'notifications',
      version: 1,
      schema: {
        // simple id store for recording seen notifications
        notifications: {
          key: {
            keyPath: 'id'
          },
          indexes: {
            id: { unique: true }
          }
        },
        // key-value store for notification settings
        settings: {
          key: {
            keyPath: 'key'
          },
          indexes: {
            key: { unique: true }
          }
        }
      }
    });
}

function updateSettings(settings) {
  return getDb()
    .then(function (server) {
      // TODO: no way to delete settings, probably ok
      var keys = Object.keys(settings);
      return Promise
        .all(keys.map(function (key) {
          var value = settings[key];
          console.log('set setting:', key, '<-', value);
          return server.settings
            .query('key')
            .filter('key', key)
            .modify({value: value})
            .execute()
            .then(function (results) {
              if (!results.length) {
                return server.settings.add({key: key, value: value});
              } else {
                return results[0];
              }
            });
        }))
        .then(function () {
          // if not already loaded, try to load
          loadPushHandler(server);
        });
    });
}

function getSetting(server, key) {
  return server
    .settings
    .query('key')
    .filter('key', key)
    .execute()
    .then(function (settings) {
      if (settings.length) {
        console.log('get setting:', key, '->', settings[0].value);
        return settings[0].value;
      }
      return undefined;
    });
}

function showIfUnseen(server, notification) {
  return server.notifications
    .query('id')
    .filter('id', notification.id)
    .execute()
    .then(function (results) {
      // already seen
      if (results.length) { return; }

      return Promise
        .all([
          showNotification(notification),
          server.notifications.add({id: notification.id})
        ]);
    });
}

function showNotification(notification) {
  console.log('showing', notification);
  return self.registration.showNotification(notification.title, {
      body: notification.body || notification.content,
      icon: notification.icon || 'resources/icons/icon.png',
      tag: notification.id // unique id, non-unique for replace
    });
}

function loadPushHandler(server) {
  if (_loadedPushHandler) { return; }

  return getSetting(server, 'pushHandler')
    .then(function (pushHandler) {
      if (pushHandler) {
        importScripts(pushHandler);
        _loadedPushHandler = true;
      }
    });
}

var _loadedPushHandler = false;
getDb().then(loadPushHandler);

self.addEventListener('activate', function (event) {
  console.log('activated!');
});
