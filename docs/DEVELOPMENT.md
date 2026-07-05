# Beacon — Development guide

## Requirements

- **JDK 21** (Android Studio's bundled runtime works).
- **Android SDK**: platform 37, build-tools 36.x, platform-tools. Android Studio installs these on demand; for CLI-only setups use `sdkmanager "platform-tools" "platforms;android-37" "build-tools;36.0.0"`.
- Gradle is provided by the wrapper (`./gradlew`, Gradle 9.3.1) — don't install it separately.
- `local.properties` with `sdk.dir=<path-to-android-sdk>` (Android Studio writes this automatically; it is gitignored).

## Build & verify

```bash
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew test            # JVM unit tests (protocol, tie-break, chat repository)
./gradlew lint            # manifest/permission checks
```

## Toolchain notes

- **AGP 9 uses built-in Kotlin** — there is no `org.jetbrains.kotlin.android` plugin in this project; adding it back will fail the build.
- KSP (Room codegen) requires KSP 2.3.x+ to work with built-in Kotlin — earlier KSP versions abort configuration.
- The adaptive launcher icon must stay in `res/mipmap-anydpi-v26/` — aapt2 does not resolve `<adaptive-icon>` from an unqualified `mipmap-anydpi/` folder, even with minSdk 26 (lint's `ObsoleteSdkInt` suggestion is wrong here).

## Device testing (needs 2 physical phones)

Nearby Connections does not work on emulators — it needs real Bluetooth/Wi-Fi radios and Google Play services. Install the debug APK on both phones (`adb install`, or run from Android Studio; wireless debugging via `adb pair`/`adb connect <ip>:<port>` also works).

**Checkpoint A — M1 discovery & connect**
1. Complete onboarding on both phones (grant all nearby permissions).
2. Leave both on the Home screen a few meters apart.
3. Expect: each phone lists the other by display name within ~5–30 s, and the persistent notification shows "1 person nearby".
4. Useful logcat filter: `adb logcat -s NearbyManager`.

**Checkpoint B — M2 1:1 chat**
1. From Checkpoint A, tap the peer on either phone.
2. Exchange messages both ways — delivery should feel instant; sender sees a local echo immediately.
3. Kill and relaunch the app: the conversation history must survive (Room DB), and the peer reconnects automatically.
4. Duplicate check: toggling Bluetooth off/on mid-chat may resend payloads — messages must not duplicate (idempotent msgId).

## Architecture crib sheet

- `nearby/NearbyManager.kt` — advertise + discover simultaneously; connects to every Beacon endpoint found (full mesh, no relay). The simultaneous-connect race is resolved by `IdGen.shouldInitiateConnection` (lexicographically smaller session prefix initiates).
- `nearby/protocol/BeaconEnvelope.kt` — the single JSON wire format. `type` is a string so unknown types are skipped, `msgId` is the idempotency key.
- A 1:1 chat is a room whose code is `IdGen.dmRoomCode(a, b)` — M3 group rooms will reuse the same `ChatRepository` pipeline.
- DI is a hand-rolled `AppContainer` on `BeaconApp`; ViewModels get it via the `viewModel { }` factory in `BeaconNavHost`.
