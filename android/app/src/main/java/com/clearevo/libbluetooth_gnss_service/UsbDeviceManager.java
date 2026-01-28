package com.clearevo.libbluetooth_gnss_service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages USB serial device detection and enumeration.
 * Handles attach/detach events and provides device list to Flutter via platform channel.
 */
public class UsbDeviceManager {

    private static final String TAG = "UsbDeviceManager";

    public static final String ACTION_USB_DEVICE_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DEVICE_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

    private final Context context;
    private final UsbManager usbManager;
    private UsbEventListener listener;
    private BroadcastReceiver usbReceiver;
    private boolean receiverRegistered = false;

    public interface UsbEventListener {
        void onUsbDeviceAttached(UsbDevice device);
        void onUsbDeviceDetached(UsbDevice device);
    }

    public UsbDeviceManager(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public void setListener(UsbEventListener listener) {
        this.listener = listener;
    }

    /**
     * Register the BroadcastReceiver for USB attach/detach events.
     */
    public void registerReceiver() {
        if (receiverRegistered) {
            return;
        }

        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device == null) return;

                Log.d(TAG, "USB event: " + action + " device: " + device.getDeviceName());

                if (ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    if (listener != null) {
                        listener.onUsbDeviceAttached(device);
                    }
                } else if (ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    if (listener != null) {
                        listener.onUsbDeviceDetached(device);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
        receiverRegistered = true;
        Log.d(TAG, "USB BroadcastReceiver registered");
    }

    /**
     * Unregister the BroadcastReceiver.
     */
    public void unregisterReceiver() {
        if (receiverRegistered && usbReceiver != null) {
            try {
                context.unregisterReceiver(usbReceiver);
            } catch (Exception e) {
                Log.d(TAG, "unregisterReceiver exception: " + e.getMessage());
            }
            receiverRegistered = false;
            Log.d(TAG, "USB BroadcastReceiver unregistered");
        }
    }

    /**
     * Get list of connected USB serial devices.
     * Uses usb-serial-for-android library to detect supported serial devices.
     *
     * @return List of device info maps suitable for platform channel
     */
    public List<Map<String, Object>> getUsbDevices() {
        List<Map<String, Object>> deviceList = new ArrayList<>();

        if (usbManager == null) {
            Log.d(TAG, "UsbManager is null");
            return deviceList;
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        Log.d(TAG, "Found " + availableDrivers.size() + " USB serial drivers");

        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            Map<String, Object> deviceInfo = new HashMap<>();

            deviceInfo.put("deviceId", device.getDeviceId());
            deviceInfo.put("deviceName", device.getDeviceName());
            deviceInfo.put("vendorId", device.getVendorId());
            deviceInfo.put("productId", device.getProductId());
            deviceInfo.put("driverType", driver.getClass().getSimpleName());

            // These may be null on some devices/API levels
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String manufacturerName = device.getManufacturerName();
                String productName = device.getProductName();
                String serialNumber = null;
                try {
                    serialNumber = device.getSerialNumber();
                } catch (SecurityException e) {
                    // Permission required for serial number on some devices
                    Log.d(TAG, "Cannot get serial number: " + e.getMessage());
                }

                deviceInfo.put("manufacturerName", manufacturerName != null ? manufacturerName : "");
                deviceInfo.put("productName", productName != null ? productName : "");
                deviceInfo.put("serialNumber", serialNumber != null ? serialNumber : "");
            } else {
                deviceInfo.put("manufacturerName", "");
                deviceInfo.put("productName", "");
                deviceInfo.put("serialNumber", "");
            }

            // Number of ports on this device
            deviceInfo.put("portCount", driver.getPorts().size());

            deviceList.add(deviceInfo);
            Log.d(TAG, "Found USB device: " + deviceInfo);
        }

        return deviceList;
    }

    /**
     * Check if USB host mode is supported on this device.
     */
    public boolean isUsbHostSupported() {
        return context.getPackageManager().hasSystemFeature("android.hardware.usb.host");
    }

    /**
     * Get a USB device by its device ID.
     *
     * @param deviceId The device ID to search for
     * @return The UsbDevice if found, null otherwise
     */
    public UsbDevice getDeviceById(int deviceId) {
        if (usbManager == null) {
            return null;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getDeviceId() == deviceId) {
                return device;
            }
        }
        return null;
    }
}
