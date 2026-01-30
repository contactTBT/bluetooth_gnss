# Story 1.13: USB Auto-Reconnect on App Startup

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Device Support |
| **Story ID** | 1.13 |
| **Type** | Brownfield Enhancement |
| **Status** | Ready for Testing |
| **Priority** | High |
| **Dependencies** | USB VID/PID saving (already implemented in Android) |

## User Story

**As a** user with a USB GNSS device,
**I want** the app to remember my last USB device and auto-reconnect on app startup,
**So that** I don't have to manually select the device every time I open the app.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | When a USB device is selected, save its VID/PID to Flutter preferences | Done |
| AC2 | On app startup, if last device was USB, find connected device by VID/PID | Done |
| AC3 | If matching USB device found, auto-select it in the device dropdown | Done |
| AC4 | Device should be ready to connect without manual selection | Done |

## Technical Notes

### Current State
- Android side already saves VID/PID via `Util.save_connect_args()` when connecting USB
- Flutter uses separate SharedPreferences for device selection (`target_device`)
- USB devices are identified by `usb_$deviceId` but deviceId changes on replug
- VID/PID are stable identifiers that work across replugs

### Implementation Approach

1. **Modify `setSelectedDevice()` in connect.dart**
   - When saving a USB device, also save `usb_vendor_id` and `usb_product_id` to preferences

2. **Modify `getSelectedDevice()` in connect.dart**
   - If `target_device` starts with `usb_`, check saved VID/PID
   - Find matching device in current USB device list by VID/PID
   - Return that device (even if deviceId changed)

3. **Modify `loadUnifiedDeviceList()` in connect.dart**
   - After loading, call auto-select logic for USB devices

## Tasks

- [x] Task 1: Add VID/PID saving to `setSelectedDevice()` when USB device is selected
- [x] Task 2: Update `getSelectedDevice()` to match USB devices by VID/PID instead of deviceId
- [x] Task 3: Add auto-select logic after `loadUnifiedDeviceList()`
- [ ] Task 4: Test USB reconnection flow

## Definition of Done

- [x] USB device VID/PID saved to Flutter preferences on selection
- [x] USB device matched by VID/PID on app startup (not by deviceId)
- [x] USB device auto-selected in dropdown if connected
- [x] App ready to connect without manual device selection

## Files to Modify

| File | Action | Description |
|------|--------|-------------|
| `lib/connect.dart` | Modified | Add VID/PID saving and matching logic |

---

## Dev Agent Record

### Agent Model Used
- Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List
| File | Action | Description |
|------|--------|-------------|
| `lib/connect.dart` | Modified | Added VID/PID saving/matching for USB auto-reconnect |

### Implementation Details

**`setSelectedDevice()` changes:**
- When USB device is selected, saves `usb_vendor_id` and `usb_product_id` to preferences
- Clears USB IDs when Bluetooth device is selected

**`getSelectedDevice()` changes:**
- If `target_device` starts with `usb_`, matches by VID/PID instead of deviceId
- Finds connected USB device with matching VID/PID
- Returns device even if deviceId changed (due to replug)

**`loadUnifiedDeviceList()` changes:**
- After loading device list, calls `getSelectedDevice()` to auto-select
- Updates `selectedDeviceNotifier` with matched device
- Updates `target_device` pref with new device ID (in case it changed)

### Change Log
| Date | Change |
|------|--------|
| 2026-01-30 | Story created |
| 2026-01-30 | Implementation complete - VID/PID saving and matching |
