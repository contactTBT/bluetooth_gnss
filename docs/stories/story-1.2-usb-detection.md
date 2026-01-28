# Story 1.2: USB Device Detection & Enumeration

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.2 |
| **Status** | Done |
| **Priority** | High |
| **Dependencies** | Story 1.1 |

## User Story

**As a** user,
**I want** the app to detect when I plug in a USB GNSS receiver,
**so that** I can see it as an available device to connect to.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | App detects USB serial devices when connected via USB-OTG | |
| AC2 | USB device information (name, vendor ID, product ID) is retrievable | |
| AC3 | BroadcastReceiver handles `USB_DEVICE_ATTACHED` and `USB_DEVICE_DETACHED` events | |
| AC4 | Device list updates dynamically when USB devices are plugged/unplugged | |
| AC5 | Platform channel method `getUsbDevices()` returns list of connected USB serial devices | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Bluetooth device enumeration continues to work independently | |
| IV2 | App handles scenario where no USB devices are connected (empty list, no errors) | |
| IV3 | Hot-plug events don't cause crashes or ANRs | |

## Technical Notes

### Files to Create/Modify

1. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_device_manager.java`** (NEW)
   - USB device enumeration
   - BroadcastReceiver for attach/detach

2. **`android/app/src/main/java/com/clearevo/bluetooth_gnss/MainActivity.java`** (MODIFY)
   - Register/unregister USB broadcast receiver
   - Add platform channel handler for `getUsbDevices()`

3. **`lib/channels.dart`** (MODIFY)
   - Add `getUsbDevices()` method

### Implementation Guidance

```java
// USB device enumeration
UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

// Filter for serial devices using usb-serial-for-android
List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
```

```dart
// Platform channel in channels.dart
static Future<List<Map<String, dynamic>>> getUsbDevices() async {
  final List<dynamic> result = await _channel.invokeMethod('getUsbDevices');
  return result.cast<Map<String, dynamic>>();
}
```

### Device Info Structure

```json
{
  "deviceId": 1234,
  "deviceName": "/dev/bus/usb/001/002",
  "vendorId": 5446,
  "productId": 425,
  "manufacturerName": "u-blox AG",
  "productName": "u-blox GNSS receiver",
  "serialNumber": "ABC123",
  "driverType": "CdcAcmSerialDriver"
}
```

### Testing Checklist

- [ ] Plug in ZED-F9P via USB-OTG - device appears in list
- [ ] Unplug device - device removed from list
- [ ] Plug/unplug rapidly - no crashes
- [ ] No USB device connected - empty list, no errors
- [ ] Bluetooth devices still listed correctly

## References

- [usb-serial-for-android device detection](https://github.com/mik3y/usb-serial-for-android#device-detection)
- PRD: [docs/prd.md](../prd.md)

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List

| File | Action |
|------|--------|
| `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/UsbDeviceManager.java` | Created - USB device enumeration and BroadcastReceiver |
| `android/app/src/main/java/com/clearevo/bluetooth_gnss/MainActivity.java` | Modified - Added USB platform channel and receiver registration |
| `lib/channels.dart` | Modified - Added getUsbDevices() and isUsbHostSupported() methods |

### Completion Notes

- AC1: USB serial device detection implemented using usb-serial-for-android UsbSerialProber
- AC2: Device info includes deviceId, deviceName, vendorId, productId, manufacturerName, productName, serialNumber, driverType, portCount
- AC3: BroadcastReceiver registered for USB_DEVICE_ATTACHED and USB_DEVICE_DETACHED with API 33+ RECEIVER_NOT_EXPORTED flag
- AC4: USB events sent to Flutter via settings event channel with callback_src "usb_device_event"
- AC5: Platform channel methods `getUsbDevices()` and `isUsbHostSupported()` implemented
- Build succeeds: `flutter build apk` produces 56.1MB APK
- All existing Flutter tests pass (2/2)
- IV1/IV2/IV3 require manual device verification

### Change Log

| Date | Change |
|------|--------|
| 2026-01-28 | Initial implementation - USB device detection and enumeration complete |
