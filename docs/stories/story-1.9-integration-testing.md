# Story 1.9: Integration Testing & Validation

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.9 |
| **Status** | Draft |
| **Priority** | High |
| **Dependencies** | Story 1.8 |

## User Story

**As a** developer,
**I want** comprehensive testing of USB functionality,
**so that** I can release with confidence that existing features are unaffected.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | USB connection tested with ArduSimple RTK Surveyor Kit (ZED-F9P) | |
| AC2 | Mock location verified in third-party apps (Maps, SW Maps, etc.) | |
| AC3 | NTRIP + USB RTK fix verified with real base station | |
| AC4 | 8-hour continuous session test completed without disconnection | |
| AC5 | All Bluetooth regression tests pass | |
| AC6 | USB hot-plug stress test (10 connect/disconnect cycles) passes | |
| AC7 | Tested on at least 2 different Android devices | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Full Bluetooth workflow tested: pair → connect → NTRIP → mock location → disconnect | |
| IV2 | App store listing requirements still met (permissions explained) | |
| IV3 | No new crashes in crash reporting (if applicable) | |

## Test Plan

### 1. USB Connection Tests

#### 1.1 Basic Connection
| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| USB detection | Plug in ZED-F9P via USB-OTG | Device appears in list with USB icon | |
| USB connection | Tap USB device in list | Permission prompt → Connection established | |
| Baud rate detection | Connect to ZED-F9P | Baud rate (115200) detected automatically | |
| Data reception | After connection | NMEA sentences visible in log/debug | |
| Disconnect | Tap disconnect button | Connection closed cleanly | |

#### 1.2 Mock Location
| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| Mock location enable | Connect USB, enable mock location | System uses external GPS position | |
| Google Maps | Open Maps after USB connection | Blue dot at receiver's location | |
| SW Maps | Open SW Maps after USB connection | Position matches receiver | |
| Position accuracy | Compare to known reference | Position within expected accuracy | |

#### 1.3 NTRIP + RTK
| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| NTRIP connect | Configure NTRIP, connect USB | NTRIP connection established | |
| RTCM flow | Monitor NTRIP counters | Bytes received and sent incrementing | |
| RTK fix | Wait for convergence | Fix type changes to RTK Fixed (4) | |
| Accuracy | Check position after RTK fix | Position accuracy <10cm | |

### 2. Stability Tests

#### 2.1 Long Duration
| Test Case | Duration | Conditions | Expected Result | Status |
|-----------|----------|------------|-----------------|--------|
| 8-hour test | 8 hours | USB connected, mock location active | No disconnections, stable position | |
| With NTRIP | 8 hours | USB + NTRIP active | RTK fix maintained | |

#### 2.2 Stress Tests
| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| Hot-plug x10 | Connect/disconnect 10 times rapidly | No crashes, proper detection each time | |
| Permission deny x5 | Deny permission 5 times | Graceful error handling, no crashes | |
| NTRIP reconnect x5 | Drop network 5 times | NTRIP reconnects each time | |

### 3. Bluetooth Regression Tests

| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| BT device list | Open app (no USB) | Paired BT devices shown | |
| BT connection | Tap BT device | Connects as before | |
| BT mock location | Enable mock location | System uses BT GPS position | |
| BT NTRIP | Configure NTRIP | RTCM flows to BT receiver | |
| BT disconnect | Tap disconnect | Clean disconnection | |
| BT auto-reconnect | Enable auto-reconnect, power cycle receiver | Reconnects automatically | |

### 4. Multi-Device Testing

| Device | Android Version | USB-OTG Works | All Tests Pass | Notes |
|--------|-----------------|---------------|----------------|-------|
| Device 1: _________ | _______ | | | |
| Device 2: _________ | _______ | | | |

### 5. Edge Cases

| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| No USB-OTG | Test on device without OTG | App works, USB section empty or hidden | |
| USB hub | Connect via USB hub | Expected: not supported message or works | |
| Multiple USB | Connect 2 USB serial devices | Both appear in list (if supported) | |
| BT + USB list | Have paired BT and connected USB | Both appear with correct icons | |
| Low battery | Test at <20% battery | USB still works (OTG power from phone) | |

### 6. Error Handling

| Test Case | Steps | Expected Result | Status |
|-----------|-------|-----------------|--------|
| Permission denied | Deny USB permission | Friendly error message, can retry | |
| Wrong device | Connect non-GNSS USB serial | Baud detection fails with message | |
| Cable disconnect | Unplug during connection | Disconnect detected, notification shown | |
| Cable disconnect (data) | Unplug while receiving data | Clean disconnect, resources freed | |

## Test Environment

### Required Hardware
- [ ] ArduSimple RTK Calibrated Surveyor Kit (ZED-F9P)
- [ ] USB-OTG adapter (USB-C to USB-A or direct USB-C cable)
- [ ] Android phone 1: ________________
- [ ] Android phone 2: ________________
- [ ] Bluetooth GNSS receiver (for regression testing)

### Required Software
- [ ] Latest app build with USB support
- [ ] Google Maps (for mock location verification)
- [ ] SW Maps or similar GIS app
- [ ] u-center (optional, for receiver configuration)

### NTRIP Configuration
- Host: ________________
- Port: ________________
- Mountpoint: ________________
- Username: ________________
- Password: ________________

## Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Developer | | | |
| QA | | | |
| Product Owner | | | |

## Issues Found

| # | Description | Severity | Status | Resolution |
|---|-------------|----------|--------|------------|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |

## References

- PRD: [docs/prd.md](../prd.md)
- Project Brief: [docs/brief.md](../brief.md)
