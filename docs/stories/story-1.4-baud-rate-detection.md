# Story 1.4: Baud Rate Auto-Detection

## Story Info

| Field | Value |
|-------|-------|
| **Epic** | USB Serial GNSS Connectivity |
| **Story ID** | 1.4 |
| **Status** | Draft |
| **Priority** | High |
| **Dependencies** | Story 1.3 |

## User Story

**As a** user,
**I want** the app to automatically detect the correct baud rate,
**so that** I don't need to manually configure serial settings.

## Acceptance Criteria

| # | Criterion | Status |
|---|-----------|--------|
| AC1 | Auto-detection cycles through baud rates: 115200, 57600, 38400, 19200, 9600, 4800 | |
| AC2 | Detection reads data at each rate, validates NMEA sentence structure (`$` prefix, `*` checksum) | |
| AC3 | Detection completes within 5 seconds total | |
| AC4 | Successful detection reports the detected baud rate via callback | |
| AC5 | Failed detection (no valid data at any rate) reports error with attempted rates | |
| AC6 | Detection can be cancelled if user disconnects during process | |

## Integration Verification

| # | Verification | Status |
|---|--------------|--------|
| IV1 | Auto-detection works correctly with ZED-F9P at 115200 baud | |
| IV2 | Detection process doesn't leave serial port in inconsistent state | |
| IV3 | Timeout handling doesn't cause resource leaks | |

## Technical Notes

### Files to Modify

1. **`android/app/src/main/java/com/clearevo/libbluetooth_gnss_service/usb_conn_mgr.java`**
   - Add `connectWithAutoDetect()` method
   - Add baud rate detection logic

### Detection Algorithm

```java
public static final int[] BAUD_RATES = {115200, 57600, 38400, 19200, 9600, 4800};
public static final int DETECTION_TIMEOUT_PER_RATE_MS = 800;
public static final int MIN_VALID_SENTENCES = 2;

public void connectWithAutoDetect(UsbDevice device) {
    for (int baudRate : BAUD_RATES) {
        if (closed) return; // Check for cancellation

        Log.d(TAG, "Trying baud rate: " + baudRate);

        try {
            // Open at this baud rate
            port.setParameters(baudRate, 8, STOPBITS_1, PARITY_NONE);

            // Try to read and validate NMEA
            if (validateNmeaData(DETECTION_TIMEOUT_PER_RATE_MS)) {
                Log.d(TAG, "Detected baud rate: " + baudRate);
                m_callback.on_baud_rate_detected(baudRate);
                m_callback.on_usb_connected(device.getDeviceName(), baudRate);
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error at baud " + baudRate + ": " + e.getMessage());
        }
    }

    // All rates failed
    m_callback.on_usb_error("Could not detect baud rate. Tried: " + Arrays.toString(BAUD_RATES));
}
```

### NMEA Validation

```java
private boolean validateNmeaData(int timeoutMs) {
    byte[] buffer = new byte[256];
    StringBuilder lineBuilder = new StringBuilder();
    int validSentences = 0;
    long startTime = System.currentTimeMillis();

    while (System.currentTimeMillis() - startTime < timeoutMs) {
        int bytesRead = m_usb_serial_port.read(buffer, 100);
        if (bytesRead > 0) {
            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.US_ASCII);
            lineBuilder.append(chunk);

            // Check for complete NMEA sentences
            String data = lineBuilder.toString();
            int newlineIdx;
            while ((newlineIdx = data.indexOf('\n')) != -1) {
                String line = data.substring(0, newlineIdx).trim();
                data = data.substring(newlineIdx + 1);

                if (isValidNmeaSentence(line)) {
                    validSentences++;
                    if (validSentences >= MIN_VALID_SENTENCES) {
                        return true;
                    }
                }
            }
            lineBuilder = new StringBuilder(data);
        }
    }
    return false;
}

private boolean isValidNmeaSentence(String line) {
    // Basic NMEA validation: starts with $, contains *, checksum matches
    if (!line.startsWith("$") || !line.contains("*")) {
        return false;
    }

    int asteriskIdx = line.lastIndexOf('*');
    if (asteriskIdx < 1 || asteriskIdx + 3 > line.length()) {
        return false;
    }

    // Verify checksum
    String payload = line.substring(1, asteriskIdx);
    String checksumStr = line.substring(asteriskIdx + 1);

    int calculatedChecksum = 0;
    for (char c : payload.toCharArray()) {
        calculatedChecksum ^= c;
    }

    try {
        int providedChecksum = Integer.parseInt(checksumStr, 16);
        return calculatedChecksum == providedChecksum;
    } catch (NumberFormatException e) {
        return false;
    }
}
```

### Callback Extension

```java
public interface usb_conn_callbacks {
    // ... existing methods ...
    void on_baud_rate_detected(int baudRate);
    void on_baud_rate_detection_progress(int currentRate, int ratesRemaining);
}
```

### Testing Checklist

- [ ] ZED-F9P at 115200 - detected on first attempt
- [ ] Device at 9600 - detected after cycling through higher rates
- [ ] No device / no data - times out with error message
- [ ] Disconnect during detection - process stops cleanly
- [ ] Total detection time < 5 seconds

## References

- NMEA 0183 specification (checksum calculation)
- PRD: [docs/prd.md](../prd.md)
