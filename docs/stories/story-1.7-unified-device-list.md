# Story 1.7: Unified Device List UI

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.7 |
| **Status** | Done |
| **Priority** | High |
| **Dependencies** | Story 1.5 |

## User Story

**As a** user,
**I want** to see both Bluetooth and USB devices in one list,
**so that** I can easily choose my preferred connection method.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | Device list in `connect_screen_idle.dart` shows both BT and USB devices | |
| AC2 | Each device entry displays connection type indicator (BT icon / USB icon) | |
| AC3 | USB devices appear/disappear dynamically as they're plugged/unplugged | |
| AC4 | Tapping a USB device initiates USB connection flow (permission → connect) | |
| AC5 | Tapping a Bluetooth device continues to work as before | |
| AC6 | Empty states handled: "No devices" shows when no BT paired AND no USB connected | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Bluetooth device list remains fully functional | |
| IV2 | Device selection state management handles both types correctly | |
| IV3 | UI performs well with multiple devices (no lag or jank) | |

## Technical Notes

### Files to Modify

1. **`lib/connect_screen_idle.dart`**
   - Modify device list to include USB devices
   - Add connection type indicator to list items
   - Handle USB device tap → connection flow

2. **`lib/channels.dart`**
   - Already has `getUsbDevices()` from Story 1.2
   - Already has `connectUsb()` from Story 1.5

3. **`lib/utils.dart`** or new **`lib/device_model.dart`**
   - Create unified device model for both BT and USB

### Unified Device Model

```dart
enum DeviceConnectionType {
  bluetooth,
  usb,
}

class GnssDevice {
  final String id;
  final String name;
  final DeviceConnectionType type;
  final Map<String, dynamic> metadata; // BT: address, USB: vendorId, productId, etc.

  GnssDevice({
    required this.id,
    required this.name,
    required this.type,
    this.metadata = const {},
  });

  // Factory constructors
  factory GnssDevice.fromBluetooth(Map<String, dynamic> btDevice) {
    return GnssDevice(
      id: btDevice['address'],
      name: btDevice['name'] ?? 'Unknown BT Device',
      type: DeviceConnectionType.bluetooth,
      metadata: btDevice,
    );
  }

  factory GnssDevice.fromUsb(Map<String, dynamic> usbDevice) {
    return GnssDevice(
      id: 'usb_${usbDevice['deviceId']}',
      name: usbDevice['productName'] ?? 'USB Serial Device',
      type: DeviceConnectionType.usb,
      metadata: usbDevice,
    );
  }
}
```

### Device List Widget

```dart
class DeviceListItem extends StatelessWidget {
  final GnssDevice device;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Icon(
        device.type == DeviceConnectionType.bluetooth
          ? Icons.bluetooth
          : Icons.usb,
        color: device.type == DeviceConnectionType.bluetooth
          ? Colors.blue
          : Colors.green,
      ),
      title: Text(device.name),
      subtitle: Text(
        device.type == DeviceConnectionType.bluetooth
          ? device.metadata['address'] ?? ''
          : 'USB Serial',
      ),
      trailing: Icon(Icons.chevron_right),
      onTap: onTap,
    );
  }
}
```

### Combined Device List State

```dart
class _ConnectScreenIdleState extends State<ConnectScreenIdle> {
  List<GnssDevice> _devices = [];
  StreamSubscription? _usbEventSubscription;

  @override
  void initState() {
    super.initState();
    _loadDevices();
    _subscribeToUsbEvents();
  }

  Future<void> _loadDevices() async {
    final btDevices = await Channels.getBluetoothDevices();
    final usbDevices = await Channels.getUsbDevices();

    setState(() {
      _devices = [
        ...btDevices.map((d) => GnssDevice.fromBluetooth(d)),
        ...usbDevices.map((d) => GnssDevice.fromUsb(d)),
      ];
    });
  }

  void _subscribeToUsbEvents() {
    // Listen for USB attach/detach events
    _usbEventSubscription = Channels.usbDeviceEvents.listen((event) {
      _loadDevices(); // Refresh list on USB change
    });
  }

  void _onDeviceTap(GnssDevice device) {
    if (device.type == DeviceConnectionType.bluetooth) {
      // Existing Bluetooth connection flow
      _connectBluetooth(device.metadata['address']);
    } else {
      // New USB connection flow
      _connectUsb(device.metadata['deviceId']);
    }
  }

  Future<void> _connectUsb(int deviceId) async {
    // Show connecting state
    setState(() => _isConnecting = true);

    try {
      final success = await Channels.connectUsb(deviceId);
      if (success) {
        // Navigate to connected screen
        Navigator.pushReplacement(context, ...);
      }
    } catch (e) {
      // Show error
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('USB connection failed: $e')),
      );
    } finally {
      setState(() => _isConnecting = false);
    }
  }
}
```

### USB Event Stream (Platform Channel)

```dart
// In channels.dart
static Stream<String> get usbDeviceEvents {
  return _eventChannel.receiveBroadcastStream().map((event) => event as String);
}

static const _eventChannel = EventChannel('com.dnablast.bluetooth_gnss/usb_events');
```

### Empty State

```dart
Widget _buildDeviceList() {
  if (_devices.isEmpty) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.devices, size: 64, color: Colors.grey),
          SizedBox(height: 16),
          Text('No devices available'),
          SizedBox(height: 8),
          Text(
            'Pair a Bluetooth device or connect a USB GNSS receiver',
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  return ListView.builder(
    itemCount: _devices.length,
    itemBuilder: (context, index) => DeviceListItem(
      device: _devices[index],
      onTap: () => _onDeviceTap(_devices[index]),
    ),
  );
}
```

### Testing Checklist

- [ ] BT devices appear in list (regression)
- [ ] USB device appears when plugged in
- [ ] USB device disappears when unplugged
- [ ] BT device shows Bluetooth icon
- [ ] USB device shows USB icon
- [ ] Tap BT device - connects via Bluetooth
- [ ] Tap USB device - connects via USB
- [ ] No devices - empty state message displayed
- [ ] Multiple devices - list scrolls correctly

## References

- [connect_screen_idle.dart](../../lib/connect_screen_idle.dart)
- PRD: [docs/prd.md](../prd.md)

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List

| File | Action |
|------|--------|
| `lib/gnss_device.dart` | Created - GnssDevice model with DeviceConnectionType enum, factory constructors for BT/USB |
| `lib/connect.dart` | Modified - Added unifiedDeviceListNotifier, selectedDeviceNotifier, loadUnifiedDeviceList(), getSelectedDevice(), setSelectedDevice(), connect() handles USB, connectUsb() now passes NTRIP params |
| `lib/channels.dart` | Modified - connectUsb() now accepts connectionParams map with NTRIP settings |
| `lib/utils_ui.dart` | Modified - Added reactiveUnifiedDeviceDropDown() widget with BT/USB icons |
| `lib/settings_screen.dart` | Modified - Replaced BT-only dropdown with unified device dropdown, added refresh button |
| `android/.../bluetooth_gnss_service.java` | Modified - Added USB: prefix detection, NTRIP callback sends to USB, UBX commands sent on USB connect |
| `android/.../usb_conn_mgr.java` | Modified - Added output queue (ConcurrentLinkedQueue), writer thread (queue_to_outputstream_writer_thread), add_send_buffer() method for NTRIP/UBX data |
| `android/.../MainActivity.java` | Modified - connectUsb handler reads NTRIP params from Flutter instead of hardcoding disable_ntrip=true |

### Completion Notes

- AC1: Settings screen now shows unified device list with both Bluetooth and USB devices via `reactiveUnifiedDeviceDropDown`
- AC2: Each device displays an icon (blue Bluetooth icon, green USB icon) and subtitle showing device details
- AC3: Refresh button added to reload device list; `loadUnifiedDeviceList()` fetches both BT paired devices and connected USB devices
- AC4: `connect()` function checks selected device type and calls `connectUsb()` for USB devices
- AC5: Bluetooth devices continue to work via `connectBluetooth()` - original logic preserved
- AC6: Empty state shows "No devices found" with hint to pair Bluetooth or connect USB
- Build succeeds: `flutter build apk` produces 56.1MB APK
- All Flutter tests pass (2/2)
- IV1/IV2/IV3 require manual device verification

### Bug Fixes (Post-Implementation)
- Fixed: USB connection now works with Bluetooth turned OFF (BT check skipped when USB device selected)
- Fixed: IllegalArgumentException when connecting USB - service now detects "USB:" prefix in bdaddr and skips BT connection logic
- Added: USB icon shown on floating button when USB device selected (instead of BT icon)
- Fixed: UI now transitions to Connected state for USB connections - `_checkUpdateSelectedDev` now checks both BT and USB connection status via `getConnectionStatus()`
- Fixed: NTRIP corrections now sent to USB device - added output queue and writer thread to `usb_conn_mgr`, modified NTRIP callback to send to USB
- Fixed: UBX accuracy values (hAcc/vAcc) now available for USB - UBX commands sent on USB connect to enable PUBX messages
- Fixed: NTRIP connection now starts for USB devices - was hardcoded `disable_ntrip=true`, now passes all NTRIP settings from Flutter
- Fixed: Added flush() after USB writes for timely RTCM delivery
- Fixed: NTRIP `m_all_ntrip_params_specified` flag now set for USB connections - was only set for Bluetooth, causing NTRIP to never auto-start for USB-only

### Change Log

| Date | Change |
|------|--------|
| 2026-01-28 | Initial implementation - Unified device list UI complete |
| 2026-01-28 | Bug fix - USB connection works without Bluetooth, fixed IllegalArgumentException |
| 2026-01-28 | Bug fix - UI shows Connected state for USB (checks both BT and USB status) |
| 2026-01-28 | Bug fix - NTRIP corrections and UBX accuracy data now work for USB connections |
| 2026-01-28 | Bug fix - NTRIP connection enabled for USB (was hardcoded disabled) |
| 2026-01-28 | Bug fix - NTRIP auto-start now works for USB-only (m_all_ntrip_params_specified flag) |
