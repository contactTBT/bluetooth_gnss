# Story 1.5: Service Integration

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.5 |
| **Status** | Draft |
| **Priority** | High |
| **Dependencies** | Story 1.3, Story 1.4 |

## User Story

**As a** user,
**I want** USB GNSS data to provide mock location updates,
**so that** my location apps use the external USB receiver's position.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `bluetooth_gnss_service.java` accepts USB connection type parameter | |
| AC2 | Service manages `usb_conn_mgr` lifecycle alongside existing `rfcomm_conn_mgr` | |
| AC3 | USB `InputStream` data routes to existing `NativeParser` for NMEA/UBX parsing | |
| AC4 | Parsed position data updates Android mock location provider | |
| AC5 | Service foreground notification indicates USB connection (vs Bluetooth) | |
| AC6 | Position offset settings apply to USB-sourced positions | |
| AC7 | Logging (if enabled) captures USB-received data | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Bluetooth connections continue to work when no USB device is connected | |
| IV2 | Mock location updates arrive at expected rate (matching receiver output) | |
| IV3 | Service lifecycle (start/stop/restart) handles USB connections correctly | |

## Technical Notes

### Files to Modify

1. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`**
   - Add `usb_conn_mgr` member variable
   - Add USB connection type handling
   - Modify notification to show connection type
   - Route USB streams to parser

2. **`lib/channels.dart`**
   - Add `connectUsb()` method
   - Add connection type to status responses

### Connection Type Enum

```java
public enum ConnectionType {
    NONE,
    BLUETOOTH_RFCOMM,
    BLUETOOTH_BLE,
    USB_SERIAL
}

private ConnectionType m_connection_type = ConnectionType.NONE;
```

### Service Modifications

```java
// New member variable
usb_conn_mgr g_usb_mgr = null;

// USB connection method
public void startUsbConnection(UsbDevice device) {
    if (g_usb_mgr != null) {
        g_usb_mgr.close();
    }

    g_usb_mgr = new usb_conn_mgr(this, new usb_conn_callbacks() {
        @Override
        public void on_usb_connected(String deviceName, int baudRate) {
            m_connection_type = ConnectionType.USB_SERIAL;
            updateNotification("Connected via USB: " + deviceName);
            startDataReaderThread(g_usb_mgr.getInputStream());
        }

        @Override
        public void on_usb_disconnected(String reason) {
            m_connection_type = ConnectionType.NONE;
            handleDisconnect(reason);
        }

        @Override
        public void on_usb_error(String error) {
            notifyError(error);
        }
    });

    g_usb_mgr.connectWithAutoDetect(device);
}
```

### Stream Reader Integration

The existing `inputstream_to_queue_reader_thread` pattern can be reused:

```java
private void startDataReaderThread(InputStream inputStream) {
    // Same logic as Bluetooth - read from stream, parse NMEA/UBX
    // The NativeParser doesn't care about the source of the data

    m_data_reader_thread = new Thread(() -> {
        byte[] buffer = new byte[RFCOMM_READ_BUFF_SIZE];
        while (!closed) {
            try {
                int bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    processIncomingData(buffer, bytesRead);

                    // Log if enabled
                    if (m_log_bt_rx_fos != null) {
                        m_log_bt_rx_fos.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                if (!closed) {
                    handleDisconnect("Read error: " + e.getMessage());
                }
                break;
            }
        }
    });
    m_data_reader_thread.start();
}
```

### Notification Update

```java
private void updateNotification(String status) {
    String connectionIcon = (m_connection_type == ConnectionType.USB_SERIAL)
        ? "USB" : "BT";

    Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("GNSS Connected (" + connectionIcon + ")")
        .setContentText(status)
        .setSmallIcon(R.drawable.ic_notification)
        .build();

    NotificationManager manager = getSystemService(NotificationManager.class);
    manager.notify(NOTIFICATION_ID, notification);
}
```

### Platform Channel Method

```dart
// In channels.dart
static Future<bool> connectUsb(int deviceId) async {
  final result = await _channel.invokeMethod('connectUsb', {
    'deviceId': deviceId,
  });
  return result as bool;
}

static Future<Map<String, dynamic>> getConnectionStatus() async {
  final result = await _channel.invokeMethod('getConnectionStatus');
  return Map<String, dynamic>.from(result);
  // Returns: {connected: true, type: "USB_SERIAL", device: "...", baudRate: 115200}
}
```

### Testing Checklist

- [ ] Connect USB device - mock location updates in Maps app
- [ ] Position offsets applied correctly to USB positions
- [ ] Logging captures USB data when enabled
- [ ] Notification shows "USB" for USB connections
- [ ] Disconnect USB - service handles gracefully
- [ ] Bluetooth still works when USB not connected

## References

- [bluetooth_gnss_service.java](../../android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java)
- PRD: [docs/prd.md](../prd.md)
