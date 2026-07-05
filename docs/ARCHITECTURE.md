# Beacon — Architecture

## Platform

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Min SDK:** 26 (Android 8.0) — reasonable BLE + runtime-permission baseline. Target latest stable.
- **Architecture pattern:** MVVM + unidirectional data flow (ViewModel → StateFlow → Compose).

## Connectivity

**Primary:** [Nearby Connections API](https://developers.google.com/nearby/connections/overview) (Google Play Services).

Chosen over raw BLE because it abstracts BLE + Wi-Fi + hotspot, handles the transport negotiation, and gives reliable demos with 2 devices — the right trade-off for the first build. Raw BLE / Wi-Fi Direct can be revisited later as a learning exercise or to drop the Play Services dependency.

**Strategy:** `P2P_CLUSTER` — many-to-many, which maps naturally to rooms.

**Discovery model**
- Advertise + discover under a fixed service ID (`com.beacon.nearby`).
- A **room** = a logical channel; the room code is exchanged in the connection/endpoint metadata, and messages are filtered/routed by room locally.
- "Need Help Nearby" = a broadcast payload type with a TTL, shown to any discovered endpoint in range.

## Permissions (get this right — it's where most nearby apps feel broken)

Runtime permissions needed (version-gated):
- `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (API 31+)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` (required by Nearby/BLE scan on older APIs)
- `NEARBY_WIFI_DEVICES` (API 33+, with `neverForLocation` where applicable)

A clean, staged onboarding flow that explains *why* each permission is needed is a first-class feature, not an afterthought.

## Foreground service

Discovery/advertising while the app is backgrounded runs in a **foreground service** with a persistent notification ("Beacon is active in <room>"). Handles Android's background execution limits.

## Data & persistence

All data is **local and ephemeral** — no backend required for the core app.

- **Room (Jetpack)** for local message history and saved contacts.
- In-memory state for active rooms, discovered endpoints, live help requests.
- **Expiry is enforced on read and by a periodic sweep:**
  - Messages/chats: default 24 h.
  - Rooms: cleared when empty or after inactivity timeout.
  - Help requests: 10–30 min TTL.

## Identity

- **Ephemeral identity** by default: a locally generated display name + random ID per session.
- Optional **QR code exchange** to promote a fleeting connection into a saved contact.
- No account, no login, no server-side profile.

## High-level module layout (proposed)

```
app/
  ui/            Compose screens (rooms list, room chat, help feed, onboarding)
  nearby/        Nearby Connections wrapper (advertise, discover, payloads)
  service/       Foreground service
  data/          Room DB, repositories, expiry sweeper
  model/         Room, Message, HelpRequest, Intent, Peer
  domain/        Use cases (join room, send message, post help, matching)
```

## No-backend stance

v1 is **fully peer-to-peer and offline** — no server. This keeps the project self-contained, privacy-friendly, and demoable anywhere. A backend (relay, persistence, moderation) is only considered if scope later demands it.
