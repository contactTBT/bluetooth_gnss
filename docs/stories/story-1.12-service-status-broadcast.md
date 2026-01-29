# Story 1.12: Service Status Broadcast - Brownfield Addition

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | External Integration Support |
| **Story ID** | 1.12 |
| **Type** | Brownfield Enhancement |
| **Status** | Ready |
| **Priority** | Medium |
| **Dependencies** | Story 1.11 (Disconnect Broadcast) |

## User Story

**As a** third-party app developer launching bluetooth_gnss service,
**I want** to receive broadcast notifications about service status changes and errors,
**So that** my app can react to connection failures, NTRIP errors, and other service events without needing the bluetooth_gnss UI.

## Story Context

### Existing System Integration

| Aspect | Details |
|--------|---------|
| **Integrates with** | `bluetooth_gnss_service.java` error handling and connection flow |
| **Technology** | Android Intent broadcast with JSON payload |
| **Follows pattern** | Existing POSITION_UPDATE broadcast mechanism |
| **Touch points** | Connection handlers, error handlers, NTRIP connection |

### Current Behavior

When errors occur:
- Logged to logcat
- Toast shown (only visible if bluetooth_gnss UI is in foreground)
- Notification updated
- **No broadcast sent** - third-party apps have no way to know about failures

### Enhanced Behavior

Broadcast a SERVICE_STATUS intent for key events:
```json
{
  "status": "error",
  "error_type": "connection",
  "message": "Connect failed: device not found",
  "timestamp": 1704067200000
}
```

## Acceptance Criteria

### Functional Requirements

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | New `SERVICE_STATUS` broadcast intent action defined | |
| AC2 | Broadcast sent on successful connection with `status: "connected"` | |
| AC3 | Broadcast sent on connection failure with `status: "error"` and error details | |
| AC4 | Broadcast sent on NTRIP connection success/failure | |
| AC5 | Broadcast sent when service starts with `status: "connecting"` | |

### Status Values

| Status | When Sent |
|--------|-----------|
| `connecting` | Service started, attempting connection |
| `connected` | Successfully connected to GNSS device |
| `error` | Connection or NTRIP failure |
| `ntrip_connected` | NTRIP caster connection established |
| `ntrip_error` | NTRIP connection failed |
| `disconnected` | Device disconnected (complement to story 1.11) |

### Error Types

| Error Type | Description |
|------------|-------------|
| `connection` | Bluetooth/USB connection failure |
| `permission` | Missing permissions |
| `ntrip` | NTRIP caster connection failure |
| `device_not_found` | Target device not found |
| `service` | General service error |

### Integration Requirements

| # | Criterion | Status |
|---|-----------|--------|
| IR1 | Existing POSITION_UPDATE broadcast unchanged | |
| IR2 | Existing error handling (toast, notification) unchanged | |
| IR3 | Third-party apps can register for SERVICE_STATUS broadcast | |

## Technical Notes

### Implementation Approach

Add a new broadcast intent action and helper method:

```java
// New intent action
public static final String SERVICE_STATUS_INTENT_ACTION =
    "com.clearevo.libbluetooth_gnss_service.SERVICE_STATUS";

// Helper method
private void broadcastServiceStatus(String status, String errorType, String message) {
    try {
        Intent intent = new Intent();
        intent.setAction(SERVICE_STATUS_INTENT_ACTION);
        JSONObject jo = new JSONObject();
        long ts = System.currentTimeMillis();
        try { jo.put("status", status); } catch (Exception e) {}
        try { jo.put("timestamp", ts); } catch (Exception e) {}
        if (errorType != null) {
            try { jo.put("error_type", errorType); } catch (Exception e) {}
        }
        if (message != null) {
            try { jo.put("message", message); } catch (Exception e) {}
        }
        intent.putExtra(INTENT_EXTRA_DATA_JSON_KEY, jo.toString());
        getApplicationContext().sendBroadcast(intent);
        log(TAG, "broadcastServiceStatus: " + status + ", " + errorType + ", " + message);
    } catch (Throwable tr) {
        log(TAG, "WARNING: broadcastServiceStatus failed: " + getStackTraceString(tr));
    }
}
```

### Integration Points

| Location | Event | Status | Error Type |
|----------|-------|--------|------------|
| `onStartCommand()` | Service starting | `connecting` | - |
| After successful BT connect | Connected | `connected` | - |
| After successful USB connect | Connected | `connected` | - |
| BT connect catch block | Failure | `error` | `connection` |
| USB connect catch block | Failure | `error` | `connection` |
| `connect_ntrip()` success | NTRIP connected | `ntrip_connected` | - |
| `connect_ntrip()` failure | NTRIP failed | `ntrip_error` | `ntrip` |
| `on_usb_error()` | USB error | `error` | `connection` |

### Broadcast JSON Examples

**Connecting:**
```json
{
  "status": "connecting",
  "timestamp": 1704067200000
}
```

**Connected:**
```json
{
  "status": "connected",
  "timestamp": 1704067200000
}
```

**Connection Error:**
```json
{
  "status": "error",
  "error_type": "connection",
  "message": "Connect failed: device not found",
  "timestamp": 1704067200000
}
```

**NTRIP Connected:**
```json
{
  "status": "ntrip_connected",
  "timestamp": 1704067200000
}
```

**NTRIP Error:**
```json
{
  "status": "ntrip_error",
  "error_type": "ntrip",
  "message": "NTRIP connection failed: host unreachable",
  "timestamp": 1704067200000
}
```

## Third-Party App Usage

```java
// Register receiver
IntentFilter filter = new IntentFilter();
filter.addAction("com.clearevo.libbluetooth_gnss_service.SERVICE_STATUS");
filter.addAction("com.clearevo.libbluetooth_gnss_service.POSITION_UPDATE");
registerReceiver(myReceiver, filter);

// Handle in receiver
@Override
public void onReceive(Context context, Intent intent) {
    String action = intent.getAction();
    String json = intent.getStringExtra("data_json");
    JSONObject data = new JSONObject(json);

    if ("SERVICE_STATUS".equals(action)) {
        String status = data.getString("status");
        if ("error".equals(status)) {
            String errorType = data.optString("error_type");
            String message = data.optString("message");
            // Handle error
        } else if ("connected".equals(status)) {
            // Connection successful
        }
    }
}
```

## Definition of Done

- [ ] `SERVICE_STATUS_INTENT_ACTION` constant defined
- [ ] `broadcastServiceStatus()` helper method implemented
- [ ] Broadcast sent on service start (`connecting`)
- [ ] Broadcast sent on successful connection (`connected`)
- [ ] Broadcast sent on connection failure (`error`)
- [ ] Broadcast sent on NTRIP success/failure
- [ ] Broadcast sent on USB error
- [ ] Tested with broadcast receiver
- [ ] No regression in existing functionality

## Test Plan

### Manual Tests

| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| Start service | Broadcast `status: "connecting"` received | |
| Successful BT connection | Broadcast `status: "connected"` received | |
| Successful USB connection | Broadcast `status: "connected"` received | |
| BT device not found | Broadcast `status: "error"`, `error_type: "connection"` received | |
| NTRIP connection success | Broadcast `status: "ntrip_connected"` received | |
| NTRIP connection failure | Broadcast `status: "ntrip_error"` received | |
| USB error | Broadcast `status: "error"` received | |

## Tasks

- [ ] Task 1: Define `SERVICE_STATUS_INTENT_ACTION` constant
- [ ] Task 2: Create `broadcastServiceStatus()` helper method
- [ ] Task 3: Add broadcast on service start (`connecting`)
- [ ] Task 4: Add broadcast on successful connection (`connected`)
- [ ] Task 5: Add broadcast on connection failure (`error`)
- [ ] Task 6: Add broadcast on NTRIP events
- [ ] Task 7: Add broadcast on USB error
- [ ] Task 8: Test all broadcast scenarios

## References

- Service file: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`
- Story 1.11: [story-1.11-disconnect-broadcast.md](story-1.11-disconnect-broadcast.md)
- Broadcast action: `com.clearevo.libbluetooth_gnss_service.SERVICE_STATUS`

---

## Dev Agent Record

### Agent Model Used
- (To be filled on implementation)

### File List
| File | Action | Description |
|------|--------|-------------|
| (To be filled on implementation) | | |

### Change Log
| Date | Change |
|------|--------|
| 2026-01-29 | Story created |
