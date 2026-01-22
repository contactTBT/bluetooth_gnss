# Story 1.8: Connection Status & Disconnect Handling

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.8 |
| **Status** | Draft |
| **Priority** | High |
| **Dependencies** | Story 1.7 |

## User Story

**As a** user,
**I want** clear feedback about my USB connection status,
**so that** I know when I'm connected and can recover from disconnections.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | Connected screen shows "USB" connection type and detected baud rate | |
| AC2 | USB cable removal is detected within 1 second | |
| AC3 | Disconnection triggers user notification (toast or status update) | |
| AC4 | Resources are cleaned up gracefully on disconnect (no leaks) | |
| AC5 | Auto-reconnect setting applies to USB (attempts reconnection on disconnect) | |
| AC6 | Connection errors display user-friendly messages (permission denied, device not found, etc.) | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Bluetooth disconnect handling remains unchanged | |
| IV2 | Rapid connect/disconnect cycles don't cause crashes | |
| IV3 | Service remains stable after USB disconnection | |

## Technical Notes

### Files to Modify

1. **`lib/connect_screen_connected.dart`**
   - Display connection type (USB/BT)
   - Display baud rate for USB connections

2. **`lib/connect_screen_connecting.dart`**
   - Show baud rate detection progress for USB

3. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_mgr.java`**
   - Implement disconnect detection
   - Clean resource cleanup

4. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`**
   - Handle USB disconnect events
   - Implement auto-reconnect for USB

### Connected Screen Updates

```dart
class _ConnectScreenConnectedState extends State<ConnectScreenConnected> {
  String _connectionType = '';
  int? _baudRate;

  @override
  void initState() {
    super.initState();
    _loadConnectionStatus();
  }

  Future<void> _loadConnectionStatus() async {
    final status = await Channels.getConnectionStatus();
    setState(() {
      _connectionType = status['type'] ?? 'Unknown';
      _baudRate = status['baudRate'];
    });
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        // Connection type indicator
        Row(
          children: [
            Icon(
              _connectionType == 'USB_SERIAL' ? Icons.usb : Icons.bluetooth,
              color: _connectionType == 'USB_SERIAL' ? Colors.green : Colors.blue,
            ),
            SizedBox(width: 8),
            Text(_connectionType == 'USB_SERIAL' ? 'USB Connection' : 'Bluetooth Connection'),
            if (_baudRate != null) ...[
              SizedBox(width: 8),
              Text('(${_baudRate} baud)', style: TextStyle(color: Colors.grey)),
            ],
          ],
        ),
        // ... rest of connected screen
      ],
    );
  }
}
```

### Connecting Screen - Baud Rate Progress

```dart
class _ConnectScreenConnectingState extends State<ConnectScreenConnecting> {
  String _status = 'Connecting...';
  int? _currentBaudRate;

  @override
  void initState() {
    super.initState();
    _subscribeToConnectionEvents();
  }

  void _subscribeToConnectionEvents() {
    Channels.connectionEvents.listen((event) {
      if (event['type'] == 'baud_rate_detection') {
        setState(() {
          _status = 'Detecting baud rate...';
          _currentBaudRate = event['baudRate'];
        });
      } else if (event['type'] == 'baud_rate_detected') {
        setState(() {
          _status = 'Baud rate detected: ${event['baudRate']}';
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text(_status),
          if (_currentBaudRate != null)
            Text('Trying: $_currentBaudRate baud', style: TextStyle(color: Colors.grey)),
        ],
      ),
    );
  }
}
```

### USB Disconnect Detection

```java
// In usb_conn_mgr.java
private Thread m_connection_monitor_thread;

private void startConnectionMonitor() {
    m_connection_monitor_thread = new Thread(() -> {
        while (!closed) {
            try {
                Thread.sleep(500); // Check every 500ms

                if (m_usb_serial_port != null && !m_usb_serial_port.isOpen()) {
                    Log.d(TAG, "USB connection lost");
                    m_callback.on_usb_disconnected("USB device disconnected");
                    break;
                }

                // Also check if device is still attached
                if (!isDeviceStillAttached()) {
                    Log.d(TAG, "USB device detached");
                    m_callback.on_usb_disconnected("USB cable removed");
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    });
    m_connection_monitor_thread.start();
}

private boolean isDeviceStillAttached() {
    UsbManager usbManager = (UsbManager) m_context.getSystemService(Context.USB_SERVICE);
    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
    return deviceList.containsKey(m_target_usb_device.getDeviceName());
}
```

### Auto-Reconnect for USB

```java
// In bluetooth_gnss_service.java
private void handleUsbDisconnect(String reason) {
    Log.d(TAG, "USB disconnected: " + reason);

    // Notify UI
    notifyDisconnected(reason);

    // Attempt auto-reconnect if enabled
    if (m_auto_reconnect && m_last_usb_device != null) {
        Log.d(TAG, "Auto-reconnect enabled, will retry in 3 seconds");
        m_handler.postDelayed(() -> {
            if (isDeviceStillAttached(m_last_usb_device)) {
                Log.d(TAG, "Attempting USB auto-reconnect");
                startUsbConnection(m_last_usb_device);
            } else {
                Log.d(TAG, "Device no longer attached, skipping auto-reconnect");
            }
        }, 3000);
    }
}
```

### Error Messages

```java
// User-friendly error messages
public static String getUserFriendlyError(String error) {
    if (error.contains("permission")) {
        return "USB permission denied. Please grant permission when prompted.";
    } else if (error.contains("not found")) {
        return "USB device not found. Please reconnect the device.";
    } else if (error.contains("baud rate")) {
        return "Could not detect baud rate. The device may not be a GNSS receiver.";
    } else if (error.contains("disconnected") || error.contains("removed")) {
        return "USB device disconnected. Please check the cable connection.";
    }
    return "USB connection error: " + error;
}
```

### Resource Cleanup

```java
// In usb_conn_mgr.java
@Override
public void close() {
    closed = true;

    // Stop monitor thread
    if (m_connection_monitor_thread != null) {
        m_connection_monitor_thread.interrupt();
        m_connection_monitor_thread = null;
    }

    // Close serial port
    if (m_usb_serial_port != null) {
        try {
            m_usb_serial_port.close();
        } catch (IOException e) {
            Log.e(TAG, "Error closing USB port: " + e.getMessage());
        }
        m_usb_serial_port = null;
    }

    // Close USB connection
    if (m_usb_connection != null) {
        m_usb_connection.close();
        m_usb_connection = null;
    }

    Log.d(TAG, "USB connection manager closed");
}
```

### Testing Checklist

- [ ] Connected screen shows "USB Connection"
- [ ] Baud rate displayed for USB connection
- [ ] Unplug USB cable - disconnect detected within 1 second
- [ ] Toast/notification shown on disconnect
- [ ] Auto-reconnect works when cable reconnected
- [ ] Permission denied - user-friendly error message
- [ ] Rapid connect/disconnect (10 cycles) - no crashes
- [ ] Bluetooth disconnect still works (regression)

## References

- [connect_screen_connected.dart](../../lib/connect_screen_connected.dart)
- PRD: [docs/prd.md](../prd.md)
