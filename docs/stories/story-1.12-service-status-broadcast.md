# Story 1.12: Connection Error Broadcast - Brownfield Addition

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | External Integration Support |
| **Story ID** | 1.12 |
| **Type** | Brownfield Enhancement |
| **Status** | Ready for Testing |
| **Priority** | Medium |
| **Dependencies** | Story 1.11 (Disconnect Broadcast) |

## User Story

**As a** third-party app developer launching bluetooth_gnss service,
**I want** to receive a POSITION_UPDATE broadcast with `fix_status: "Invalid"` when connection fails,
**So that** my app can react to connection failures without needing the bluetooth_gnss UI.

## Story Context

### Existing System Integration

| Aspect | Details |
|--------|---------|
| **Integrates with** | `bluetooth_gnss_service.java` error handling and connection flow |
| **Technology** | Android Intent broadcast with JSON payload |
| **Follows pattern** | Story 1.11 disconnect broadcast mechanism |
| **Touch points** | USB connect catch block, BT connect catch block |

### Current Behavior

When connection fails:
- Logged to logcat
- Toast shown (only visible if bluetooth_gnss UI is in foreground)
- Notification updated
- **No broadcast sent** - third-party apps have no way to know about connection failures

### Enhanced Behavior

On connection failure, broadcast a POSITION_UPDATE with `fix_status: "Invalid"` (same format as story 1.11 disconnect broadcast):
```json
{
  "java_ts": 1704067200000,
  "system_ts": 1704067200000,
  "gnss_ts": 0,
  "latitude": 0.0,
  "longitude": 0.0,
  "altitude": 0.0,
  "accuracy": 0.0,
  "vertical_accuracy": 0.0,
  "fix_status": "Invalid",
  "bearing": 0.0,
  "speed_m_s": 0.0,
  "n_sats": 0
}
```

This reuses the `broadcastDisconnect()` method from story 1.11. Third-party apps can use the same broadcast receiver and check `fix_status: "Invalid"` to detect both disconnections and connection failures.

## Acceptance Criteria

### Functional Requirements

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | USB connection failure triggers POSITION_UPDATE broadcast with `fix_status: "Invalid"` | Done |
| AC2 | Bluetooth connection failure triggers POSITION_UPDATE broadcast with `fix_status: "Invalid"` | Done |
| AC3 | Broadcast uses same format as story 1.11 (same fields as normal position updates) | Done |

### Integration Requirements

| # | Criterion | Status |
|---|-----------|--------|
| IR1 | Existing POSITION_UPDATE broadcast format unchanged for normal position updates | Done |
| IR2 | Existing error handling (toast, notification) unchanged | Done |
| IR3 | Reuses story 1.11's `broadcastDisconnect()` method | Done |

## Technical Notes

### Implementation Approach

Reuse the `broadcastDisconnect(String reason)` method from story 1.11 and call it from connection error handlers:

```java
// In USB connect catch block
} catch (final Exception e) {
    log(TAG, "USB connect exception: " + getStackTraceString(e));
    broadcastDisconnect("USB Connect failed: " + e.getMessage());
    // ... existing toast and notification code
}

// In BT connect catch block
} catch (final Exception e) {
    broadcastDisconnect("Connect failed: " + e.toString());
    // ... existing toast and notification code
}
```

### Integration Points

| Location | Event | Broadcast |
|----------|-------|-----------|
| USB connect catch block | USB connection failed | `fix_status: "Invalid"` |
| BT connect catch block | Bluetooth connection failed | `fix_status: "Invalid"` |

## Third-Party App Usage

```java
// Register receiver (same as story 1.11)
IntentFilter filter = new IntentFilter();
filter.addAction("com.clearevo.libbluetooth_gnss_service.POSITION_UPDATE");
registerReceiver(myReceiver, filter);

// Handle in receiver
@Override
public void onReceive(Context context, Intent intent) {
    String json = intent.getStringExtra("data_json");
    JSONObject data = new JSONObject(json);

    String fixStatus = data.optString("fix_status", "");
    if ("Invalid".equals(fixStatus)) {
        // Connection failed or disconnected - no valid position available
        // Update UI to show "No GPS" or similar
    } else {
        // Valid position update
        double lat = data.getDouble("latitude");
        double lon = data.getDouble("longitude");
        // ... use position data
    }
}
```

## Definition of Done

- [x] USB connection failure broadcasts Invalid fix status
- [x] Bluetooth connection failure broadcasts Invalid fix status
- [x] Reuses existing `broadcastDisconnect()` method
- [ ] Tested with broadcast receiver
- [ ] No regression in existing functionality

## Test Plan

### Manual Tests

| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| USB device not available | Broadcast received with `fix_status: "Invalid"` | |
| Bluetooth device not found | Broadcast received with `fix_status: "Invalid"` | |
| Bluetooth device out of range | Broadcast received with `fix_status: "Invalid"` | |
| Normal position updates | `fix_status` contains actual fix quality (RTK, DGPS, etc.) - unchanged | |

## Tasks

- [x] Task 1: Add `broadcastDisconnect()` call to USB connect catch block
- [x] Task 2: Add `broadcastDisconnect()` call to BT connect catch block
- [ ] Task 3: Test all error broadcast scenarios

## References

- Service file: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`
- Story 1.11: [story-1.11-disconnect-broadcast.md](story-1.11-disconnect-broadcast.md)
- Broadcast action: `com.clearevo.libbluetooth_gnss_service.POSITION_UPDATE`

---

## Dev Agent Record

### Agent Model Used
- Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List
| File | Action | Description |
|------|--------|-------------|
| `android/.../bluetooth_gnss_service.java` | Modified | Added `broadcastDisconnect()` calls to USB and BT connect error handlers |

### Implementation Details

**Reused `broadcastDisconnect(String reason)` method from story 1.11**

**Integration points added:**
- USB connect catch block (line ~405): `broadcastDisconnect("USB Connect failed: " + e.getMessage())`
- BT connect catch block (line ~1134): `broadcastDisconnect("Connect failed: " + e.toString())`

### Change Log
| Date | Change |
|------|--------|
| 2026-01-29 | Story created |
| 2026-01-29 | Story simplified - reuse POSITION_UPDATE with Invalid fix_status instead of separate SERVICE_STATUS |
| 2026-01-29 | Implementation complete - added broadcastDisconnect calls to connection error handlers |
