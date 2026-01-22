# USB GNSS Brownfield Enhancement PRD

## Intro Project Analysis and Context

### Analysis Source

- IDE-based fresh analysis of codebase
- Project Brief available at `docs/brief.md`

### Existing Project Overview

#### Current Project State

Bluetooth GNSS is a Flutter + Rust + Java Android application that:
- Connects Android devices to external Bluetooth GPS/GNSS receivers (Classic RFCOMM and BLE)
- Parses NMEA/UBX data via Rust native library
- Provides mock location to Android system
- Supports NTRIP for RTK corrections
- Offers logging, position offsets, and auto-reconnect features

#### Architecture

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

### Available Documentation Analysis

| Documentation | Status |
|---------------|--------|
| Tech Stack Documentation | Partial (README build instructions) |
| Source Tree/Architecture | Not documented (analyzed from code) |
| Coding Standards | Not documented (inferred from code patterns) |
| API Documentation | Not documented |
| Project Brief | Available (`docs/brief.md`) |

### Enhancement Scope Definition

#### Enhancement Type

- [x] New Feature Addition
- [x] Integration with New Systems (Android USB Host API)

#### Enhancement Description

Add USB serial communication as an alternative connection method to external GNSS receivers, enabling users to connect via USB-OTG instead of or in addition to Bluetooth. The enhancement uses `usb-serial-for-android` library and integrates with the existing stream-based parsing pipeline.

#### Impact Assessment

- [x] Moderate Impact (some existing code changes)

New `usb_conn_mgr` is additive; modifications to `bluetooth_gnss_service.java` and Flutter UI are moderate; existing Bluetooth code remains untouched.

### Goals and Background Context

#### Goals

- Enable USB serial connectivity to external GNSS receivers
- Provide unified device selection UI showing both Bluetooth and USB devices
- Maintain full feature parity (NTRIP, logging, mock location, offsets) over USB
- Support baud rate auto-detection for simplified UX
- Achieve 8-hour session stability for mining field operations
- Zero regression in existing Bluetooth functionality

#### Background Context

The Bluetooth GNSS app has proven valuable for connecting Android devices to external GNSS receivers. However, users in the mining industry require USB connectivity for scenarios where Bluetooth is unreliable (RF-noisy environments near heavy equipment) or unavailable (USB-only receivers). The existing architecture already abstracts communication via `InputStream`/`OutputStream`, making USB serial integration a natural extension. The primary target hardware is the ArduSimple RTK Surveyor Kit (u-blox ZED-F9P) for precision localization in mining operations (hole implantation, drilling, loading).

### Change Log

| Change | Date | Version | Description | Author |
|--------|------|---------|-------------|--------|
| Initial PRD | 2026-01-22 | 1.0 | Initial brownfield PRD for USB GNSS connectivity | PM Agent |

---

## Requirements

### Functional Requirements

| ID | Requirement |
|----|-------------|
| FR1 | The app shall detect USB serial devices when connected via USB-OTG and display them in the device list |
| FR2 | The app shall display a unified device list showing both Bluetooth (paired) and USB (connected) devices with clear type indicators (BT/USB icons) |
| FR3 | The app shall establish USB serial connections using the `usb-serial-for-android` library supporting CDC-ACM, FTDI, CH340, PL2303, and CP210x chipsets |
| FR4 | The app shall automatically detect the correct baud rate by cycling through common rates (4800, 9600, 19200, 38400, 57600, 115200) |
| FR5 | The app shall request Android USB permissions when the user selects a USB device for connection |
| FR6 | The app shall route USB `InputStream` data to the existing NMEA/UBX parsing pipeline |
| FR7 | The app shall route NTRIP correction data to the USB `OutputStream` when NTRIP is enabled |
| FR8 | The app shall provide mock location updates from USB-connected GNSS receivers identical to Bluetooth behavior |
| FR9 | The app shall detect USB cable disconnection and notify the user with graceful resource cleanup |
| FR10 | The app shall support all existing features over USB: logging, position offsets, timestamp offsets, auto-reconnect |

### Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| NFR1 | USB connections shall establish within 5 seconds including baud rate auto-detection |
| NFR2 | USB connections shall maintain stability for 8+ hours continuous operation |
| NFR3 | USB data processing shall not introduce latency greater than Bluetooth (target: <100ms from receiver to mock location) |
| NFR4 | The enhancement shall not increase APK size by more than 500KB (library overhead) |
| NFR5 | USB connection handling shall not impact battery consumption beyond the existing Bluetooth baseline when USB is not in use |
| NFR6 | The app shall handle USB hot-plug events without crashing or entering an inconsistent state |

### Compatibility Requirements

| ID | Requirement |
|----|-------------|
| CR1 | **Existing API Compatibility:** All existing platform channel APIs between Flutter and Java shall remain unchanged; new USB APIs shall be additive |
| CR2 | **Bluetooth Functionality:** All existing Bluetooth features (RFCOMM, BLE, device pairing, auto-reconnect) shall continue to work identically |
| CR3 | **UI/UX Consistency:** USB device selection and connection flow shall match the existing Bluetooth UX patterns; users familiar with Bluetooth workflow shall find USB intuitive |
| CR4 | **Service Integration:** The `bluetooth_gnss_service` shall manage USB connections using the same foreground service lifecycle as Bluetooth connections |
| CR5 | **Parsing Compatibility:** USB data streams shall be processed by the existing `NativeParser` (Rust) without any changes to the parsing logic |

---

## User Interface Enhancement Goals

### Integration with Existing UI

The current Bluetooth GNSS UI follows a simple flow:
1. **Idle state** (`connect_screen_idle.dart`): Shows list of paired Bluetooth devices
2. **Connecting state** (`connect_screen_connecting.dart`): Shows connection progress
3. **Connected state** (`connect_screen_connected.dart`): Shows connection status, GNSS data, controls

**USB integration approach:**
- The idle state device list will be extended to show both Bluetooth (paired) and USB (connected) devices
- Each device entry will include a visual indicator (icon or badge) for connection type
- Selection of a USB device triggers USB-specific permission flow before connection
- Connecting and connected states remain structurally identical — only the underlying transport differs
- Status displays will indicate "USB" vs "Bluetooth" connection type

### Modified/New Screens and Views

| Screen | Modification |
|--------|--------------|
| `connect_screen_idle.dart` | **Modified:** Device list shows both BT and USB devices with type indicators; USB device detection on plug |
| `connect_screen_connecting.dart` | **Modified:** Connection progress includes baud rate detection status for USB |
| `connect_screen_connected.dart` | **Modified:** Status area indicates connection type (USB/BT); shows baud rate for USB connections |
| `channels.dart` | **Modified:** Add platform channel methods for USB device enumeration, permission requests, and connection |
| `settings_screen.dart` | **Minor:** No changes for MVP (manual baud rate config deferred to Phase 2) |

### UI Consistency Requirements

| Requirement | Description |
|-------------|-------------|
| Visual language | USB device entries shall use the same layout as Bluetooth entries; only the type indicator differs |
| Type indicators | Use recognizable icons: Bluetooth icon (existing) for BT devices, USB icon for USB devices |
| Selection behavior | Tapping a device initiates connection regardless of type; permission prompts appear as needed |
| Connection feedback | Progress indicators, success/failure messages, and status displays follow existing patterns |
| Error handling | USB-specific errors (permission denied, device disconnected, baud detection failed) use existing error display patterns with USB-specific messaging |
| Color scheme | No new colors; use existing theme colors for all USB-related UI elements |

---

## Technical Constraints and Integration Requirements

### Existing Technology Stack

| Layer | Technology | Version/Notes |
|-------|------------|---------------|
| **Languages** | Dart, Java, Rust | Flutter UI, Android service, native parsing |
| **Frameworks** | Flutter | Version specified in `pubspec.yaml` |
| **Native Bridge** | flutter_rust_bridge | Rust FFI for NMEA/UBX parsing |
| **Android Min SDK** | API 21+ | USB Host requires API 12+ |
| **Build System** | Gradle (Android), Cargo (Rust), Flutter | Standard toolchain |
| **External Dependencies** | Google Play Services (FusedLocation) | For location services |
| **License** | GPL v2+ | Must maintain compatibility |

### Integration Approach

**Database Integration Strategy:**
- N/A — App uses SharedPreferences for settings, no database changes required

**API Integration Strategy:**
- New platform channel methods added to `channels.dart` for USB operations
- Java-side handlers in `bluetooth_gnss_service.java` for USB device enumeration, permissions, connection
- USB connection manager (`usb_conn_mgr.java`) exposes same stream interface as `rfcomm_conn_mgr.java`

**Frontend Integration Strategy:**
- Device list state extended to include USB devices alongside Bluetooth devices
- Connection state machine handles both transport types with type-specific permission flows
- Status displays parameterized by connection type

**Testing Integration Strategy:**
- Unit tests for `usb_conn_mgr` baud rate detection logic
- Integration tests for USB permission flow (instrumented tests)
- Manual testing with ZED-F9P hardware
- Regression testing of all Bluetooth functionality

### Code Organization and Standards

**File Structure:**
```
android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/
├── bluetooth_gnss_service.java  (MODIFIED - add USB connection handling)
├── rfcomm_conn_mgr.java         (UNCHANGED - Bluetooth connection)
├── usb_conn_mgr.java            (NEW - USB serial connection)
├── usb_conn_callbacks.java      (NEW - USB callback interface)
├── ...existing files...

lib/
├── channels.dart                (MODIFIED - add USB platform channels)
├── connect_screen_idle.dart     (MODIFIED - unified device list)
├── connect_screen_connecting.dart (MODIFIED - baud rate status)
├── connect_screen_connected.dart  (MODIFIED - connection type display)
├── ...existing files...

AndroidManifest.xml              (MODIFIED - USB permissions, intent filters)
build.gradle                     (MODIFIED - add usb-serial-for-android dependency)
```

**Naming Conventions:**
- Java: `snake_case` class names (matching existing `rfcomm_conn_mgr` style)
- Dart: `snake_case` file names, `camelCase` variables (Flutter standard)
- Constants: `UPPER_SNAKE_CASE` (matching existing patterns)

**Coding Standards:**
- Follow existing code patterns in `rfcomm_conn_mgr.java` for connection lifecycle
- Use existing logging via `Log.d(TAG, ...)` pattern
- Implement callback interfaces for async operations (matching `rfcomm_conn_callbacks`)

### Deployment and Operations

**Build Process Integration:**
- Add `usb-serial-for-android` dependency to `build.gradle`:
  ```gradle
  implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'
  ```
- No Rust changes required — parsing is transport-agnostic
- No Flutter plugin additions — using platform channels

**Deployment Strategy:**
- Standard APK build via `flutter build apk`
- No backend changes
- No staged rollout required (feature is opt-in by device selection)

### Risk Assessment and Mitigation

**Technical Risks:**

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| USB-OTG not working on some devices | Medium | High | Document tested devices; provide troubleshooting guide |
| `usb-serial-for-android` bugs with CDC-ACM | Low | High | Library is mature; fallback to fork if needed |
| Baud rate detection fails on some receivers | Medium | Medium | Log attempts for debugging; Phase 2 manual override |
| USB permission denied by user | Low | Medium | Clear permission rationale in UI; graceful handling |

**Integration Risks:**

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Platform channel API breaks existing BT flow | Low | High | Additive API changes only; comprehensive regression testing |
| Service lifecycle issues with dual transports | Medium | Medium | Clear state management; only one active connection at a time |
| UI state management complexity | Medium | Low | Follow existing patterns; thorough testing |

---

## Epic and Story Structure

### Epic Approach

**Epic Structure Decision:** Single Epic

**Rationale:** This enhancement is a cohesive feature addition — USB serial connectivity. All work items are interdependent and deliver value only when combined.

---

## Epic 1: USB Serial GNSS Connectivity

**Epic Goal:** Enable users to connect to external GNSS receivers via USB serial (USB-OTG), providing an alternative to Bluetooth with full feature parity (NTRIP, logging, mock location, offsets).

**Integration Requirements:**
- All existing Bluetooth functionality must remain intact
- USB connections use the same service lifecycle as Bluetooth
- Stream data flows through existing parsing pipeline unchanged
- UI changes are additive to existing device list

---

### Story 1.1: Project Setup & Dependency Integration

**As a** developer,
**I want** the USB serial library integrated and permissions configured,
**so that** I have the foundation to build USB connectivity features.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `usb-serial-for-android` library (v3.7.0+) is added to `build.gradle` dependencies |
| AC2 | `AndroidManifest.xml` includes `<uses-feature android:name="android.hardware.usb.host" android:required="false" />` |
| AC3 | USB intent filter is configured for device attachment events |
| AC4 | Project builds successfully with `flutter build apk` |
| AC5 | Library classes are accessible from Java code (import compiles) |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Existing Bluetooth pairing and connection flow works unchanged |
| IV2 | App launches and runs normally on devices without USB-OTG |
| IV3 | APK size increase is <500KB |

---

### Story 1.2: USB Device Detection & Enumeration

**As a** user,
**I want** the app to detect when I plug in a USB GNSS receiver,
**so that** I can see it as an available device to connect to.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | App detects USB serial devices when connected via USB-OTG |
| AC2 | USB device information (name, vendor ID, product ID) is retrievable |
| AC3 | BroadcastReceiver handles `USB_DEVICE_ATTACHED` and `USB_DEVICE_DETACHED` events |
| AC4 | Device list updates dynamically when USB devices are plugged/unplugged |
| AC5 | Platform channel method `getUsbDevices()` returns list of connected USB serial devices |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Bluetooth device enumeration continues to work independently |
| IV2 | App handles scenario where no USB devices are connected (empty list, no errors) |
| IV3 | Hot-plug events don't cause crashes or ANRs |

---

### Story 1.3: USB Connection Manager Core

**As a** developer,
**I want** a `usb_conn_mgr` class that establishes USB serial connections,
**so that** I can obtain `InputStream`/`OutputStream` for GNSS data transfer.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `usb_conn_mgr.java` class created following `rfcomm_conn_mgr` patterns |
| AC2 | `usb_conn_callbacks.java` interface defined for connection state callbacks |
| AC3 | USB permission request flow implemented (prompts user, handles grant/deny) |
| AC4 | Connection establishes to USB serial device at specified baud rate |
| AC5 | `InputStream` and `OutputStream` are accessible after successful connection |
| AC6 | `close()` method properly releases USB resources |
| AC7 | Connection errors are reported via callback with descriptive messages |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | `rfcomm_conn_mgr` remains unchanged and functional |
| IV2 | USB connection can be established while no Bluetooth connection is active |
| IV3 | Resource cleanup doesn't affect other app components |

---

### Story 1.4: Baud Rate Auto-Detection

**As a** user,
**I want** the app to automatically detect the correct baud rate,
**so that** I don't need to manually configure serial settings.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Auto-detection cycles through baud rates: 115200, 57600, 38400, 19200, 9600, 4800 |
| AC2 | Detection reads data at each rate, validates NMEA sentence structure (`$` prefix, `*` checksum) |
| AC3 | Detection completes within 5 seconds total |
| AC4 | Successful detection reports the detected baud rate via callback |
| AC5 | Failed detection (no valid data at any rate) reports error with attempted rates |
| AC6 | Detection can be cancelled if user disconnects during process |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Auto-detection works correctly with ZED-F9P at 115200 baud |
| IV2 | Detection process doesn't leave serial port in inconsistent state |
| IV3 | Timeout handling doesn't cause resource leaks |

---

### Story 1.5: Service Integration

**As a** user,
**I want** USB GNSS data to provide mock location updates,
**so that** my location apps use the external USB receiver's position.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | `bluetooth_gnss_service.java` accepts USB connection type parameter |
| AC2 | Service manages `usb_conn_mgr` lifecycle alongside existing `rfcomm_conn_mgr` |
| AC3 | USB `InputStream` data routes to existing `NativeParser` for NMEA/UBX parsing |
| AC4 | Parsed position data updates Android mock location provider |
| AC5 | Service foreground notification indicates USB connection (vs Bluetooth) |
| AC6 | Position offset settings apply to USB-sourced positions |
| AC7 | Logging (if enabled) captures USB-received data |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Bluetooth connections continue to work when no USB device is connected |
| IV2 | Mock location updates arrive at expected rate (matching receiver output) |
| IV3 | Service lifecycle (start/stop/restart) handles USB connections correctly |

---

### Story 1.6: NTRIP Over USB

**As a** user,
**I want** NTRIP correction data sent to my USB-connected RTK receiver,
**so that** I get centimeter-level accuracy via USB connection.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | NTRIP connection can be established while USB GNSS connection is active |
| AC2 | RTCM correction data from NTRIP is written to USB `OutputStream` |
| AC3 | GGA sentences are sent to NTRIP server (for VRS) using USB-received position |
| AC4 | NTRIP byte counters update correctly for USB connections |
| AC5 | NTRIP reconnection logic works with USB connection (same as Bluetooth) |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | NTRIP over Bluetooth continues to work unchanged |
| IV2 | RTK fix achieved with ZED-F9P via USB + NTRIP |
| IV3 | NTRIP disable/enable settings apply to USB connections |

---

### Story 1.7: Unified Device List UI

**As a** user,
**I want** to see both Bluetooth and USB devices in one list,
**so that** I can easily choose my preferred connection method.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Device list in `connect_screen_idle.dart` shows both BT and USB devices |
| AC2 | Each device entry displays connection type indicator (BT icon / USB icon) |
| AC3 | USB devices appear/disappear dynamically as they're plugged/unplugged |
| AC4 | Tapping a USB device initiates USB connection flow (permission → connect) |
| AC5 | Tapping a Bluetooth device continues to work as before |
| AC6 | Empty states handled: "No devices" shows when no BT paired AND no USB connected |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Bluetooth device list remains fully functional |
| IV2 | Device selection state management handles both types correctly |
| IV3 | UI performs well with multiple devices (no lag or jank) |

---

### Story 1.8: Connection Status & Disconnect Handling

**As a** user,
**I want** clear feedback about my USB connection status,
**so that** I know when I'm connected and can recover from disconnections.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | Connected screen shows "USB" connection type and detected baud rate |
| AC2 | USB cable removal is detected within 1 second |
| AC3 | Disconnection triggers user notification (toast or status update) |
| AC4 | Resources are cleaned up gracefully on disconnect (no leaks) |
| AC5 | Auto-reconnect setting applies to USB (attempts reconnection on disconnect) |
| AC6 | Connection errors display user-friendly messages (permission denied, device not found, etc.) |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Bluetooth disconnect handling remains unchanged |
| IV2 | Rapid connect/disconnect cycles don't cause crashes |
| IV3 | Service remains stable after USB disconnection |

---

### Story 1.9: Integration Testing & Validation

**As a** developer,
**I want** comprehensive testing of USB functionality,
**so that** I can release with confidence that existing features are unaffected.

#### Acceptance Criteria

| # | Criterion |
|---|-----------|
| AC1 | USB connection tested with ArduSimple RTK Surveyor Kit (ZED-F9P) |
| AC2 | Mock location verified in third-party apps (Maps, SW Maps, etc.) |
| AC3 | NTRIP + USB RTK fix verified with real base station |
| AC4 | 8-hour continuous session test completed without disconnection |
| AC5 | All Bluetooth regression tests pass |
| AC6 | USB hot-plug stress test (10 connect/disconnect cycles) passes |
| AC7 | Tested on at least 2 different Android devices |

#### Integration Verification

| # | Verification |
|---|--------------|
| IV1 | Full Bluetooth workflow tested: pair → connect → NTRIP → mock location → disconnect |
| IV2 | App store listing requirements still met (permissions explained) |
| IV3 | No new crashes in crash reporting (if applicable) |

---

### Story Dependency Diagram

```
1.1 Project Setup
 └── 1.2 USB Detection
      └── 1.3 Connection Manager
           ├── 1.4 Baud Rate Auto-Detection
           └── 1.5 Service Integration
                ├── 1.6 NTRIP Over USB
                └── 1.7 Unified Device List UI
                     └── 1.8 Connection Status & Disconnect
                          └── 1.9 Integration Testing
```

---

## Key Decisions Summary

| Decision | Choice |
|----------|--------|
| USB Serial Library | `usb-serial-for-android` (not raw Android USB API) |
| Architecture | New `usb_conn_mgr` class (separate from Bluetooth code) |
| UI Approach | Unified device list with BT/USB connection type indicators |
| Baud Rate | Auto-detection included in MVP |
| USB Topology | Direct connection only (no hub support in MVP) |
| Target Hardware | ArduSimple RTK Surveyor Kit (ZED-F9P) |
| Primary Use Case | Mining field operations |
| Future Integration | Exposed Android service with mock location disabled (post-MVP) |

---

## Next Steps

1. Review and approve this PRD
2. Create story files in `docs/stories/` for implementation tracking
3. Begin Story 1.1: Project Setup & Dependency Integration
4. Proceed through stories sequentially per dependency diagram

---

*This PRD is ready for handoff to the Development team for implementation.*
