# QwiQR

Native Android app for reliable Bluetooth HID scanner capture — pairing, focus-independent
key event interception, buffer-and-commit scan parsing, and live connection status.

## Current Scope

This repo currently implements the scan-capture proof of concept:

- Pair with a Bluetooth HID scanner (Classic or BLE), either through the device's own
  Bluetooth settings or directly in-app via device discovery + `createBond()`.
- Capture scans via an Activity-level key event listener, not a focused text field, so
  input is never lost regardless of what's on screen.
- Buffer characters and commit a scan on the Enter/CR terminator, discarding a stale
  partial buffer if a scan is interrupted mid-read.
- Serialize commits through a single-consumer channel so rapid consecutive scans are
  always processed in order, never dropped or merged.
- Live connection-status indicator (Connected / Reconnecting / Disconnected), combining
  Bluetooth ACL broadcasts with a periodic BLE GATT state poll for accuracy.

Persistence, kiosk mode, and the full item-tracking workflow are not yet implemented.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3
- **Architecture:** ViewModel + `StateFlow`, Compose observing state directly
- **Concurrency:** Kotlin Coroutines (`Channel` for serialized scan processing)
- **Min SDK:** 26 (Android 8.0) — **Target/Compile SDK:** 35 (Android 15)
- **Build:** Gradle 8.11.1, Android Gradle Plugin 8.10.1

## Project Structure

```
app/src/main/java/com/example/itemtracker/
├── MainActivity.kt                  Activity-level dispatchKeyEvent capture
└── scan/
    ├── ScanCaptureBuffer.kt         Buffer + commit-on-terminator logic
    ├── ScanViewModel.kt             Key event routing, serialized scan queue
    ├── ScannerConnectionMonitor.kt  Live connection status (ACL + BLE GATT poll)
    ├── DevicePairingManager.kt      In-app device discovery + pairing (createBond())
    └── ScanScreen.kt                Compose UI

app/src/test/java/com/example/itemtracker/scan/
└── ScanCaptureBufferTest.kt         Unit tests for buffer/timeout logic
```

## Getting Started

1. Open this folder in Android Studio (**File → Open**, not New Project).
2. If prompted that the Gradle wrapper is missing, accept the offer to create it — the
   repo doesn't commit `gradlew`/`gradle-wrapper.jar`, Android Studio generates them
   using its bundled Gradle on first sync.
3. Let Gradle sync finish, then run on a device or emulator.
4. If Android Studio flags the pinned AGP/Kotlin versions as mismatched with what it has
   bundled, accept its suggested upgrade.

## Testing

Unit tests cover the capture buffer's core logic: clean commits, rapid consecutive
scans, interrupted-scan handling, and stale-buffer edge cases.

Run from Android Studio (right-click `ScanCaptureBufferTest` → Run), or from the
command line once the Gradle wrapper exists:

```bash
./gradlew testDebugUnitTest
```

## Known Limitations

- Connection-status events are scoped to BLE/dual-mode devices, which rules out the
  common false-positive case (Classic Bluetooth accessories moving the indicator), but
  doesn't yet distinguish between two different BLE devices paired at once.
- The live GATT-based status poll is BLE-specific; a Classic HID scanner would still
  work for capture, but connection status would fall back to a less precise,
  broadcast-only signal.
- No public Android API lets a third-party app force an HID profile reconnect the way
  Settings can, so the app only observes and initiates pairing rather than forcing
  reconnection. Testing showed the OS reliably auto-reconnects after a link-loss
  disconnect (e.g. the scanner sleeping), but not after an explicit Settings disconnect,
  which intentionally suppresses auto-reconnect until the user reconnects manually.
