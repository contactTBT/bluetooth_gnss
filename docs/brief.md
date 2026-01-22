# Project Brief: USB GNSS

## Executive Summary

**Product Concept:** USB GNSS — an enhancement to the Bluetooth GNSS Android application that adds USB serial communication as an alternative connection method for external GPS/GNSS receivers, enabling users to choose between Bluetooth and USB-OTG connections.

**Primary Problem:** While Bluetooth GNSS effectively connects Android devices to external GPS receivers via Bluetooth, some use cases require USB connectivity — particularly for devices that lack Bluetooth, for improved reliability in RF-noisy environments, or for users who prefer wired connections for reduced latency and power consumption.

**Target Market:** Mining industry professionals requiring precise localization for hole implantation, drilling, loading, and related operations. The feature implementation remains transport-agnostic but the primary use case and validation focus is mining field operations.

**Key Value Proposition:** Provide connection flexibility by supporting both Bluetooth and USB serial communication within the same app, leveraging the existing architecture (InputStream/OutputStream abstraction, NMEA/UBX parsing, mock location provider) with minimal disruption.

---

## Problem Statement

### Current State and Pain Points

The Bluetooth GNSS app currently supports only Bluetooth (Classic RFCOMM and BLE) connections to external GNSS receivers. Users with USB-only GNSS devices, or those operating in environments where Bluetooth is unreliable or prohibited, cannot use the application.

**Specific pain points:**
- **Hardware incompatibility:** Some high-precision GNSS receivers (especially older or industrial models) only offer USB connectivity
- **Bluetooth interference:** RF-noisy environments (near radio equipment, in vehicles with multiple Bluetooth devices) can cause connection drops
- **Power considerations:** Bluetooth consumes additional battery on the receiver; USB can power the device while communicating
- **Latency sensitivity:** Some precision applications benefit from the lower, more consistent latency of wired connections

### Impact of the Problem

- Users with USB-only receivers must seek alternative apps or cannot use mock location at all
- Professional users in surveying/GIS may choose competing solutions that offer USB support
- Reliability issues in noisy environments lead to user frustration and negative reviews

### Why Existing Solutions Fall Short

- Other GPS mock apps either lack USB support entirely or have poor UX
- Generic USB serial terminal apps don't provide mock location integration
- No single app currently offers the combination of USB + Bluetooth + NTRIP + mock location that Bluetooth GNSS provides

### Urgency

USB-C is now ubiquitous on Android devices, and USB-OTG support is standard. The technical barriers to USB serial communication have lowered significantly, making this an opportune time to add the feature.

---

## Proposed Solution

### Core Concept and Approach

Add a USB serial connection manager (`usb_conn_mgr`) alongside the existing `rfcomm_conn_mgr`, implementing the same `InputStream`/`OutputStream` pattern. The app will detect connected USB serial devices, allow users to select USB as the connection type, and route data through the existing NMEA/UBX parsing and mock location pipeline.

**High-level architecture:**
```
┌─────────────────┐     ┌─────────────────┐
│  Bluetooth UI   │     │     USB UI      │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│ rfcomm_conn_mgr │     │  usb_conn_mgr   │  ← NEW
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
         ┌─────────────────────┐
         │ InputStream/Output  │
         │   Stream Abstraction│
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │ Existing parsing &  │
         │ mock location logic │
         └─────────────────────┘
```

### Key Differentiators

- **Unified experience:** Same app handles both Bluetooth and USB — users don't need multiple apps
- **Shared infrastructure:** NTRIP support, logging, mock location offsets all work with USB connections
- **Minimal disruption:** Existing Bluetooth functionality remains untouched

### Why This Will Succeed

- The existing architecture already abstracts communication via streams — USB serial fits naturally
- Proven USB serial libraries exist for Android (e.g., `usb-serial-for-android` by mik3y)
- The app already handles Android permissions, foreground services, and mock location — only USB-specific permissions need adding

### High-Level Vision

Users open the app, see available connection options (paired Bluetooth devices AND connected USB devices) in a unified device list with connection type indicators, select their preferred connection, and connect. The rest of the experience (NTRIP, logging, mock location) remains identical regardless of transport.

---

## Target Users

### Primary User Segment: Mining Industry Professionals

**Profile:**
- Mining operations personnel requiring precise localization
- Use cases: hole implantation, drilling, loading operations
- Use high-precision external GNSS receivers (RTK-capable)
- Technically competent, familiar with NTRIP and coordinate systems
- Work in field conditions where reliability is critical

**Current Behaviors:**
- Using Bluetooth GNSS or similar apps with Bluetooth receivers
- May own multiple receivers (some Bluetooth-only, some USB-only)
- Configure NTRIP for RTK corrections
- Log raw NMEA/UBX data for post-processing

**Specific Needs:**
- Reliable connection in RF-noisy mining environments (heavy equipment, radio communications)
- Ability to use USB-only receivers without switching apps
- Consistent experience across connection types
- Power delivery to receiver via USB while operating

**Goals:**
- Accurate mock location for field data collection and mining software
- Minimize equipment failures during time-sensitive operations
- Reduce number of apps/tools needed in workflow

---

## Goals & Success Metrics

### Business Objectives

- **Expand hardware compatibility:** Enable connection to USB-only GNSS receivers used in mining operations
- **Improve field reliability:** Provide wired connection option for RF-challenging mining environments
- **Reduce workflow friction:** Eliminate need for multiple apps or workarounds when using USB receivers
- **Maintain existing functionality:** Zero regression in Bluetooth features for current users

### User Success Metrics

- **Connection success rate:** USB connections establish reliably on first attempt (>95%)
- **Position delivery:** Mock location updates at expected rate (1Hz minimum, matching receiver output)
- **Session stability:** USB connections remain stable for full work shift (8+ hours) without drops
- **Feature parity:** All existing features (NTRIP, logging, offsets) work identically over USB

### Key Performance Indicators (KPIs)

- **Adoption:** Number of users utilizing USB connection mode (vs. Bluetooth-only)
- **Reliability:** Mean time between connection failures for USB sessions
- **Compatibility:** Number of confirmed working USB GNSS receiver models
- **User satisfaction:** App store rating maintained or improved after feature release

---

## MVP Scope

### Core Features (Must Have)

- **USB device detection:** Automatically detect connected USB serial devices when plugged in
- **Unified device list:** Display both Bluetooth and USB devices in single list with type indicators (BT/USB icons)
- **USB connection manager:** New `usb_conn_mgr` class using `usb-serial-for-android` library
- **Baud rate auto-detection:** Automatically detect correct baud rate (common rates: 4800, 9600, 19200, 38400, 57600, 115200)
- **USB permission handling:** Request and manage Android USB permissions with user-friendly prompts
- **Stream abstraction:** Route USB InputStream/OutputStream to existing parsing pipeline
- **Feature parity:** NTRIP, logging, mock location offsets all functional over USB
- **Connection status:** Clear indication of USB connection state in UI
- **Graceful disconnect handling:** Detect USB cable removal, clean up resources, notify user

### Out of Scope for MVP

- USB hub support (direct connection only)
- Manual baud rate configuration UI
- USB device filtering/favorites
- Simultaneous Bluetooth + USB connections
- USB power delivery negotiation/monitoring
- Support for non-serial USB protocols (e.g., USB HID GPS devices)

### MVP Success Criteria

- Successfully connect to ArduSimple RTK Surveyor Kit (ZED-F9P) via USB
- Mock location updates received at receiver's configured rate
- NTRIP corrections flow to receiver over USB connection
- 8-hour field session stability in mining environment
- No regression in existing Bluetooth functionality

---

## Post-MVP Vision

### Phase 2 Features

- **Manual baud rate override:** Settings option for users with non-standard configurations
- **USB device profiles:** Remember settings per device (baud rate, protocol preferences)
- **Connection auto-start:** Option to auto-connect when specific USB device is plugged in
- **Enhanced status display:** Show USB-specific info (baud rate detected, data throughput)
- **Device compatibility database:** Crowdsourced list of confirmed working USB GNSS receivers

### Long-term Vision

- **Simultaneous connections:** Use multiple GNSS sources (USB + Bluetooth) for redundancy or comparison
- **USB hub support:** Connect multiple USB devices through a hub
- **Data multiplexing:** Route GNSS data to multiple consumers (mock location + logging + network stream)
- **Integration APIs:** Exposed Android service for other apps to consume GNSS data directly (with mock location disabled)
- **Cross-platform consideration:** Evaluate USB serial support feasibility for iOS (limited but possible via MFi)

### Expansion Opportunities

- **Mining-specific features:** Integration with mining software platforms, shift logging, equipment tagging
- **Fleet management:** Centralized configuration deployment for multiple devices
- **Offline map caching:** Pre-load site maps for areas with no connectivity
- **Sensor fusion:** Combine GNSS with IMU data for improved positioning in challenging conditions

---

## Technical Considerations

### Platform Requirements

- **Target Platforms:** Android (existing app platform)
- **Minimum Android Version:** Same as current app (API 21+, USB Host requires API 12+)
- **USB-OTG Support:** Required on target devices (standard on most Android phones since ~2013)
- **Performance Requirements:** Process NMEA sentences at receiver output rate (typically 1-10 Hz) without lag

### Technology Preferences

- **USB Serial Library:** `usb-serial-for-android` (mik3y) — MIT licensed, supports CDC-ACM, FTDI, CH340, PL2303, CP210x
- **Frontend:** Existing Flutter UI — add USB device list items and connection type indicators
- **Backend:** New Java `usb_conn_mgr` class following patterns from `rfcomm_conn_mgr`
- **Parsing:** Existing Rust native library (NativeParser) — no changes needed, already transport-agnostic

### Architecture Considerations

- **Repository Structure:** Changes primarily in `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/`
- **New Files:**
  - `usb_conn_mgr.java` — USB connection management
  - `usb_device_list.java` — USB device enumeration (or integrate into existing device list logic)
- **Modified Files:**
  - `bluetooth_gnss_service.java` — Add USB connection type handling
  - Flutter UI files — Unified device list with connection type indicators
  - `AndroidManifest.xml` — USB permissions and intent filters

- **Integration Requirements:**
  - USB permission request flow (Android USB Host API)
  - USB device attach/detach broadcast receivers
  - Coordinate with existing Bluetooth device list UI

- **Security/Compliance:**
  - USB permission model is user-consent based (similar to Bluetooth)
  - No sensitive data handling changes — same NMEA/UBX streams
  - GPL v2+ license compatibility — `usb-serial-for-android` is MIT (compatible)

---

## Constraints & Assumptions

### Constraints

- **Budget:** Open-source project — no budget for commercial libraries or services; must use freely available dependencies
- **Timeline:** Not explicitly defined — feature-driven release when MVP criteria met
- **Resources:** Solo developer or small team; must minimize complexity and leverage existing patterns
- **Technical:**
  - Must maintain GPL v2+ license compatibility
  - Cannot modify core Android USB Host API behavior
  - Limited to USB-OTG capable Android devices
  - Direct USB connection only (no hub support in MVP)

### Key Assumptions

- Target Android devices have functional USB-OTG support
- ArduSimple RTK Surveyor Kit (ZED-F9P) presents as standard CDC-ACM USB serial device
- `usb-serial-for-android` library is stable and actively maintained
- Baud rate auto-detection can cycle through common rates without causing device issues
- Users will grant USB permissions when prompted (similar acceptance rate to Bluetooth)
- Mining field conditions (dust, vibration, temperature) do not affect USB-C connector reliability significantly more than Bluetooth stability
- Existing NMEA/UBX parsing handles data identically regardless of transport source
- NTRIP correction data can be written to USB output stream same as Bluetooth output stream
- Mock location provider works identically whether position data arrives via USB or Bluetooth

---

## Risks & Open Questions

### Key Risks

- **USB-OTG compatibility variance:** Some Android devices have buggy USB-OTG implementations or require specific adapters
  - *Impact:* Users unable to connect despite having USB-OTG "support"
  - *Mitigation:* Document tested device/adapter combinations; provide troubleshooting guide

- **Library dependency risk:** `usb-serial-for-android` could become unmaintained or have undiscovered bugs
  - *Impact:* Blocked on fixes or forced to fork/maintain
  - *Mitigation:* Library is MIT licensed and well-established (3k+ stars); forking is feasible if needed

- **Baud rate auto-detection failures:** Some devices may not respond predictably during rate cycling
  - *Impact:* Connection fails or takes excessive time
  - *Mitigation:* Implement timeout logic; add manual override in Phase 2; document known-good settings for target hardware

- **USB disconnect handling:** Cable removal, phone rotation, or USB power issues could cause abrupt disconnects
  - *Impact:* Data loss, app crash, or hung state
  - *Mitigation:* Robust broadcast receiver for USB detach events; graceful cleanup; auto-reconnect option

- **Mining environment durability:** USB-C connectors exposed to dust, vibration, and temperature extremes
  - *Impact:* Intermittent connections or hardware damage
  - *Mitigation:* Recommend ruggedized cables/enclosures; out of app scope but important for deployment guidance

### Open Questions

- What Android devices are currently used in mining operations? (for targeted testing)
- Are there specific ruggedized phone/tablet models in use?
- Is there an existing cable/adapter setup, or will new hardware be procured?
- What is the typical GNSS update rate configured on the ZED-F9P? (1 Hz, 5 Hz, 10 Hz?)
- Are there any existing integrations with mining software that should inform the exposed service API design?

### Areas Needing Further Research

- `usb-serial-for-android` behavior with ZED-F9P specifically (bench testing)
- Android USB permission UX across different device manufacturers (Samsung, Zebra, etc.)
- Power delivery behavior — does USB connection charge or drain phone battery?
- Latency comparison: USB vs. Bluetooth for same receiver

---

## Appendices

### A. Research Summary

**Technical Research:**
- Reviewed existing `rfcomm_conn_mgr.java` architecture — uses `InputStream`/`OutputStream` abstraction suitable for USB extension
- Identified `usb-serial-for-android` as preferred library (MIT license, active maintenance, CDC-ACM support)
- Confirmed ZED-F9P (ArduSimple RTK Surveyor Kit) uses standard USB CDC-ACM class

**Codebase Analysis:**
- Connection management: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/rfcomm_conn_mgr.java`
- Service layer: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`
- Stream handling: `inputstream_to_queue_reader_thread.java`, `queue_to_outputstream_writer_thread.java`
- Native parsing: `NativeParser.java` (Rust FFI) — transport-agnostic

**Library Evaluation:**
- `usb-serial-for-android` (mik3y): https://github.com/mik3y/usb-serial-for-android
  - 3,400+ GitHub stars
  - Supports: CDC-ACM, FTDI, CH340, PL2303, CP210x
  - MIT License (GPL v2+ compatible)
  - Active maintenance

### B. Target Hardware Reference

**ArduSimple RTK Calibrated Surveyor Kit**
- Chipset: u-blox ZED-F9P
- USB Interface: CDC-ACM (standard serial)
- Protocols: NMEA 0183, UBX binary, RTCM3
- Baud rates: Configurable (default typically 38400 or 115200)
- Capabilities: Multi-band (L1/L2), RTK, centimeter-level accuracy
- Product page: https://www.ardusimple.com/product/rtk-calibrated-surveyor-kit/

### C. References

- Bluetooth GNSS original repository: https://github.com/ykasidit/bluetooth_gnss
- usb-serial-for-android library: https://github.com/mik3y/usb-serial-for-android
- Android USB Host API: https://developer.android.com/develop/connectivity/usb/host
- u-blox ZED-F9P documentation: https://www.u-blox.com/en/product/zed-f9p-module

---

## Next Steps

### Immediate Actions

1. **Bench test USB serial library:** Connect ZED-F9P to Android device, verify `usb-serial-for-android` detects and communicates correctly
2. **Prototype `usb_conn_mgr`:** Create minimal implementation that opens USB serial connection and reads NMEA data
3. **Validate baud rate auto-detection:** Test cycling through common rates with ZED-F9P
4. **Integrate with existing stream pipeline:** Route USB InputStream to existing parsing logic, verify NMEA sentences parse correctly
5. **Test mock location delivery:** Confirm position updates flow through to Android mock location provider
6. **UI integration:** Add USB devices to unified device list with connection type indicators
7. **NTRIP verification:** Confirm correction data writes to USB OutputStream and receiver processes it
8. **Field testing:** Deploy to mining environment for real-world validation

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
| Future Integration | Exposed Android service with mock location disabled |

---

*This Project Brief provides the full context for USB GNSS. Please proceed to PRD generation to create detailed requirements.*
