# Story 1.10: Enhanced Broadcast Fields - Brownfield Addition

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | External Integration Support |
| **Story ID** | 1.10 |
| **Type** | Brownfield Enhancement |
| **Status** | Ready for Review |
| **Priority** | Medium |
| **Dependencies** | None (uses existing broadcast infrastructure) |

## User Story

**As a** third-party app developer integrating with bluetooth_gnss,
**I want** the POSITION_UPDATE broadcast to include fix status and vertical accuracy,
**So that** my app can display comprehensive position quality information to users.

## Story Context

### Existing System Integration

| Aspect | Details |
|--------|---------|
| **Integrates with** | `bluetooth_gnss_service.java` POSITION_UPDATE broadcast |
| **Technology** | Android Intent broadcast with JSON payload |
| **Follows pattern** | Existing `jo.put()` calls with try/catch wrapping (lines 1636-1645) |
| **Touch points** | Single location: broadcast construction in `setMock()` method |

### Current Broadcast Structure

The `POSITION_UPDATE` broadcast currently includes:
```json
{
  "java_ts": 1704067200000,
  "system_ts": 1704067200000,
  "gnss_ts": 1704067199500,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "altitude": 52.5,
  "accuracy": 5.2,
  "bearing": 45.0,
  "speed_m_s": 2.5,
  "n_sats": 10
}
```

### Enhanced Broadcast Structure

After implementation:
```json
{
  "java_ts": 1704067200000,
  "system_ts": 1704067200000,
  "gnss_ts": 1704067199500,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "altitude": 52.5,
  "accuracy": 5.2,
  "vertical_accuracy": 3.1,
  "fix_status": "RTK",
  "bearing": 45.0,
  "speed_m_s": 2.5,
  "n_sats": 10
}
```

## Acceptance Criteria

### Functional Requirements

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `fix_status` field added to POSITION_UPDATE broadcast JSON | |
| AC2 | `vertical_accuracy` field added to POSITION_UPDATE broadcast JSON | |
| AC3 | Fix status reflects GGA fix quality (e.g., "No fix", "GPS fix (SPS)", "DGPS", "RTK", "FloatRTK") | |
| AC4 | Vertical accuracy uses u-blox `vAcc` when available, falls back to `vdop × CEP` calculation | |

### Integration Requirements

| # | Criterion | Status |
|---|-----------|--------|
| IR1 | Existing broadcast fields remain unchanged | |
| IR2 | New fields follow existing `jo.put()` pattern with try/catch | |
| IR3 | Broadcast timing and frequency unchanged | |
| IR4 | PARSED_NMEA_UPDATE broadcast remains unaffected | |

### Quality Requirements

| # | Criterion | Status |
|---|-----------|--------|
| QR1 | No regression in existing broadcast consumers | |
| QR2 | Fields gracefully handle missing data (null/NaN scenarios) | |
| QR3 | Performance impact negligible (two additional JSON fields) | |

## Technical Notes

### Data Sources

| Field | Primary Source | Fallback | Key in params_map |
|-------|---------------|----------|-------------------|
| `fix_status` | GGA sentence | None | `{talker}_fix_quality` |
| `vertical_accuracy` | u-blox PUBX,00 | `vdop × CEP` | `UBX_POSITION_vAcc` or calculated |

### Implementation Location

**File:** `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`

**Modification Point:** Lines 1636-1645 (POSITION_UPDATE broadcast construction)

### Code Pattern to Follow

```java
// Existing pattern (lines 1636-1645):
try {jo.put("accuracy", accuracy);} catch (Exception e) {}

// Add similarly:
try {jo.put("fix_status", fix_status);} catch (Exception e) {}
try {jo.put("vertical_accuracy", vaccuracy);} catch (Exception e) {}
```

### Data Availability

Both values are already extracted in `onPositionUpdate()` method:
- `fix_status`: Available from `params_map.get(talker + "_fix_quality")`
- `vaccuracy`: Already calculated at lines 1982-1983 (u-blox or vdop×CEP fallback)

## Risk and Compatibility Check

### Minimal Risk Assessment

| Aspect | Details |
|--------|---------|
| **Primary Risk** | Consumers not expecting new fields |
| **Mitigation** | New fields are additive; JSON consumers typically ignore unknown fields |
| **Rollback** | Remove two `jo.put()` lines |

### Compatibility Verification

| # | Check | Status |
|---|-------|--------|
| CV1 | No breaking changes to existing broadcast fields | |
| CV2 | JSON structure remains backward compatible (additive only) | |
| CV3 | No changes to broadcast action or intent structure | |
| CV4 | Performance impact negligible | |

## Definition of Done

- [x] `fix_status` field added to POSITION_UPDATE broadcast
- [x] `vertical_accuracy` field added to POSITION_UPDATE broadcast
- [x] Data correctly sourced from existing parsed values
- [x] Existing broadcast fields unchanged
- [x] Code follows existing pattern (try/catch wrapped jo.put)
- [ ] Tested with broadcast receiver (verify JSON output)
- [ ] No regression in mock location functionality

## Test Plan

### Unit Tests

| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| Broadcast with RTK fix | `fix_status` = "RTK", `vertical_accuracy` populated | |
| Broadcast with DGPS fix | `fix_status` = "DGPS", `vertical_accuracy` populated | |
| Broadcast with no fix | `fix_status` = "No fix" or absent | |
| Broadcast without u-blox | `vertical_accuracy` = vdop × CEP fallback | |

### Integration Tests

| Test Case | Expected Result | Status |
|-----------|-----------------|--------|
| Existing app compatibility | Apps ignoring new fields continue working | |
| New fields in broadcast monitor | Both fields visible in broadcast JSON | |

## Implementation Estimate

**Scope:** ~10 lines of code change in single file
**Risk:** Low (additive change, follows existing pattern)
**Complexity:** Minimal (data already available, just needs to be added to broadcast)

## Tasks

- [x] Task 1: Add `fix_status` field to POSITION_UPDATE broadcast JSON
- [x] Task 2: Add `vertical_accuracy` field to POSITION_UPDATE broadcast JSON
- [x] Task 3: Verify data is correctly sourced from existing parsed values
- [ ] Task 4: Test broadcast output contains new fields

## References

- Service file: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java`
- Parser file: `android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/gnss_sentence_parser.java`
- Broadcast action: `com.dnablast.libbluetooth_gnss_service.POSITION_UPDATE`

---

## Dev Agent Record

### Agent Model Used
- Claude Opus 4.5

### File List
| File | Action | Description |
|------|--------|-------------|
| android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/bluetooth_gnss_service.java | Modified | Added fix_quality parameter to setMock(), added vertical_accuracy and fix_status to broadcast JSON |

### Change Log
| Change | Details |
|--------|---------|
| Added fix_quality parameter to setMock() | Line 1506: Method signature extended with `String fix_quality` |
| Added vertical_accuracy to broadcast | Line 1646: `jo.put("vertical_accuracy", vaccuracy)` |
| Added fix_status to broadcast | Line 1647: `jo.put("fix_status", fix_quality)` |
| Updated QSTARZ_BLE caller | Lines 1081-1083: Extract fix_quality_matched and pass to setMock |
| Updated NMEA caller | Lines 1989-1991: Extract talker_fix_quality and pass to setMock |

### Debug Log References
- None

### Completion Notes
- Implementation complete - code changes follow existing patterns exactly
- Both NMEA and QSTARZ_BLE paths updated to pass fix_quality
- Manual testing required to verify broadcast output (Task 4)
