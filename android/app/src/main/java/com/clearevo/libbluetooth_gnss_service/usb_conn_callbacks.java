package com.clearevo.libbluetooth_gnss_service;

/**
 * Callback interface for USB serial connection events.
 * Follows the same pattern as rfcomm_conn_callbacks.
 */
public interface usb_conn_callbacks extends readline_callbacks {
    /**
     * Called when USB permission request completes.
     * @param granted true if permission was granted, false if denied
     */
    void on_usb_permission_result(boolean granted);

    /**
     * Called when USB serial connection is successfully established.
     * @param deviceName the name/path of the connected USB device
     * @param baudRate the baud rate used for the connection
     */
    void on_usb_connected(String deviceName, int baudRate);

    /**
     * Called when USB serial connection is disconnected.
     * @param reason description of why the disconnection occurred
     */
    void on_usb_disconnected(String reason);

    /**
     * Called when an error occurs during USB operations.
     * @param error description of the error
     */
    void on_usb_error(String error);
}
