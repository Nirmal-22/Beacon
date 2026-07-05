# Beacon — Roadmap

Each milestone is a **complete, testable-with-2-devices** slice. Ship, demo, then move on.

## M0 — Project setup
- [x] Android Studio project, Kotlin + Compose, MVVM skeleton.
- [x] Nearby Connections dependency + service ID.
- [x] Staged permission onboarding flow (BLE / location / nearby-wifi), with rationale screens.

## M1 — Discovery & connect (the hard part)
- [x] Advertise + discover under fixed service ID.
- [x] Two devices find each other and establish a connection.
- [x] Foreground service + persistent notification.
- **Demo:** two phones see and connect to each other.

## M2 — 1:1 chat
- [x] Send/receive text over an established connection.
- [x] Message list UI (Compose), local echo, timestamps.
- **Demo:** two phones exchange messages.

## M3 — Temporary Rooms (Pillar 1)
- [x] Create / join room by name or code.
- [x] Group broadcast to everyone in the room.
- [x] Room list + presence (who's here).
- **Demo:** 2–3 devices in one room chatting.

## M4 — Ephemerality
- [x] Room clears when last person leaves / inactivity timeout.
- [x] Message expiry (default 24 h) via Room DB + periodic sweep.
- [x] Ephemeral identity (display name + random session ID).
- **Demo:** leave room → data gone.

## M5 — Intent & Icebreaker (supporting feature)
- [x] Broadcast one intent inside a room.
- [x] Surface matching users.
- [x] Icebreaker tap → accept → chat unlocks.
- **Demo:** two users with matching intents connect via icebreaker.

## M6 — Need Help Nearby (Pillar 2 / signature)
- [x] Post a help request (category + text + TTL 10–30 min).
- [x] Nearby feed of active requests; respond → 1:1 chat.
- [x] Auto-expire requests.
- **Demo:** post "charger?", second device sees it and responds.

## M7 — Polish & showcase
- [x] Clean empty states ("no one nearby yet").
- [x] Block / report.
- [~] QR save-contact: deferred — session ids rotate every run, so a saved contact could never be re-found without stable identity (needs keypairs; out of v1 scope).
- [x] Compose UI polish, transitions, dark mode.
- [ ] 60-second demo video with 2 devices — script in [DEMO.md](DEMO.md).

---

## Later (out of v1 scope)
- Event Companion mode (bounded event network).
- One-Question-of-the-day inside rooms.
- Raw BLE / Wi-Fi Direct transport (drop Play Services dependency).
