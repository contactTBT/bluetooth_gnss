# Story 1.3: USB Connection Manager Core

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.3 |
| **Status** | Done |
| **Priority** | High |
| **Dependencies** | Story 1.2 |

## User Story

**As a** developer,
**I want** a `usb_conn_mgr` class that establishes USB serial connections,
**so that** I can obtain `InputStream`/`OutputStream` for GNSS data transfer.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `usb_conn_mgr.java` class created following `rfcomm_conn_mgr` patterns | |
| AC2 | `usb_conn_callbacks.java` interface defined for connection state callbacks | |
| AC3 | USB permission request flow implemented (prompts user, handles grant/deny) | |
| AC4 | Connection establishes to USB serial device at specified baud rate | |
| AC5 | `InputStream` and `OutputStream` are accessible after successful connection | |
| AC6 | `close()` method properly releases USB resources | |
| AC7 | Connection errors are reported via callback with descriptive messages | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | `rfcomm_conn_mgr` remains unchanged and functional | |
| IV2 | USB connection can be established while no Bluetooth connection is active | |
| IV3 | Resource cleanup doesn't affect other app components | |

## Technical Notes

### Files to Create

1. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_mgr.java`**
   - Main USB connection manager class
   - Implements `Closeable`

2. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_callbacks.java`**
   - Callback interface for connection events

### Class Structure

```java
public class usb_conn_mgr implements Closeable {

    UsbSerialPort m_usb_serial_port;
    InputStream m_usb_is;
    OutputStream m_usb_os;
    UsbDevice m_target_usb_device;

    Context m_context;
    usb_conn_callbacks m_callback;
    volatile boolean closed = false;

    static final String TAG = "btgnss_usbmgr";

    // Constructor
    public usb_conn_mgr(Context context, usb_conn_callbacks callback) { ... }

    // Request USB permission
    public void requestPermission(UsbDevice device) { ... }

    // Connect to device at specified baud rate
    public void connect(UsbDevice device, int baudRate) { ... }

    // Get streams for data transfer
    public InputStream getInputStream() { return m_usb_is; }
    public OutputStream getOutputStream() { return m_usb_os; }

    // Check connection state
    public boolean isConnected() { ... }

    // Close and cleanup
    @Override
    public void close() { ... }
}
```

### Callback Interface

```java
public interface usb_conn_callbacks {
    void on_usb_permission_result(boolean granted);
    void on_usb_connected(String deviceName, int baudRate);
    void on_usb_disconnected(String reason);
    void on_usb_error(String error);
}
```

### USB Permission Flow

```java
// Request permission
PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0,
    new Intent(ACTION_USB_PERMISSION), FLAG_IMMUTABLE);
usbManager.requestPermission(device, permissionIntent);

// Handle result via BroadcastReceiver
if (ACTION_USB_PERMISSION.equals(action)) {
    synchronized (this) {
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
        m_callback.on_usb_permission_result(granted);
    }
}
```

### Connection Code

```java
// Using usb-serial-for-android
UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
UsbSerialPort port = driver.getPorts().get(0);

UsbDeviceConnection connection = usbManager.openDevice(device);
port.open(connection);
port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

// Wrap in streams
m_usb_is = new UsbSerialInputStream(port);
m_usb_os = new UsbSerialOutputStream(port);
```

### Testing Checklist

- [ ] Permission dialog appears when selecting USB device
- [ ] Permission grant → connection established
- [ ] Permission deny → error callback with message
- [ ] Streams readable/writable after connection
- [ ] `close()` releases all resources
- [ ] Reconnection after close works

## References

- [rfcomm_conn_mgr.java](../../android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/rfcomm_conn_mgr.java) - Pattern reference
- [usb-serial-for-android usage](https://github.com/mik3y/usb-serial-for-android#usage)
- PRD: [docs/prd.md](../prd.md)

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List

| File | Action |
|------|--------|
| `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_callbacks.java` | Created - Callback interface for USB connection events |
| `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_mgr.java` | Created - USB serial connection manager |

### Completion Notes

- AC1: `usb_conn_mgr.java` created following rfcomm_conn_mgr patterns (Closeable, callbacks, connection watcher)
- AC2: `usb_conn_callbacks.java` interface with on_usb_permission_result, on_usb_connected, on_usb_disconnected, on_usb_error
- AC3: USB permission flow with PendingIntent and BroadcastReceiver (FLAG_MUTABLE for API 31+, RECEIVER_NOT_EXPORTED for API 33+)
- AC4: connect() method opens UsbSerialPort with configurable baud rate (default 115200)
- AC5: UsbSerialInputStream/UsbSerialOutputStream inner classes wrap UsbSerialPort for standard stream access
- AC6: close() method releases all resources (port, connection, receiver, watcher thread)
- AC7: Errors reported via on_usb_error callback with descriptive messages
- Build succeeds: `flutter build apk` produces 56.1MB APK (deprecation warning for getParcelableExtra is expected on newer Android)
- All existing Flutter tests pass (2/2)
- rfcomm_conn_mgr.java unchanged (IV1)
- IV2/IV3 require manual device verification

### Change Log

| Date | Change |
|------|--------|
| 2026-01-28 | Initial implementation - USB connection manager core complete |
