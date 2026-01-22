# Story 1.1: Project Setup & Dependency Integration

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.1 |
| **Status** | Draft |
| **Priority** | High |
| **Dependencies** | None |

## User Story

**As a** developer,
**I want** the USB serial library integrated and permissions configured,
**so that** I have the foundation to build USB connectivity features.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | `usb-serial-for-android` library (v3.7.0+) is added to `build.gradle` dependencies | |
| AC2 | `AndroidManifest.xml` includes `<uses-feature android:name="android.hardware.usb.host" android:required="false" />` | |
| AC3 | USB intent filter is configured for device attachment events | |
| AC4 | Project builds successfully with `flutter build apk` | |
| AC5 | Library classes are accessible from Java code (import compiles) | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Existing Bluetooth pairing and connection flow works unchanged | |
| IV2 | App launches and runs normally on devices without USB-OTG | |
| IV3 | APK size increase is <500KB | |

## Technical Notes

### Files to Modify

1. **`android/app/build.gradle`**
   - Add dependency: `implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'`
   - May need to add JitPack repository if not present

2. **`android/app/src/main/AndroidManifest.xml`**
   - Add USB host feature declaration
   - Add intent filter for USB device attachment

### Implementation Guidance

```gradle
// In build.gradle (app level)
dependencies {
    implementation 'com.github.mik3y:usb-serial-for-android:3.7.0'
}
```

```xml
<!-- In AndroidManifest.xml -->
<uses-feature android:name="android.hardware.usb.host" android:required="false" />

<activity android:name=".MainActivity" ...>
    <intent-filter>
        <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
    </intent-filter>
    <meta-data
        android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
        android:resource="@xml/device_filter" />
</activity>
```

### Testing Checklist

- [ ] Run `flutter build apk` - builds without errors
- [ ] Install on device, launch app - no crashes
- [ ] Test Bluetooth connection - works as before
- [ ] Check APK size increase

## References

- [usb-serial-for-android GitHub](https://github.com/mik3y/usb-serial-for-android)
- [Android USB Host API](https://developer.android.com/develop/connectivity/usb/host)
- PRD: [docs/prd.md](../prd.md)
