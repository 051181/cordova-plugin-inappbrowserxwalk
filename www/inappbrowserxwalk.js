/*global cordova, module*/

function InAppBrowserXwalk() {
 
}

var callbacks = new Array ();

InAppBrowserXwalk.prototype = {
    close: function () {
        cordova.exec(null, null, "InAppBrowserXwalk", "close", []);
    },
    addEventListener: function (eventname, func) {
        callbacks[eventname] = func;
    },
    removeEventListener: function (eventname) {
        callbacks[eventname] = undefined;
    },
    show: function () {
        cordova.exec(null, null, "InAppBrowserXwalk", "show", []);
    },
    hide: function () {
        cordova.exec(null, null, "InAppBrowserXwalk", "hide", []);
    },
    stopLoading: function (scrpt) {
        cordova.exec(null, null, "InAppBrowserXwalk", "stopLoading", []);
    },
    // executeScript: function (scrpt) {
    //     cordova.exec(null, null, "InAppBrowserXwalk", "executeScript", [scrpt]);
    // }
    executeScript: function(injectDetails, cb) {
        if (injectDetails.code) {
            cordova.exec(cb, null, "InAppBrowserXwalk", "executeScript", [injectDetails.code, !!cb]);
        // } else if (injectDetails.file) {
        //     exec(cb, null, "InAppBrowser", "injectScriptFile", [injectDetails.file, !!cb]);
        } else {
            throw new Error('executeScript requires exactly one of code or file to be specified');
        }
    },
}

var callback = function(event) {
    if (event.type === "loadstart" && callbacks['loadstart'] !== undefined) {
        callbacks['loadstart'](event.url);
    }
    if (event.type === "loadstop" && callbacks['loadstop'] !== undefined) {
        callbacks['loadstop'](event.url);
    }
    if (event.type === "exit" && callbacks['exit'] !== undefined) {
        callbacks['exit']();
    }
    if (event.type === "jsCallback" && callbacks['jsCallback'] !== undefined) {
        callbacks['jsCallback'](event.result);
    }
}

module.exports = {
    open: function (url, options) {
        options = (options === undefined) ? "{}" : JSON.stringify(options);
        cordova.exec(callback, null, "InAppBrowserXwalk", "open", [url, options]);
        return new InAppBrowserXwalk();
    }
};
