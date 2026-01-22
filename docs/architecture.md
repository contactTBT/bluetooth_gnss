# USB GNSS Brownfield Enhancement Architecture

## Introduction

This document outlines the architectural approach for enhancing Bluetooth GNSS with USB serial connectivity. Its primary goal is to serve as the guiding architectural blueprint for AI-driven development of new features while ensuring seamless integration with the existing system.

**Relationship to Existing Architecture:**
This document supplements existing project architecture by defining how new components will integrate with current systems. Where conflicts arise between new and existing patterns, this document provides guidance on maintaining consistency while implementing enhancements.

### Existing Project Analysis

#### Current Project State

| Aspect | Details |
|--------|---------|
| **Primary Purpose** | Connect Android devices to external Bluetooth GNSS receivers, providing mock location to the Android system |
| **Current Tech Stack** | Flutter (Dart UI) + Java (Android Service) + Rust (Native NMEA/UBX parsing via FFI) |
| **Architecture Style** | Layered: Flutter UI → Platform Channels → Android Foreground Service → Connection Managers |
| **Deployment Method** | Standard APK via `flutter build apk`, distributed on Google Play |

#### Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    Flutter UI (Dart)                    │
│  main.dart, connect_screen*.dart, settings_screen.dart  │
└─────────────────────────┬───────────────────────────────┘
                          │ Platform Channels
┌─────────────────────────▼───────────────────────────────┐
│              bluetooth_gnss_service.java                │
│     (Android Service - foreground, mock location)       │
├─────────────────────────┬───────────────────────────────┤
│   rfcomm_conn_mgr.java  │     ntrip_conn_mgr.java       │
│   (Bluetooth RFCOMM/BLE)│     (NTRIP corrections)       │
└─────────────────────────┼───────────────────────────────┘
                          │ InputStream/OutputStream
┌─────────────────────────▼───────────────────────────────┐
│              NativeParser.java (Rust FFI)               │
│              rust_lib_bluetooth_gnss                    │
└─────────────────────────────────────────────────────────┘
```

#### Key Architectural Patterns

1. **Platform Channel Communication**
   - `MethodChannel("com.clearevo.bluetooth_gnss/engine")` — Dart → Java method calls
   - `EventChannel("com.clearevo.bluetooth_gnss/engine_events")` — Java → Dart async updates
   - Parameters flow via `paramMap` with `ValueNotifier<dynamic>` for reactive UI

2. **Connection Management**
   - `rfcomm_conn_mgr` handles Bluetooth connections
   - Implements `Closeable`, provides `InputStream`/`OutputStream`
   - Callbacks via `rfcomm_conn_callbacks` interface

3. **State Management**
   - `ConnectState` enum: `Loading`, `PendingRequirements`, `ReadyToConnect`, `Connecting`, `Connected`
   - `ValueNotifier` pattern for reactive updates across Flutter UI
   - Device selection via SharedPreferences (`target_bdaddr`)

#### Available Documentation

| Documentation | Status |
|---------------|--------|
| README.md | Build instructions |
| Project Brief | `docs/brief.md` |
| PRD | `docs/prd.md` |
| Story files | `docs/stories/` |

#### Identified Constraints

- Must maintain GPL v2+ license compatibility
- Must not break existing Bluetooth functionality
- Must work on devices without USB-OTG (graceful degradation)
- Service must remain a single foreground service handling both transport types

### Change Log

| Change | Date | Version | Description | Author |
|--------|------|---------|-------------|--------|
| Initial | 2026-01-22 | 1.0 | Brownfield architecture for USB GNSS | Architect Agent |

---

## Enhancement Scope and Integration Strategy

### Enhancement Overview

| Aspect | Details |
|--------|---------|
| **Enhancement Type** | New Feature Addition + Integration with New Systems (Android USB Host API) |
| **Scope** | Add USB serial communication as alternative transport for GNSS data |
| **Integration Impact** | Moderate — new classes additive, existing code modified minimally |

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Flutter UI Layer                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐  │
│  │ settings_screen │  │connect_screen_* │  │    channels.dart    │  │
│  │  (device list)  │  │  (connection)   │  │ (platform channel)  │  │
│  └────────┬────────┘  └────────┬────────┘  └──────────┬──────────┘  │
└───────────┼────────────────────┼─────────────────────┼──────────────┘
            │                    │                     │
            │         MethodChannel / EventChannel     │
            │                    │                     │
┌───────────▼────────────────────▼─────────────────────▼──────────────┐
│                    bluetooth_gnss_service.java                       │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Connection Management                     │    │
│  │  ┌─────────────────┐          ┌─────────────────┐           │    │
│  │  │ rfcomm_conn_mgr │          │  usb_conn_mgr   │ ◄── NEW   │    │
│  │  │   (Bluetooth)   │          │     (USB)       │           │    │
│  │  └────────┬────────┘          └────────┬────────┘           │    │
│  │           │                            │                     │    │
│  │           └──────────┬─────────────────┘                     │    │
│  │                      ▼                                       │    │
│  │           InputStream / OutputStream                         │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                              │                                       │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              Existing Parsing & Mock Location                │    │
│  │         NativeParser (Rust) → Mock Location Provider         │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

### Integration Approach

| Layer | Strategy |
|-------|----------|
| **Code Integration** | New `usb_conn_mgr` class alongside existing `rfcomm_conn_mgr`; service orchestrates both |
| **Database Integration** | N/A — SharedPreferences only; add new USB-specific preference keys |
| **API Integration** | Extend existing `MethodChannel` with new USB methods; all existing methods unchanged |
| **UI Integration** | Unified device list showing both BT and USB with type indicators |

### Compatibility Requirements

| Requirement | Approach |
|-------------|----------|
| **Existing API Compatibility** | All existing platform channel methods unchanged; new USB methods are additive |
| **Bluetooth Functionality** | Zero modifications to `rfcomm_conn_mgr`; all BT code paths unchanged |
| **UI/UX Consistency** | USB devices use same list item layout as BT; only icon differs |
| **Performance Impact** | USB processing uses same stream pipeline; no overhead when USB not in use |

---

## Tech Stack

### Existing Technology Stack

| Category | Current Technology | Version | Usage in Enhancement |
|----------|-------------------|---------|---------------------|
| **UI Framework** | Flutter | Per `pubspec.yaml` | Extend existing widgets |
| **UI Language** | Dart | Latest stable | Add USB-related methods |
| **Android Service** | Java | Java 8+ | Add `usb_conn_mgr` |
| **Native Parsing** | Rust | 1.90 | No changes — transport-agnostic |
| **Rust Bridge** | flutter_rust_bridge | 2.11.1 | No changes needed |
| **Build System** | Gradle | Per project | Add USB library dependency |
| **State Management** | ValueNotifier | Built-in | Extend for USB state |
| **Preferences** | SharedPreferences | Latest | Add USB device preferences |

### New Technology Additions

| Technology | Version | Purpose | Rationale |
|------------|---------|---------|-----------|
| **usb-serial-for-android** | 3.7.0+ | USB serial communication | MIT licensed, mature, supports CDC-ACM/FTDI/CH340/PL2303/CP210x |
| **Android USB Host API** | API 12+ | USB device access | Required for USB-OTG; standard Android SDK |

### Dependency Configuration

**Gradle (`android/app/build.gradle`):**
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'
}
```

**AndroidManifest.xml:**
```xml
<uses-feature android:name="android.hardware.usb.host" android:required="false" />
```

---

## Data Models

### USB Device Model (Java)

```java
public class UsbDeviceInfo {
    int deviceId;
    String deviceName;
    int vendorId;
    int productId;
    String manufacturerName;
    String productName;
    String driverType;
}
```

### Unified Device Model (Dart)

```dart
enum DeviceConnectionType { bluetooth, usb }

class GnssDevice {
  final String id;
  final String name;
  final DeviceConnectionType type;
  final Map<String, dynamic> metadata;
}
```

### Preferences Schema

| Key | Type | Purpose | Default |
|-----|------|---------|---------|
| `target_bdaddr` | String | Selected Bluetooth device | `""` (existing) |
| `target_usb_device_id` | int | Selected USB device ID | `-1` (new) |
| `last_connection_type` | String | Last used connection type | `"bluetooth"` (new) |

---

## Component Architecture

### New Components

#### 1. usb_conn_mgr.java

**Responsibility:** Manage USB serial connection lifecycle.

**Key Interfaces:**
- `void connectWithAutoDetect(UsbDevice device)`
- `InputStream getInputStream()`
- `OutputStream getOutputStream()`
- `void close()`

**Dependencies:** `usb-serial-for-android` library, Android USB Host API

#### 2. usb_conn_callbacks.java

**Callback Interface:**
```java
public interface usb_conn_callbacks {
    void on_usb_permission_result(boolean granted);
    void on_usb_connected(String deviceName, int baudRate);
    void on_usb_disconnected(String reason);
    void on_usb_error(String error);
    void on_baud_rate_detected(int baudRate);
}
```

### Component Interaction

```
┌──────────────┐     ┌──────────────────────┐     ┌──────────────┐
│  Flutter UI  │────▶│ bluetooth_gnss_svc   │────▶│ usb_conn_mgr │
└──────────────┘     │                      │     └──────────────┘
                     │  ┌────────────────┐  │
                     │  │rfcomm_conn_mgr │  │
                     │  └────────────────┘  │
                     │                      │
                     │  ┌────────────────┐  │
                     │  │ ntrip_conn_mgr │  │
                     │  └────────────────┘  │
                     └──────────────────────┘
                              │
                              ▼
                     ┌──────────────────────┐
                     │    NativeParser      │
                     │   (Rust NMEA/UBX)    │
                     └──────────────────────┘
                              │
                              ▼
                     ┌──────────────────────┐
                     │ Mock Location API    │
                     └──────────────────────┘
```

---

## API Design (Platform Channels)

### New Methods

| Method | Purpose |
|--------|---------|
| `getUsbDevices()` | Enumerate connected USB serial devices |
| `connectUsb(deviceId)` | Initiate USB connection with auto-baud |
| `disconnectUsb()` | Close USB connection |
| `getConnectionStatus()` | Get current connection type and details |

### EventChannel Events

| Event Type | Purpose |
|------------|---------|
| `usb_device_attached` | USB device plugged in |
| `usb_device_detached` | USB device removed |
| `usb_connected` | USB connection established |
| `usb_disconnected` | USB connection closed |
| `usb_error` | USB error occurred |
| `usb_baud_detection_progress` | Baud rate detection status |

### Dart API

```dart
static Future<List<Map<String, dynamic>>> getUsbDevices() async { ... }
static Future<bool> connectUsb(int deviceId) async { ... }
static Future<bool> disconnectUsb() async { ... }
static Future<Map<String, dynamic>> getConnectionStatus() async { ... }
```

---

## Source Tree

### New Files

```
android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/
├── usb_conn_mgr.java           # NEW
├── usb_conn_callbacks.java     # NEW

android/app/src/main/res/xml/
└── usb_device_filter.xml       # NEW

lib/models/
└── gnss_device.dart            # NEW
```

### Modified Files

| File | Changes |
|------|---------|
| `bluetooth_gnss_service.java` | Add USB connection management |
| `MainActivity.java` | Register USB broadcast receiver |
| `AndroidManifest.xml` | USB permissions, intent filter |
| `build.gradle` | Add USB library dependency |
| `channels.dart` | Add USB platform channel methods |
| `connect_screen_idle.dart` | Unified device list |
| `connect_screen_connecting.dart` | Baud rate progress |
| `connect_screen_connected.dart` | Connection type display |
| `settings_screen.dart` | USB device selection |

---

## Infrastructure and Deployment

### Build Process

No changes to build process:
```bash
flutter pub get
flutter build apk
```

Gradle automatically handles USB library dependency.

### APK Size Impact

~200-250 KB increase (well under 500KB budget)

### Rollback Strategy

- USB feature is opt-in (only activates when user selects USB device)
- Bluetooth functionality unaffected
- Can revert to previous Play Store version if critical issues

---

## Coding Standards

### Java Standards

| Aspect | Pattern |
|--------|---------|
| Class naming | `snake_case` (e.g., `usb_conn_mgr`) |
| Constants | `UPPER_SNAKE_CASE` |
| Logging | `Log.d(TAG, "message")` |
| Error handling | Catch, log, callback |

### Dart Standards

| Aspect | Pattern |
|--------|---------|
| File naming | `snake_case.dart` |
| Class naming | `PascalCase` |
| Variables | `camelCase` |
| State | `ValueNotifier` pattern |

### Critical Rules

- Never modify existing platform channel method signatures
- Use existing `dlog()` and toast patterns for errors
- USB resources must be released in `close()` method
- All existing Bluetooth code paths must remain unchanged

---

## Testing Strategy

### Unit Tests

| Test | Location |
|------|----------|
| Baud rate detection | `test_usb_conn_mgr.java` |
| NMEA validation | `test_usb_conn_mgr.java` |
| Error messages | `test_usb_conn_mgr.java` |

### Integration Tests

- USB permission flow
- Device enumeration
- Service integration

### Regression Tests

- Full Bluetooth workflow must pass
- App works on devices without USB-OTG

### Hardware Testing

- ArduSimple RTK Surveyor Kit (ZED-F9P)
- 8-hour stability test
- Multiple Android devices

---

## Security Integration

### USB Permission Model

- User explicitly grants permission per-device
- Permissions revoked on device disconnect (by default)
- No new network exposure
- Data validated (NMEA checksums)

### No New Attack Surfaces

- USB data is parsed, never executed
- Same security model as Bluetooth
- Standard Android USB Host API

---

## Next Steps

### Developer Handoff

1. **Read documents:** brief.md → prd.md → architecture.md → story-1.1
2. **Study patterns:** `rfcomm_conn_mgr.java`, `channels.dart`
3. **Start with Story 1.1:** Project Setup & Dependency Integration

### Implementation Order

```
1.1 Project Setup → 1.2 USB Detection → 1.3 Connection Manager →
1.4 Baud Detection → 1.5 Service Integration → 1.6 NTRIP →
1.7 Unified UI → 1.8 Disconnect Handling → 1.9 Integration Testing
```

### Key Decisions Summary

| Decision | Choice |
|----------|--------|
| USB Library | `usb-serial-for-android` v3.7.0+ |
| Architecture | New `usb_conn_mgr` class (separate from BT) |
| UI | Unified device list with BT/USB icons |
| Baud Rate | Auto-detection (115200 → 4800) |
| Connection | Direct USB only (no hubs) |

---

*This architecture document is ready for implementation. Begin with Story 1.1.*
