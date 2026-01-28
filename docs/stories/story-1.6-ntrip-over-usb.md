# Story 1.6: NTRIP Over USB

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.6 |
| **Status** | Done |
| **Priority** | High |
| **Dependencies** | Story 1.5 |

## User Story

**As a** user,
**I want** NTRIP correction data sent to my USB-connected RTK receiver,
**so that** I get centimeter-level accuracy via USB connection.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | NTRIP connection can be established while USB GNSS connection is active | Done |
| AC2 | RTCM correction data from NTRIP is written to USB `OutputStream` | Done |
| AC3 | GGA sentences are sent to NTRIP server (for VRS) using USB-received position | Done |
| AC4 | NTRIP byte counters update correctly for USB connections | Done |
| AC5 | NTRIP reconnection logic works with USB connection (same as Bluetooth) | Done |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | NTRIP over Bluetooth continues to work unchanged | Done |
| IV2 | RTK fix achieved with ZED-F9P via USB + NTRIP | Done |
| IV3 | NTRIP disable/enable settings apply to USB connections | Done |

## Technical Notes

### Files to Modify

1. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`**
   - Modify NTRIP data routing to use active connection's OutputStream
   - Ensure GGA sentences use position from USB if USB is active

### NTRIP Integration Pattern

The existing NTRIP implementation writes RTCM data to `m_outgoing_buffers` queue, which is then written to the Bluetooth OutputStream. For USB, we need to route to the USB OutputStream instead.

```java
// Current implementation uses queue_to_outputstream_writer_thread
// Modify to write to active connection's OutputStream

private OutputStream getActiveOutputStream() {
    if (m_connection_type == ConnectionType.USB_SERIAL && g_usb_mgr != null) {
        return g_usb_mgr.getOutputStream();
    } else if (g_rfcomm_mgr != null) {
        return g_rfcomm_mgr.getOutputStream();
    }
    return null;
}
```

### NTRIP Callback Modification

```java
// In ntrip_conn_callbacks implementation
@Override
public void on_ntrip_data_received(byte[] rtcmData) {
    m_ntrip_cb_count++;

    OutputStream os = getActiveOutputStream();
    if (os != null) {
        try {
            os.write(rtcmData);
            os.flush();
            m_ntrip_cb_count_added_to_send_buffer++;
        } catch (IOException e) {
            Log.e(TAG, "Error writing NTRIP data: " + e.getMessage());
        }
    }
}
```

### GGA Sentence Routing

The existing code sends GGA sentences to NTRIP server for VRS (Virtual Reference Station) positioning. This should work unchanged since GGA is generated from parsed position data, regardless of whether it came from USB or Bluetooth.

```java
// Existing GGA sending logic - no changes needed
// GGA is generated from m_last_parsed_location which is set by either USB or BT data
private void sendGgaToNtrip() {
    if (m_ntrip_conn_mgr != null && m_send_gga_to_ntrip && m_last_gga_sentence != null) {
        m_ntrip_conn_mgr.sendGga(m_last_gga_sentence);
    }
}
```

### Connection Type Awareness

```java
// Update NTRIP status display to show connection type
public String getNtripStatus() {
    String connType = (m_connection_type == ConnectionType.USB_SERIAL) ? "USB" : "BT";
    return String.format("NTRIP via %s: %d bytes received, %d bytes sent to receiver",
        connType, m_ntrip_bytes_received, m_ntrip_bytes_sent);
}
```

### Testing Checklist

- [ ] Connect USB + configure NTRIP - NTRIP connection establishes
- [ ] RTCM data received - byte counter increments
- [ ] RTCM data sent to receiver - sent counter increments
- [ ] ZED-F9P achieves RTK FIX (verify in app status or u-center)
- [ ] Disable NTRIP - data flow stops
- [ ] NTRIP reconnect after network drop - works with USB connection
- [ ] Bluetooth + NTRIP still works (regression test)

### RTK Fix Verification

To verify RTK is working:
1. Check GGA sentence quality indicator:
   - `1` = GPS fix
   - `4` = RTK fixed
   - `5` = RTK float
2. Check position accuracy (should be <10cm with RTK fix)
3. App should display fix type in status

## References

- [ntrip_conn_mgr.java](../../android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/ntrip_conn_mgr.java)
- PRD: [docs/prd.md](../prd.md)

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### File List

| File | Action |
|------|--------|
| `android/.../bluetooth_gnss_service.java` | Modified - NTRIP callback sends data to USB via `g_usb_mgr.add_send_buffer()`, `m_all_ntrip_params_specified` flag set for USB connections |
| `android/.../usb_conn_mgr.java` | Modified - Added `add_send_buffer()` method, output queue (`ConcurrentLinkedQueue`), and writer thread |
| `android/.../queue_to_outputstream_writer_thread.java` | Created - Worker thread that polls queue and writes to OutputStream with flush() |
| `android/.../MainActivity.java` | Modified - Pass all NTRIP parameters from Flutter instead of hardcoding `disable_ntrip=true` |
| `lib/connect.dart` | Modified - `connectUsb()` passes full connection params including NTRIP settings |
| `lib/channels.dart` | Modified - `connectUsb()` accepts `connectionParams` map |

### Completion Notes

- AC1: NTRIP connection established when USB connection is active - `m_all_ntrip_params_specified` flag initialized for USB path
- AC2: RTCM data routed to USB via `g_usb_mgr.add_send_buffer(read_buff)` in NTRIP `on_read()` callback
- AC3: GGA sentences use same mechanism (`m_last_gga_sentence`) regardless of connection type - works unchanged
- AC4: `m_ntrip_cb_count` and `m_ntrip_cb_count_added_to_send_buffer` counters increment for USB writes
- AC5: Same `ntrip_conn_mgr` handles reconnection logic for both BT and USB connections
- IV1: Bluetooth NTRIP path unchanged - `g_rfcomm_mgr.add_send_buffer()` still used
- IV2: User verified RTK fix achieved with USB + NTRIP (reported "Everything works")
- IV3: NTRIP settings passed from Flutter to Android for USB connections

### Change Log

| Date | Change |
|------|--------|
| 2026-01-28 | Implementation completed as part of Story 1.7 bug fixes |
