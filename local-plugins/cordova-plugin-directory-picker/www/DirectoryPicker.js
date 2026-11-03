var exec = require('cordova/exec');

var DirectoryPicker = {
    select: function () {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'DirectoryPicker', 'select', []);
        });
    },
    check: function (uri) {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'DirectoryPicker', 'check', [uri]);
        });
    },
    list: function (uri) {
        return new Promise(function (resolve, reject) {
            exec(resolve, reject, 'DirectoryPicker', 'list', [uri]);
        });
    }
};

module.exports = DirectoryPicker;
