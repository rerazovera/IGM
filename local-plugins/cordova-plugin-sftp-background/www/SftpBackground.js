var exec = require('cordova/exec');
module.exports = {
    start: function (config, success, error) { exec(success || function(){}, error || function(){}, 'SftpBackground', 'start', [config || {}]); },
    stop: function (success, error) { exec(success || function(){}, error || function(){}, 'SftpBackground', 'stop', []); },
    status: function (success, error) { exec(success || function(){}, error || function(){}, 'SftpBackground', 'status', []); },
    logs: function (success, error) { exec(success || function(){}, error || function(){}, 'SftpBackground', 'logs', []); },
    clearLogs: function (success, error) { exec(success || function(){}, error || function(){}, 'SftpBackground', 'clearLogs', []); }
};
