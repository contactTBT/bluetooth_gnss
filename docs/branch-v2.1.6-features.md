# Branch v2.1.6 Features - Merge Guide

**Branch:** `branch_v2.1.6`
**Base commit:** `5a18e4c` (common ancestor with master)
**Total commits:** 22

---

## Feature Summary

### 1. USB GNSS Device Support (Stories 1.1-1.8)
**Purpose:** Enable connection to GNSS receivers via USB serial (in addition to Bluetooth)

| Commit | Description |
|--------|-------------|
| `ec206a5` | USB permissions & device filter in AndroidManifest |
| `31e426f` | USB device detection via `UsbDeviceManager.java` |
| `be1fe9e` | USB connection manager (`usb_conn_mgr.java`) |
| `214af49` | Service integration, unified device list UI |
| `51dacfe` | Full working USB connection with NTRIP support |
| `3bbd57b` | Disconnect handling for USB devices |

**Key files:**
- `android/.../UsbDeviceManager.java` - USB device enumeration
- `android/.../usb_conn_mgr.java` - USB serial connection logic
- `android/.../usb_conn_callbacks.java` - Callback interface
- `lib/gnss_device.dart` - Unified device model (BT + USB)
- `lib/channels.dart` - Flutter platform channels for USB
- `lib/connect.dart` - UI for device selection

---

### 2. Enhanced Location Broadcasts (Stories 1.10-1.12)
**Purpose:** Provide richer GNSS data to external apps via Android broadcasts

| Commit | Description |
|--------|-------------|
| `2c4d49f` | Add fix status & Z accuracy to broadcast intent |
| `4e4acc1` | Improved broadcast message format |
| `c0c8847` | Disconnect notification broadcast |
| `513eab4` | Service status broadcast (running/stopped) |

**Key files:**
- `android/.../bluetooth_gnss_service.java` - Broadcast logic

---

### 3. USB Auto-Reconnect (Story 1.13)
**Purpose:** Automatically reconnect to last used USB device on startup

| Commit | Description |
|--------|-------------|
| `ae2f437` | Store last USB device, auto-reconnect on launch |

**Key files:**
- `lib/connect.dart` - Auto-reconnect UI logic
- `android/.../UsbDeviceManager.java` - Device persistence

---

### 4. UX & Stability Improvements

| Commit | Description |
|--------|-------------|
| `9203dcd` | **Optional mock location** - Users can disable mock location activation |
| `c557a17` | **Fix focus lost** - Keyboard focus no longer lost on each keystroke |
| `ede84b6` | **Debounce args edition** - Prevents rapid repeated saves |
| `2c70797` | **Exception handling** - Service start gracefully handles missing params |
| `90bedec` | **Default NTRIP server** - Pre-populated NTRIP server value |
| `0e26279` | **Fix save USB args** - USB connection args now persist correctly |

---

### 5. Branding & Package Changes

| Commit | Description |
|--------|-------------|
| `41fadb6` | **Package rename** - Changed Android package name (affects all Java files) |
| `d0031c4` | **Notification icon** - Custom `ic_stat_notify.png` for foreground service |

**Key files:**
- `android/app/build.gradle` - applicationId change
- `android/.../res/drawable-*/ic_stat_notify.png` - Icon assets

---

### 6. Project Infrastructure (BMAD Framework)
**Purpose:** Claude Code AI assistant configuration & documentation framework

| Commit | Description |
|--------|-------------|
| `f7f91d7` | BMAD framework, stories, architecture docs, PRD |
| `fe1fdcc` | Rust builder setup, dependency updates |

**Key directories:**
- `.bmad-core/` - AI agent configuration
- `.claude/` - Claude Code commands
- `docs/` - PRD, architecture, stories

---

## How to Re-Apply Features to Master

### Option A: Cherry-Pick by Feature Group

```bash
# 1. USB Support (core feature)
git cherry-pick ec206a5 31e426f be1fe9e 214af49 51dacfe 3bbd57b

# 2. Broadcast Enhancements
git cherry-pick 2c4d49f 4e4acc1 c0c8847 513eab4

# 3. USB Auto-Reconnect
git cherry-pick ae2f437

# 4. UX Fixes
git cherry-pick 90bedec ede84b6 c557a17 0e26279 2c70797 9203dcd

# 5. Branding (apply last - affects many files)
git cherry-pick 41fadb6 d0031c4

# 6. Infrastructure (optional - only if needed)
git cherry-pick fe1fdcc f7f91d7
```

### Option B: Merge Branch

```bash
git checkout master
git merge branch_v2.1.6
# Resolve conflicts if any
```

### Option C: Rebase onto Master

```bash
git checkout branch_v2.1.6
git rebase master
# Resolve conflicts incrementally
git checkout master
git merge branch_v2.1.6
```

---

## Conflict Risk Areas

| Area | Risk | Notes |
|------|------|-------|
| Package name (`41fadb6`) | HIGH | Touches 52 files - apply separately |
| `bluetooth_gnss_service.java` | MEDIUM | Many modifications across commits |
| `lib/connect.dart` | MEDIUM | UI changes accumulated |
| `.bmad-core/` | LOW | New directory, no conflicts expected |

---

## Recommended Approach

1. **Start fresh branch from master**
2. **Cherry-pick USB support first** (stories 1.1-1.8) - this is the main feature
3. **Add broadcast enhancements** (stories 1.10-1.12)
4. **Add UX fixes** individually
5. **Apply branding changes last** (package rename is invasive)
6. **Skip BMAD infrastructure** unless needed for AI-assisted development

---

## Testing Checklist

After merging, verify:
- [ ] USB device detection works
- [ ] USB connection establishes
- [ ] NTRIP over USB functions
- [ ] Bluetooth still works (regression)
- [ ] Broadcasts received by external apps
- [ ] Auto-reconnect on app restart
- [ ] Mock location toggle works
- [ ] No keyboard focus issues in settings
