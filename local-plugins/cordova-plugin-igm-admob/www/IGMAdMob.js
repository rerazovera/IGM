var exec = require('cordova/exec');
module.exports = {
  showBanner: function (adUnitId, success, error) {
    exec(success || function(){}, error || function(){}, 'IGMAdMob', 'showBanner', [adUnitId]);
  },
  hideBanner: function (success, error) {
    exec(success || function(){}, error || function(){}, 'IGMAdMob', 'hideBanner', []);
  }
};
