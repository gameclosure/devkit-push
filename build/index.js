var path = require('path');
var File = require('vinyl');

var getPath = function (filePath) {
  return path.join(__dirname, '..', filePath);
};

exports.onBeforeBuild = function (devkitAPI, app, config, cb) {
  if (config.browser) {
    config.browser.copy.push(getPath('js/push-worker.js'));

    if (config.browser.webAppManifest) {
      config.browser.webAppManifest.gcm_sender_id = app.manifest.modules.push.gcmSenderId;
      config.browser.webAppManifest.gcm_user_visible_only = true;
    }
  }

  cb && cb();
};
