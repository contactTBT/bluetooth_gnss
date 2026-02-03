package com.dnablast.libbluetooth_gnss_service;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * USB Serial Connection Manager.
 * Manages USB serial connections following the same patterns as rfcomm_conn_mgr.
 * Provides InputStream/OutputStream for GNSS data transfer.
 */
public class usb_conn_mgr implements Closeable {

    private static final String TAG = "btgnss_usbmgr";
    private static final String ACTION_USB_PERMISSION = "com.dnablast.bluetooth_gnss.USB_PERMISSION";

    // Baud rate auto-detection constants
    public static final int[] BAUD_RATES = {115200, 57600, 38400, 19200, 9600, 4800};
    public static final int DETECTION_TIMEOUT_PER_RATE_MS = 800;
    public static final int MIN_VALID_SENTENCES = 2;

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

    // Output queue for writing data to USB device (e.g., NTRIP corrections)
    private ConcurrentLinkedQueue<byte[]> m_outgoing_buffers;
    private queue_to_outputstream_writer_thread m_outgoing_thread;

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
     * Connect to the USB serial device with automatic baud rate detection.
     * Cycles through common baud rates and validates NMEA data to find the correct rate.
     * Permission must be granted before calling this method.
     *
     * @param device The USB device to connect to
     */
    public void connectWithAutoDetect(UsbDevice device) throws Exception {
        Log.d(TAG, "connectWithAutoDetect() start - device: " + device.getDeviceName());

        if (closed) {
            throw new Exception("usb_conn_mgr is closed");
        }

        m_target_usb_device = device;

        // Check permission
        if (!hasPermission(device)) {
            String error = "USB permission not granted for device: " + device.getDeviceName();
            Log.d(TAG, error);
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            throw new Exception(error);
        }

        // Find the driver for this device
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            String error = "No USB serial driver found for device: " + device.getDeviceName();
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            throw new Exception(error);
        }

        Log.d(TAG, "Found driver: " + driver.getClass().getSimpleName() + " with " + driver.getPorts().size() + " ports");

        // Get the first port
        if (driver.getPorts().isEmpty()) {
            String error = "No ports found on USB device";
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            throw new Exception(error);
        }
        m_usb_serial_port = driver.getPorts().get(0);

        // Open connection
        m_usb_connection = m_usb_manager.openDevice(device);
        if (m_usb_connection == null) {
            String error = "Failed to open USB device connection";
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            throw new Exception(error);
        }

        try {
            // Open the port
            m_usb_serial_port.open(m_usb_connection);

            // Try each baud rate
            for (int i = 0; i < BAUD_RATES.length; i++) {
                if (closed) {
                    Log.d(TAG, "Auto-detection cancelled (closed)");
                    return;
                }

                int baudRate = BAUD_RATES[i];
                int ratesRemaining = BAUD_RATES.length - i - 1;

                Log.d(TAG, "Trying baud rate: " + baudRate);

                if (m_callback != null) {
                    m_callback.on_baud_rate_detection_progress(baudRate, ratesRemaining);
                }

                try {
                    m_usb_serial_port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                    // Try to read and validate NMEA
                    if (validateNmeaData(DETECTION_TIMEOUT_PER_RATE_MS)) {
                        Log.d(TAG, "Detected baud rate: " + baudRate);
                        m_baud_rate = baudRate;

                        // Create input/output streams
                        m_usb_is = new UsbSerialInputStream(m_usb_serial_port);
                        m_usb_os = new UsbSerialOutputStream(m_usb_serial_port);

                        m_cleanup_closables.add(m_usb_is);
                        m_cleanup_closables.add(m_usb_os);

                        // Initialize output queue and writer thread for NTRIP corrections
                        m_outgoing_buffers = new ConcurrentLinkedQueue<byte[]>();
                        m_outgoing_thread = new queue_to_outputstream_writer_thread(m_outgoing_buffers, m_usb_os);
                        m_cleanup_closables.add(m_outgoing_thread);
                        m_outgoing_thread.start();
                        Log.d(TAG, "Output writer thread started for USB");

                        // Notify detected and connected
                        if (m_callback != null) {
                            m_callback.on_baud_rate_detected(baudRate);
                            m_callback.on_usb_connected(device.getDeviceName(), baudRate);
                            m_callback.on_readline_stream_connected();
                        }

                        // Start connection state watcher
                        startConnectionWatcher();

                        Log.d(TAG, "connectWithAutoDetect() complete at " + baudRate + " baud");
                        return;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error at baud " + baudRate + ": " + e.getMessage());
                }
            }

            // All rates failed
            String error = "Could not detect baud rate. Tried: " + Arrays.toString(BAUD_RATES);
            Log.d(TAG, error);
            if (m_callback != null) {
                m_callback.on_usb_error(error);
            }
            close();
            throw new Exception(error);

        } catch (Exception e) {
            Log.d(TAG, "connectWithAutoDetect() exception: " + Log.getStackTraceString(e));
            close();
            throw e;
        }
    }

    /**
     * Validate that NMEA data is being received at the current baud rate.
     *
     * @param timeoutMs Maximum time to wait for valid data
     * @return true if valid NMEA sentences were received
     */
    private boolean validateNmeaData(int timeoutMs) {
        byte[] buffer = new byte[256];
        StringBuilder lineBuilder = new StringBuilder();
        int validSentences = 0;
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (closed) {
                return false;
            }

            try {
                int bytesRead = m_usb_serial_port.read(buffer, 100);
                if (bytesRead > 0) {
                    String chunk = new String(buffer, 0, bytesRead, StandardCharsets.US_ASCII);
                    lineBuilder.append(chunk);

                    // Check for complete NMEA sentences
                    String data = lineBuilder.toString();
                    int newlineIdx;
                    while ((newlineIdx = data.indexOf('\n')) != -1) {
                        String line = data.substring(0, newlineIdx).trim();
                        data = data.substring(newlineIdx + 1);

                        if (isValidNmeaSentence(line)) {
                            validSentences++;
                            Log.d(TAG, "Valid NMEA sentence #" + validSentences + ": " + line.substring(0, Math.min(20, line.length())) + "...");
                            if (validSentences >= MIN_VALID_SENTENCES) {
                                return true;
                            }
                        }
                    }
                    lineBuilder = new StringBuilder(data);
                }
            } catch (Exception e) {
                Log.d(TAG, "Error reading during validation: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * Validate a single NMEA sentence.
     * Checks for proper format: starts with $, contains *, checksum matches.
     *
     * @param line The line to validate
     * @return true if the line is a valid NMEA sentence
     */
    private boolean isValidNmeaSentence(String line) {
        // Basic NMEA validation: starts with $, contains *, checksum matches
        if (line == null || !line.startsWith("$") || !line.contains("*")) {
            return false;
        }

        int asteriskIdx = line.lastIndexOf('*');
        if (asteriskIdx < 1 || asteriskIdx + 3 > line.length()) {
            return false;
        }

        // Verify checksum
        String payload = line.substring(1, asteriskIdx);
        String checksumStr = line.substring(asteriskIdx + 1);

        // Handle case where checksum might have trailing characters
        if (checksumStr.length() > 2) {
            checksumStr = checksumStr.substring(0, 2);
        }

        int calculatedChecksum = 0;
        for (char c : payload.toCharArray()) {
            calculatedChecksum ^= c;
        }

        try {
            int providedChecksum = Integer.parseInt(checksumStr, 16);
            return calculatedChecksum == providedChecksum;
        } catch (NumberFormatException e) {
            return false;
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

    private static final int CONNECTION_WATCHER_INTERVAL_MS = 500; // Check every 500ms for fast disconnect detection

    private void startConnectionWatcher() {
        m_conn_state_watcher = new Thread() {
            @Override
            public void run() {
                Log.d(TAG, "Connection watcher thread started");
                while (m_conn_state_watcher == this && !closed) {
                    try {
                        Thread.sleep(CONNECTION_WATCHER_INTERVAL_MS);

                        if (closed) {
                            break;
                        }

                        // Check if device is still attached
                        if (!isDeviceStillAttached()) {
                            Log.d(TAG, "Connection watcher: USB device detached");
                            if (!closed && m_callback != null) {
                                m_callback.on_usb_disconnected("USB cable removed");
                                m_callback.on_readline_stream_closed();
                            }
                            break;
                        }

                    } catch (InterruptedException e) {
                        Log.d(TAG, "Connection watcher interrupted");
                        break;
                    } catch (Exception e) {
                        Log.d(TAG, "Connection watcher exception: " + e.getMessage());
                        if (!closed && m_callback != null) {
                            m_callback.on_usb_disconnected(e.getMessage());
                            m_callback.on_readline_stream_closed();
                        }
                        break;
                    }
                }
                Log.d(TAG, "Connection watcher thread ended");
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
     * Also verifies the device is still physically attached.
     */
    public boolean isConnected() {
        if (closed || m_usb_serial_port == null || m_usb_connection == null) {
            return false;
        }
        // Also check if device is still physically attached
        return isDeviceStillAttached();
    }

    /**
     * Check if the USB device is still physically attached to the system.
     */
    private boolean isDeviceStillAttached() {
        if (m_target_usb_device == null || m_usb_manager == null) {
            return false;
        }
        try {
            java.util.HashMap<String, UsbDevice> deviceList = m_usb_manager.getDeviceList();
            return deviceList.containsKey(m_target_usb_device.getDeviceName());
        } catch (Exception e) {
            Log.d(TAG, "isDeviceStillAttached exception: " + e.getMessage());
            return false;
        }
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
     * Add data to the outgoing buffer queue for asynchronous writing.
     * Used for NTRIP corrections and UBX commands.
     */
    public void add_send_buffer(byte[] buffer) {
        if (m_outgoing_buffers != null) {
            m_outgoing_buffers.add(buffer);
            // Log queue size periodically to confirm data flow
            int size = m_outgoing_buffers.size();
            if (size > 0 && size % 10 == 0) {
                Log.d(TAG, "USB outgoing queue size: " + size + ", adding " + buffer.length + " bytes");
            }
        } else {
            Log.w(TAG, "add_send_buffer called but m_outgoing_buffers is null!");
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
