package com.clearevo.bluetooth_gnss;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import com.clearevo.libbluetooth_gnss_service.bluetooth_gnss_service;

public class StartConnectionReceiver extends BroadcastReceiver {
    public static final String TAG = "btgnss_scr";
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("bluetooth.CONNECT".equals(intent.getAction())) {
            try {
                // Try to load saved config, fallback to empty map if file doesn't exist
                HashMap<String, Object> connectArgs;
                try {
                    connectArgs = Util.load_last_connect_args(context);
                } catch (Exception e) {
                    Log.d(TAG, "No saved config found, using intent args only: " + e.getMessage());
                    connectArgs = new HashMap<>();
                }

                // Override with intent extras
                final Bundle extras = intent.getExtras();
                if (extras != null) {
                    final String configStr = extras.getString("config");
                    overrideConnectionWithOptions(connectArgs, configStr);
                }

                // Only connect if we have minimum required args (bluetooth or usb device)
                if (connectArgs.containsKey("bdaddr") || connectArgs.containsKey("usb_device_name")) {
                    Util.connect(context, connectArgs);
                } else {
                    Log.e(TAG, "Missing device address (bdaddr or usb_device_name) - cannot connect. " +
                            "Either connect once via the app first, or provide device info in intent config.");
                }
            } catch (Exception e) {
                Log.d(TAG, "StartConnectionReceiver onreceive got exception: " + Log.getStackTraceString(e));
            }
        }
    }

    private void overrideConnectionWithOptions(HashMap<String, Object> connectArgs, String overriddenOptions) {
        Objects.requireNonNull(connectArgs, "connectArgs must already been initialised");
        if (overriddenOptions != null && !overriddenOptions.isEmpty()) {
            try {
                final JSONObject overrides = new JSONObject(overriddenOptions);

                ArrayList<String> all_args = new ArrayList<String>();
                all_args.addAll(Arrays.asList(bluetooth_gnss_service.BT_CONNECT_ARGS));
                all_args.addAll(Arrays.asList(bluetooth_gnss_service.BT_MOCK_ARGS));
                all_args.addAll(Arrays.asList(bluetooth_gnss_service.NTRIP_CONNECT_ARGS));
                for (String pk : all_args) {
                    final String value = overrides.optString(pk, null);
                    if (value != null) connectArgs.put(pk, value);
                }
                //legacy 'extra' args
                final JSONObject overrides_extra_params = overrides.optJSONObject("extra");
                if (overrides_extra_params != null) {
                    for (String pk : bluetooth_gnss_service.NTRIP_CONNECT_ARGS) {
                        final String value = overrides_extra_params.optString(pk, null);
                        if (value != null) connectArgs.put(pk, value);
                    }
                }
            } catch (Exception e) {
                Log.e(this.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }
}