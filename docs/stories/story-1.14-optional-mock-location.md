# Story 1.14: Optional Mock Location with Warning - Brownfield Addition

## User Story

**As a** user of Bluetooth GNSS,
**I want** mock location activation to be optional rather than mandatory,
**So that** I can connect to my GNSS device even without mock location enabled (for testing, logging-only use cases, etc.), while still being warned about the implications.

## Story Context

**Existing System Integration:**

- Integrates with: `connect.dart` → `_checkUpdateSelectedDev()` function (line 609-623)
- Technology: Flutter/Dart with ValueNotifier reactive pattern
- Follows pattern: Existing checklist icon pattern (`iconOk`, `iconFail`)
- Touch points:
  - `lib/connect.dart` - check logic
  - `lib/utils_ui.dart` - icon definitions

## Acceptance Criteria

**Functional Requirements:**

1. Mock location check no longer blocks connection (removes early `return`)
2. When mock location is NOT enabled, display a **warning icon** (new `iconWarn`) in the checklist
3. Warning text clearly explains the implication: mock locations won't be injected to Android
4. When mock location IS enabled, continue showing `iconOk` as today

**Integration Requirements:**

5. Existing connection flow continues to work unchanged when mock location IS enabled
6. New warning icon follows existing icon pattern (`iconOk`/`iconFail` style)
7. "Next step" status message updates appropriately (not blocking message)

**Quality Requirements:**

8. No regression in existing functionality when mock location is enabled
9. Warning is clearly visible but not alarming (suggest orange/amber color)

## Technical Notes

**Implementation Approach:**

1. **Add warning icon** in `utils_ui.dart`:

```dart
const iconWarn = Icon(
  Icons.warning_amber_rounded,
  color: Colors.orange,
  size: defaultChecklistIconSize,
);
```

2. **Modify check in `connect.dart`** (~line 609-623):

```dart
if (!(await isMockLocationEnabled())) {
  // WARNING instead of blocking
  icon_map["Mock Location not enabled\n(GPS positions won't update Android location)"] = iconWarn;
  // DON'T return early - continue to ReadyToConnect
} else {
  icon_map["'Mock Location app' is 'Bluetooth GNSS'..."] = iconOk;
}
```

**Existing Pattern Reference:** Lines 530-537 in `connect.dart` show conditional icon assignment

**Key Constraints:**

- Must not break existing mock location functionality when enabled
- Warning text must be clear about what won't work

## Risk and Compatibility Check

**Minimal Risk Assessment:**

- **Primary Risk:** Users may connect without realizing mock location isn't working
- **Mitigation:** Clear warning text explaining the consequence
- **Rollback:** Revert the early `return` removal to restore mandatory behavior

**Compatibility Verification:**

- [x] No breaking changes to existing APIs
- [x] No database changes
- [x] UI changes follow existing design patterns (adding `iconWarn` alongside `iconOk`/`iconFail`)
- [x] Performance impact is negligible

## Definition of Done

- [x] `iconWarn` added to `utils_ui.dart`
- [x] Mock location check modified to warn instead of block
- [x] Warning text is clear and actionable
- [ ] Connection works when mock location is disabled (with warning) - *requires manual testing*
- [ ] Connection works when mock location is enabled (as before, with `iconOk`) - *requires manual testing*
- [ ] Manual testing of both scenarios passes

---

## Dev Agent Record

### Status
Ready for Manual Testing

### File List
| File | Change |
|------|--------|
| `lib/utils_ui.dart` | Added `iconWarn` constant (orange warning icon) |
| `lib/connect.dart` | Modified mock location check to warn instead of block |

### Change Log
- Added `iconWarn` Icon constant with `Icons.warning_amber_rounded` and orange color
- Changed `_checkUpdateSelectedDev()` to show warning instead of blocking when mock location is not enabled
- Removed early `return` that prevented connection without mock location
- Function now always proceeds to `ConnectState.ReadyToConnect`

### Completion Notes
- Flutter analyze passes with no issues
- Requires manual testing on device to verify:
  1. With mock location disabled: shows orange warning, allows connection
  2. With mock location enabled: shows blue checkmark as before
