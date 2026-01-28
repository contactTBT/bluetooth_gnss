/// Device connection type enum
enum DeviceConnectionType {
  bluetooth,
  usb,
}

/// Unified model for GNSS devices (both Bluetooth and USB)
class GnssDevice {
  final String id;
  final String name;
  final DeviceConnectionType type;
  final Map<String, dynamic> metadata;

  GnssDevice({
    required this.id,
    required this.name,
    required this.type,
    this.metadata = const {},
  });

  /// Create a GnssDevice from a Bluetooth device map entry
  /// bdaddr is the key, bdname is the value
  factory GnssDevice.fromBluetooth(String bdaddr, String bdname) {
    return GnssDevice(
      id: bdaddr,
      name: bdname.isNotEmpty ? bdname : 'Unknown BT Device',
      type: DeviceConnectionType.bluetooth,
      metadata: {'address': bdaddr, 'name': bdname},
    );
  }

  /// Create a GnssDevice from a USB device info map
  factory GnssDevice.fromUsb(Map<String, dynamic> usbDevice) {
    final deviceId = usbDevice['deviceId'] as int;
    String name = usbDevice['productName'] as String? ?? '';
    if (name.isEmpty) {
      name = usbDevice['manufacturerName'] as String? ?? '';
    }
    if (name.isEmpty) {
      name = 'USB Serial Device';
    }

    return GnssDevice(
      id: 'usb_$deviceId',
      name: name,
      type: DeviceConnectionType.usb,
      metadata: usbDevice,
    );
  }

  /// Get the USB device ID (only valid for USB devices)
  int? get usbDeviceId {
    if (type == DeviceConnectionType.usb) {
      return metadata['deviceId'] as int?;
    }
    return null;
  }

  /// Get the Bluetooth address (only valid for Bluetooth devices)
  String? get bluetoothAddress {
    if (type == DeviceConnectionType.bluetooth) {
      return metadata['address'] as String?;
    }
    return null;
  }

  /// Display name with connection type indicator
  String get displayName {
    final icon = type == DeviceConnectionType.bluetooth ? '[BT]' : '[USB]';
    return '$icon $name';
  }

  /// Subtitle showing device-specific info
  String get subtitle {
    if (type == DeviceConnectionType.bluetooth) {
      return metadata['address'] as String? ?? '';
    } else {
      final vendorId = metadata['vendorId'];
      final productId = metadata['productId'];
      if (vendorId != null && productId != null) {
        return 'VID:${_hexString(vendorId)} PID:${_hexString(productId)}';
      }
      return 'USB Serial';
    }
  }

  String _hexString(dynamic value) {
    if (value is int) {
      return value.toRadixString(16).toUpperCase().padLeft(4, '0');
    }
    return value.toString();
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is GnssDevice && other.id == id && other.type == type;
  }

  @override
  int get hashCode => id.hashCode ^ type.hashCode;

  @override
  String toString() => 'GnssDevice(id: $id, name: $name, type: $type)';
}
