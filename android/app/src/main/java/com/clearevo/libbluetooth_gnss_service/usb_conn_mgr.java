package com.clearevo.libbluetooth_gnss_service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * USB Serial Connection Manager.
 * Manages USB serial connections following the same patterns as rfcomm_conn_mgr.
 * Provides InputStream/OutputStream for GNSS data transfer.
 */
public class usb_conn_mgr implements Closeable {

    private static final String TAG = "btgnss_usbmgr";
    private static final String ACTION_USB_PERMISSION = "com.clearevo.bluetooth_gnss.USB_PERMISSION";

    private final Context m_context;
    private final UsbManager m_usb_manager;
    private usb_conn_callbacks m_callback;

    private UsbDevice m_target_usb_device;
    private UsbDeviceConnection m_usb_connection;
    private UsbSerialPort m_usb_serial_port;
    private UsbSerialInputStream m_usb_is;
    private UsbSerialOutputStream m_usb_os;

    private int m_baud_rate = 115200;
    private volatile boolean closed = false;
    private boolean m_permission_receiver_registered = false;

    private List<Closeable> m_cleanup_closables = new ArrayList<>();
    private Thread m_conn_state_watcher;

    private final BroadcastReceiver m_permission_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (usb_conn_mgr.this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

                    Log.d(TAG, "USB permission result: granted=" + granted + " device=" + (device != null ? device.getDeviceName() : "null"));

                    if (m_callback != null) {
                        m_callback.on_usb_permission_result(granted);
                    }
                }
            }
        }
    };

    /**
     * Constructor for USB connection manager.
     *
     * @param context Application context
     * @param callback Callback for connection events
     */
    public usb_conn_mgr(Context context, usb_conn_callbacks callback) {
        m_context = context.getApplicationContext();
        m_usb_manager = (UsbManager) m_context.getSystemService(Context.USB_SERVICE);
        m_callback = callback;
    }

    /**
     * Set the callback for connection events.
     */
    public void setCallback(usb_conn_callbacks callback) {
        m_callback = callback;
    }

    /**
     * Check if the app has permission to access the USB device.
     */
    public boolean hasPermission(UsbDevice device) {
        return m_usb_manager != null && m_usb_manager.hasPermission(device);
    }

    /**
     * Request permission to access the USB device.
     * Result will be delivered via on_usb_permission_result callback.
     */
    public void requestPermission(UsbDevice device) {
        if (m_usb_manager == null) {
            Log.d(TAG, "UsbManager is null, cannot request permission");
            if (m_callback != null) {
                m_callback.on_usb_error("USB not supported on this device");
            }
            return;
        }

        m_target_usb_device = device;

        // Register receiver if not already registered
        if (!m_permission_receiver_registered) {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                m_context.registerReceiver(m_permission_receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                m_context.registerReceiver(m_permission_receiver, filter);
            }
            m_permission_receiver_registered = true;
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                m_context, 0, new Intent(ACTION_USB_PERMISSION), flags);

        Log.d(TAG, "Requesting USB permission for device: " + device.getDeviceName());
        m_usb_manager.requestPermission(device, permissionIntent);
    }

    /**
     * Connect to the USB serial device at the specified baud rate.
     * Permission must be granted before calling this method.
     *
     * @param device The USB device to connect to
     * @param baudRate The baud rate for serial communication
     */
    public void connect(UsbDevice device, int baudRate) throws Exception {
        Log.d(TAG, "connect() start - device: " + device.getDeviceName() + " baudRate: " + baudRate);

        if (closed) {
            throw new Exception("usb_conn_mgr is closed");
        }

        m_target_usb_device = device;
        m_baud_rate = baudRate;

        // Check permission
        if (!hasPermission(device)) {
            String error = "USB permission not granted for device: " + device.getDeviceName();
            Log.d(TAG, error);
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            throw new Exception(error);
        }

        try {
            // Find the driver for this device
            UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
            if (driver == null) {
                throw new Exception("No USB serial driver found for device: " + device.getDeviceName());
            }

            Log.d(TAG, "Found driver: " + driver.getClass().getSimpleName() + " with " + driver.getPorts().size() + " ports");

            // Get the first port
            if (driver.getPorts().isEmpty()) {
                throw new Exception("No ports found on USB device");
            }
            m_usb_serial_port = driver.getPorts().get(0);

            // Open connection
            m_usb_connection = m_usb_manager.openDevice(device);
            if (m_usb_connection == null) {
                throw new Exception("Failed to open USB device connection");
            }

            // Open the port
            m_usb_serial_port.open(m_usb_connection);
            m_usb_serial_port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            Log.d(TAG, "USB serial port opened successfully");

            // Create input/output streams
            m_usb_is = new UsbSerialInputStream(m_usb_serial_port);
            m_usb_os = new UsbSerialOutputStream(m_usb_serial_port);

            m_cleanup_closables.add(m_usb_is);
            m_cleanup_closables.add(m_usb_os);

            // Notify connected
            if (m_callback != null) {
                m_callback.on_usb_connected(device.getDeviceName(), baudRate);
                m_callback.on_readline_stream_connected();
            }

            // Start connection state watcher
            startConnectionWatcher();

            Log.d(TAG, "connect() complete");

        } catch (Exception e) {
            Log.d(TAG, "connect() exception: " + Log.getStackTraceString(e));
            if (m_callback != null) {
                m_callback.on_usb_error("Connection failed: " + e.getMessage());
            }
            close();
            throw e;
        }
    }

    /**
     * Start a reader thread that reads lines and delivers them via callback.
     * Similar to rfcomm_conn_mgr's readline mode.
     */
    public void startReadlineThread() {
        if (m_usb_is == null || m_callback == null) {
            Log.d(TAG, "Cannot start readline thread: stream or callback is null");
            return;
        }

        inputstream_to_queue_reader_thread reader = new inputstream_to_queue_reader_thread(m_usb_is, m_callback);
        m_cleanup_closables.add(reader);
        reader.start();
        Log.d(TAG, "Readline thread started");
    }

    private void startConnectionWatcher() {
        m_conn_state_watcher = new Thread() {
            @Override
            public void run() {
                while (m_conn_state_watcher == this && !closed) {
                    try {
                        Thread.sleep(3000);

                        if (closed) {
                            break;
                        }

                        if (!isConnected()) {
                            throw new Exception("USB device disconnected");
                        }

                    } catch (InterruptedException e) {
                        Log.d(TAG, "Connection watcher interrupted");
                        break;
                    } catch (Exception e) {
                        Log.d(TAG, "Connection watcher detected disconnect: " + e.getMessage());
                        if (!closed && m_callback != null) {
                            m_callback.on_usb_disconnected(e.getMessage());
                            m_callback.on_readline_stream_closed();
                        }
                        break;
                    }
                }
            }
        };
        m_conn_state_watcher.start();
    }

    /**
     * Get the input stream for reading data from the USB device.
     */
    public InputStream getInputStream() {
        return m_usb_is;
    }

    /**
     * Get the output stream for writing data to the USB device.
     */
    public OutputStream getOutputStream() {
        return m_usb_os;
    }

    /**
     * Check if currently connected to a USB device.
     */
    public boolean isConnected() {
        return !closed && m_usb_serial_port != null && m_usb_connection != null;
    }

    /**
     * Check if the manager has been closed.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Get the currently connected USB device.
     */
    public UsbDevice getConnectedDevice() {
        return m_target_usb_device;
    }

    /**
     * Get the current baud rate.
     */
    public int getBaudRate() {
        return m_baud_rate;
    }

    /**
     * Write data to the USB serial port.
     */
    public void write(byte[] data) throws IOException {
        if (m_usb_os != null) {
            m_usb_os.write(data);
        }
    }

    /**
     * Close the USB connection and release all resources.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;
        Log.d(TAG, "close() called");

        // Stop connection watcher
        if (m_conn_state_watcher != null) {
            try {
                m_conn_state_watcher.interrupt();
            } catch (Exception e) {
                // ignore
            }
            m_conn_state_watcher = null;
        }

        // Close streams and port
        for (Closeable closeable : m_cleanup_closables) {
            try {
                closeable.close();
            } catch (Exception e) {
                // ignore
            }
        }
        m_cleanup_closables.clear();

        // Close the serial port
        if (m_usb_serial_port != null) {
            try {
                m_usb_serial_port.close();
            } catch (Exception e) {
                Log.d(TAG, "Error closing USB serial port: " + e.getMessage());
            }
            m_usb_serial_port = null;
        }

        // Close the USB connection
        if (m_usb_connection != null) {
            try {
                m_usb_connection.close();
            } catch (Exception e) {
                Log.d(TAG, "Error closing USB connection: " + e.getMessage());
            }
            m_usb_connection = null;
        }

        // Unregister permission receiver
        if (m_permission_receiver_registered) {
            try {
                m_context.unregisterReceiver(m_permission_receiver);
            } catch (Exception e) {
                // ignore
            }
            m_permission_receiver_registered = false;
        }

        m_usb_is = null;
        m_usb_os = null;

        Log.d(TAG, "close() complete");
    }

    /**
     * InputStream wrapper for UsbSerialPort.
     */
    private static class UsbSerialInputStream extends InputStream implements Closeable {
        private final UsbSerialPort port;
        private final byte[] buffer = new byte[4096];
        private volatile boolean closed = false;

        UsbSerialInputStream(UsbSerialPort port) {
            this.port = port;
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int result = read(b, 0, 1);
            return result > 0 ? (b[0] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                return -1;
            }
            try {
                int readLen = Math.min(len, buffer.length);
                int count = port.read(buffer, 1000); // 1 second timeout
                if (count > 0) {
                    int copyLen = Math.min(count, len);
                    System.arraycopy(buffer, 0, b, off, copyLen);
                    return copyLen;
                }
                return 0; // timeout, no data
            } catch (Exception e) {
                if (closed) {
                    return -1;
                }
                throw new IOException("USB read error: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * OutputStream wrapper for UsbSerialPort.
     */
    private static class UsbSerialOutputStream extends OutputStream implements Closeable {
        private final UsbSerialPort port;
        private volatile boolean closed = false;

        UsbSerialOutputStream(UsbSerialPort port) {
            this.port = port;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b});
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
            try {
                byte[] data;
                if (off == 0 && len == b.length) {
                    data = b;
                } else {
                    data = new byte[len];
                    System.arraycopy(b, off, data, 0, len);
                }
                port.write(data, 1000); // 1 second timeout
            } catch (Exception e) {
                throw new IOException("USB write error: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
