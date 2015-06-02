var path = require('path');

var getPath = function (filePath) {
  return path.join(__dirname, '..', filePath);
};

exports.onBeforeBuild = function (devkitAPI, app, config, cb) {
  if (config.browser && config.browser.copy && config.browser.copy.push) {
    config.browser.copy.push(getPath('js/push-worker.js'));
    config.browser.copy.push(getPath('js/db.js'));
  }

  if (config.browser && config.browser.webAppManifest) {
    if (!app.manifest.modules || !app.manifest.modules.push || !app.manifest.modules.push.gcmSenderId) {
      console.warn('[warn] No gcmSenderId found in manifest, push will be disabled (modules.push.gcmSenderId)');
    } else {
      config.browser.webAppManifest.gcm_sender_id = app.manifest.modules.push.gcmSenderId;
      config.browser.webAppManifest.gcm_user_visible_only = true;
    }
  }

  cb && cb();
};
