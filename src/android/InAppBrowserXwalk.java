package com.shoety.plugin;

import com.shoety.plugin.BrowserDialog;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.res.Resources;
import org.apache.cordova.*;
import org.apache.cordova.PluginManager;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaWebViewImpl;

import org.crosswalk.engine.XWalkWebViewEngine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.internal.XWalkViewInternal;
import org.xwalk.core.XWalkCookieManager;
import org.crosswalk.engine.XWalkCordovaView;

import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.graphics.Typeface;
import android.widget.Toast;

import android.webkit.WebResourceResponse;

import org.xwalk.core.XWalkJavascriptResult;

public class InAppBrowserXwalk extends CordovaPlugin {

    private BrowserDialog dialog;
    private XWalkView xWalkWebView;
    private CallbackContext callbackContext;

    private String urlLoading = "";

    protected static final String LOG_TAG = "InAppBrowserXwalk";

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {

        if(action.equals("open")) {
            this.callbackContext = callbackContext;
            this.openBrowser(data);
        }

        else if(action.equals("close")) {
            this.closeBrowser();
        }

        else if(action.equals("show")) {
            this.showBrowser();
        }

        else if(action.equals("hide")) {
            this.hideBrowser();
        }

        else if(action.equals("stopLoading")) {
            this.stopLoading();
        }

        else if (action.equals("executeScript")) {
            // this.injectJS(data.getString(0));
            String jsWrapper = null;
            if (data.getBoolean(1)) {
                jsWrapper = String.format("(function(){prompt(JSON.stringify([eval(%%s)]), 'gap-iab://%s')})()", callbackContext.getCallbackId());
            }
            injectDeferredObject(data.getString(0), jsWrapper);
        }

        return true;
    }

    class MyUIClient extends XWalkUIClient {
        MyUIClient(XWalkView view) {
            super(view);
        }

        // File Chooser
        @Override
        public void openFileChooser(XWalkView view, final ValueCallback<Uri> uploadFile, String acceptType, String capture) {
            uploadFile.onReceiveValue(null);
            Log.e("X WALK", "walk");
            //cordova.setActivityResultCallback();

            cordova.setActivityResultCallback(new CordovaPlugin() {
                @Override
                public void onActivityResult(int requestCode, int resultCode, Intent intent) {
                    Log.e("X WALK", "return");
                    xWalkWebView.onActivityResult(requestCode, resultCode, intent);
                }
            });

        }

        @Override
        public void onPageLoadStarted(XWalkView view, String url) {
            super.onPageLoadStarted(view, url);
            Log.e("X WALK load", url);

            try {
                JSONObject obj = new JSONObject();
                obj.put("type", "loadstart");
                obj.put("url", url);
                PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            } catch (JSONException ex) {}
        }

        @Override
        public void onPageLoadStopped(XWalkView view, String url, XWalkUIClient.LoadStatus status) {
            super.onPageLoadStopped(view, url, status);
            Log.e("X WALK loaded", url);

            try {

                JSONObject obj = new JSONObject();
                obj.put("type", "loadstop");
                obj.put("url", url);
                PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                result.setKeepCallback(true);
                callbackContext.sendPluginResult(result);
            } catch (JSONException ex) {}
        }

        /**
         * Tell the client to display a prompt dialog to the user.
         * If the client returns true, WebView will assume that the client will
         * handle the prompt dialog and call the appropriate JsPromptResult method.
         *
         * The prompt bridge provided for the InAppBrowser is capable of executing any
         * oustanding callback belonging to the InAppBrowser plugin. Care has been
         * taken that other callbacks cannot be triggered, and that no other code
         * execution is possible.
         *
         * To trigger the bridge, the prompt default value should be of the form:
         *
         * gap-iab://<callbackId>
         *
         * where <callbackId> is the string id of the callback to trigger (something
         * like "InAppBrowser0123456789")
         *
         * If present, the prompt message is expected to be a JSON-encoded value to
         * pass to the callback. A JSON_EXCEPTION is returned if the JSON is invalid.
         *
         * @param view
         * @param url
         * @param message
         * @param defaultValue
         * @param result
         */
        @Override
        public boolean onJsPrompt(XWalkView view, String url, String message, String defaultValue, XWalkJavascriptResult result) {
            // See if the prompt string uses the 'gap-iab' protocol. If so, the remainder should be the id of a callback to execute.
            if (defaultValue != null && defaultValue.startsWith("gap")) {
                if(defaultValue.startsWith("gap-iab://")) {
                    PluginResult scriptResult;
                    String scriptCallbackId = defaultValue.substring(10);
                    if (scriptCallbackId.startsWith("InAppBrowserXwalk")) {
                        if(message == null || message.length() == 0) {
                            scriptResult = new PluginResult(PluginResult.Status.OK, new JSONArray());
                        } else {
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("type", "jsCallback");
                                obj.put("result",  Boolean.parseBoolean(message));
                                scriptResult = new PluginResult(PluginResult.Status.OK, obj);
                            } catch(JSONException e) {
                                scriptResult = new PluginResult(PluginResult.Status.JSON_EXCEPTION, e.getMessage());
                            }
                        }
                        scriptResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(scriptResult);
                        result.confirm();
                        return true;
                    }
                }
                else
                {
                    // Anything else with a gap: prefix should get this message
                    LOG.w(LOG_TAG, "InAppBrowser does not support Cordova API calls: " + url + " " + defaultValue); 
                    result.cancel();
                    return true;
                }
            }
            return false;
        }

    }

    class MyResourceClient extends XWalkResourceClient {
           MyResourceClient(XWalkView view) {
               super(view);
           }
            /*
           @Override
           public void onLoadStarted (XWalkView view, String url) {
               if(xWalkWebView == null) return;
               try {

                   if(urlLoading.equals(view.getUrl())) return;
                   urlLoading = view.getUrl();

                   JSONObject obj = new JSONObject();
                   obj.put("type", "loadstart");
                   obj.put("url", view.getUrl());
                   PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                   result.setKeepCallback(true);
                   callbackContext.sendPluginResult(result);
               } catch (JSONException ex) {}
           }

           @Override
           public void onLoadFinished (XWalkView view, String url) {
               if(xWalkWebView == null) return;
               try {

                   JSONObject obj = new JSONObject();
                   obj.put("type", "loadstop");
                   obj.put("url", view.getUrl());
                   PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                   result.setKeepCallback(true);
                   callbackContext.sendPluginResult(result);
               } catch (JSONException ex) {}
           }
           */
    }

    private void openBrowser(final JSONArray data) throws JSONException {
        final String url = data.getString(0);
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog = new BrowserDialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);

                //XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, true);

                xWalkWebView = new XWalkView(cordova.getActivity(), cordova.getActivity());
                xWalkWebView.setLayoutParams(new LinearLayout.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT));
                xWalkWebView.setResourceClient(new MyResourceClient(xWalkWebView));
                xWalkWebView.setUIClient(new MyUIClient(xWalkWebView));
                xWalkWebView.load(url, null);
                //xWalkWebView = new XWalkView(cordova.getActivity(), cordova.getActivity());
                //XWalkCookieManager mCookieManager = new XWalkCookieManager();
                //mCookieManager.setAcceptCookie(true);
                //mCookieManager.setAcceptFileSchemeCookies(true);
                //xWalkWebView.setResourceClient(new MyResourceClient(xWalkWebView));
                //xWalkWebView.setUIClient(new MyUIClient(xWalkWebView));
                //xWalkWebView.load(url, "");


                String toolbarColor = "#FFFFFF";
                int toolbarHeight = 80;
                String closeButtonText = "< Close";
                int closeButtonSize = 25;
                String closeButtonColor = "#000000";
                boolean openHidden = false;

                if(data != null && data.length() > 1) {
                    try {
                            JSONObject options = new JSONObject(data.getString(1));

                            if(!options.isNull("toolbarColor")) {
                                toolbarColor = options.getString("toolbarColor");
                            }
                            if(!options.isNull("toolbarHeight")) {
                                toolbarHeight = options.getInt("toolbarHeight");
                            }
                            if(!options.isNull("closeButtonText")) {
                                closeButtonText = options.getString("closeButtonText");
                            }
                            if(!options.isNull("closeButtonSize")) {
                                closeButtonSize = options.getInt("closeButtonSize");
                            }
                            if(!options.isNull("closeButtonColor")) {
                                closeButtonColor = options.getString("closeButtonColor");
                            }
                            if(!options.isNull("openHidden")) {
                                openHidden = options.getBoolean("openHidden");
                            }
                        }
                    catch (JSONException ex) {

                    }
                }

                LinearLayout main = new LinearLayout(cordova.getActivity());
                main.setOrientation(LinearLayout.VERTICAL);

                RelativeLayout toolbar = new RelativeLayout(cordova.getActivity());
                toolbar.setBackgroundColor(android.graphics.Color.parseColor(toolbarColor));
                toolbar.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, toolbarHeight));
                toolbar.setPadding(5, 5, 5, 5);

                TextView closeButton = new TextView(cordova.getActivity());
                closeButton.setText(closeButtonText);
                closeButton.setTextSize(closeButtonSize);
                closeButton.setTextColor(android.graphics.Color.parseColor(closeButtonColor));
                closeButton.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
                toolbar.addView(closeButton);

                closeButton.setOnClickListener(new View.OnClickListener() {
                     public void onClick(View v) {
                         closeBrowser();
                     }
                 });

                main.addView(toolbar);
                main.addView(xWalkWebView);

                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.getWindow().getAttributes().windowAnimations = android.R.style.Animation_Dialog;
                dialog.setCancelable(true);
                LayoutParams layoutParams = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
                dialog.addContentView(main, layoutParams);
                if(!openHidden) {
                    dialog.show();
                }

            }
        });
    }

    public void hideBrowser() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(dialog != null) {
                    dialog.hide();
                }
            }
        });
    }

    public void showBrowser() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(dialog != null) {
                    dialog.show();
                }
            }
        });
    }

    public void stopLoading() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                xWalkWebView.stopLoading();
            }
        });
    }

    public void closeBrowser() {
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                xWalkWebView.onDestroy();
                xWalkWebView = null;
                dialog.dismiss();
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type", "exit");
                    PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                    result.setKeepCallback(true);
                    callbackContext.sendPluginResult(result);
                } catch (JSONException ex) {}
            }
        });
    }

    /**
     * Inject an object (script or style) into the InAppBrowser WebView.
     *
     * This is a helper method for the inject{Script|Style}{Code|File} API calls, which
     * provides a consistent method for injecting JavaScript code into the document.
     *
     * If a wrapper string is supplied, then the source string will be JSON-encoded (adding
     * quotes) and wrapped using string formatting. (The wrapper string should have a single
     * '%s' marker)
     *
     * @param source      The source object (filename or script/style text) to inject into
     *                    the document.
     * @param jsWrapper   A JavaScript string to wrap the source string in, so that the object
     *                    is properly injected, or null if the source string is JavaScript text
     *                    which should be executed directly.
     */
    private void injectDeferredObject(String source, String jsWrapper) {
        String scriptToInject;
        if (jsWrapper != null) {
            org.json.JSONArray jsonEsc = new org.json.JSONArray();
            jsonEsc.put(source);
            String jsonRepr = jsonEsc.toString();
            String jsonSourceString = jsonRepr.substring(1, jsonRepr.length()-1);
            scriptToInject = String.format(jsWrapper, jsonSourceString);
        } else {
            scriptToInject = source;
        }
        final String finalScriptToInject = scriptToInject;
        LOG.e(LOG_TAG, "###################################: " + finalScriptToInject); 
        this.cordova.getActivity().runOnUiThread(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    // This action will have the side-effect of blurring the currently focused element
                    xWalkWebView.load("javascript:" + finalScriptToInject, null);
                } else {
                    xWalkWebView.evaluateJavascript(finalScriptToInject, null);
                }
            }
        });
    }

    public void injectJS(String source) {

        final String finalScriptToInject = source;
        this.cordova.getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {

                xWalkWebView.evaluateJavascript(finalScriptToInject, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String scriptResult) {

                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("type", "jsCallback");
                            obj.put("result", scriptResult);
                            PluginResult result = new PluginResult(PluginResult.Status.OK, obj);
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException ex) {}
                    }
                });
            }
        });
    }
}
