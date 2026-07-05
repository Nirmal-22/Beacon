# Beacon — Roadmap

Each milestone is a **complete, testable-with-2-devices** slice. Ship, demo, then move on.

## M0 — Project setup
- [ ] Android Studio project, Kotlin + Compose, MVVM skeleton.
- [ ] Nearby Connections dependency + service ID.
- [ ] Staged permission onboarding flow (BLE / location / nearby-wifi), with rationale screens.

## M1 — Discovery & connect (the hard part)
- [ ] Advertise + discover under fixed service ID.
- [ ] Two devices find each other and establish a connection.
- [ ] Foreground service + persistent notification.
- **Demo:** two phones see and connect to each other.

## M2 — 1:1 chat
- [ ] Send/receive text over an established connection.
- [ ] Message list UI (Compose), local echo, timestamps.
- **Demo:** two phones exchange messages.

## M3 — Temporary Rooms (Pillar 1)
- [ ] Create / join room by name or code.
- [ ] Group broadcast to everyone in the room.
- [ ] Room list + presence (who's here).
- **Demo:** 2–3 devices in one room chatting.

## M4 — Ephemerality
- [ ] Room clears when last person leaves / inactivity timeout.
- [ ] Message expiry (default 24 h) via Room DB + periodic sweep.
- [ ] Ephemeral identity (display name + random session ID).
- **Demo:** leave room → data gone.

## M5 — Intent & Icebreaker (supporting feature)
- [ ] Broadcast one intent inside a room.
- [ ] Surface matching users.
- [ ] Icebreaker tap → accept → chat unlocks.
- **Demo:** two users with matching intents connect via icebreaker.

## M6 — Need Help Nearby (Pillar 2 / signature)
- [ ] Post a help request (category + text + TTL 10–30 min).
- [ ] Nearby feed of active requests; respond → 1:1 chat.
- [ ] Auto-expire requests.
- **Demo:** post "charger?", second device sees it and responds.

## M7 — Polish & showcase
- [ ] Clean empty states ("no one nearby yet").
- [ ] Block / report.
- [ ] Optional QR exchange to save a contact.
- [ ] Compose UI polish, transitions, dark mode.
- [ ] 60-second demo video with 2 devices.

---

## Later (out of v1 scope)
- Event Companion mode (bounded event network).
- One-Question-of-the-day inside rooms.
- Raw BLE / Wi-Fi Direct transport (drop Play Services dependency).
