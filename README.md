# Beacon

**Ephemeral, proximity-based interaction — built around context and usefulness, not "who's nearby."**

Most proximity chat apps answer *"Who is near me?"* The technology (BLE, Nearby Connections, Wi-Fi Direct) is solved. The product isn't. Beacon answers the harder question: **"Why would I want to talk to them?"**

Beacon is a real-time local interaction app with two pillars:

1. **Temporary Rooms** — chat with people sharing the same *context*, not just the same location. Rooms disappear when everyone leaves.
2. **Need Help Nearby** — post a short-lived request ("charger?", "watch my bag 2 min", "one more for badminton") that only people within range see. Not about making friends — about being useful.

Everything is **ephemeral by default**: no permanent profiles required, chats expire, rooms die when empty.

> **Project purpose:** built for learning Android and as a portfolio/showcase piece — not a company or a profit venture. But the goal is a genuinely *usable, showcase-worthy* app that demonstrates the concept is worth building. This is why scope is kept tight (Event Companion deferred) and every feature must demo cleanly with just two devices.

---

## The two pillars

### 1. Temporary Rooms
Instead of chatting with everyone nearby, you join a room defined by shared context:

- 📚 Library — Silent Floor
- 🚆 Train 12628 — Coach B2
- ☕ Starbucks Indiranagar
- 🎤 Concert — Gate 3

You immediately have common ground. Rooms auto-expire when the last person leaves.

### 2. Need Help Nearby
Post a request that expires in 10–30 minutes, visible only to people within ~50–100 m:

- 🔌 USB-C charger?
- 🎒 Watch my bag for 2 minutes?
- 🏸 Need one more player for badminton
- 🚗 Anyone going to Terminal 2?

Solves real, immediate problems instead of manufacturing social interaction.

### Supporting feature — Intent-based Discovery
Inside a room, broadcast a single lightweight intent (☕ Coffee, 📚 Study partner, 🤝 Networking). Matching people surface. An **icebreaker** (tap an emoji → the other person accepts) unlocks chat, which cuts spam.

---

## What Beacon deliberately does *not* do

- ❌ **AI "who you'll get along with"** — matching interests is set intersection, not AI. Not needed.
- ❌ **Collect people you crossed paths with** (Bluetooth Pokémon GO) — novelty that fades.
- ❌ **Skill Radar** — too niche for a general app.
- ❌ **Event Companion** — a great idea, but too large in scope for now.

Scope discipline is a feature.

---

## Design principles

| Principle | Why |
|---|---|
| **Ephemeral by default** | No ghost-town profiles, less moderation burden, privacy-friendly. |
| **No signup friction** | Ephemeral identity + tap-to-join. Instant first-use "wow." |
| **Context before conversation** | Shared context = common ground = fewer awkward cold DMs. |
| **Useful, not just social** | "Help Nearby" gives a reason to open the app when you have no friends nearby. |
| **Works with 2 devices** | Every feature must demo cleanly with just two phones. |

---

## Documentation

- [Product spec](docs/PRODUCT.md) — the concept, pillars, features, and what's cut.
- [Architecture](docs/ARCHITECTURE.md) — stack, connectivity, data model.
- [Roadmap](docs/ROADMAP.md) — milestones from discovery to full app.

---

## Status

**M0–M2 code complete** (branch `feat/m0-m2`) — Kotlin + Jetpack Compose app with:

- Gradle scaffold: AGP 9.1.1, built-in Kotlin 2.3.21, Compose BOM 2026.06.00, compileSdk 37 / minSdk 26.
- Staged permission onboarding (version-gated for API 26 → 33+).
- Nearby Connections full-mesh discovery + auto-connect (`P2P_CLUSTER`), foreground service with live peer-count notification.
- 1:1 chat over a JSON wire protocol, persisted in Room with idempotent message IDs.
- 18 JVM unit tests (protocol codec, connection tie-break, chat repository).

Next: the M1/M2 device checkpoints (two physical phones discovering each other and chatting — see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)), then M3 Temporary Rooms.
