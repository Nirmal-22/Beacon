# Beacon — Product Spec

## The core insight

Proximity chat apps fail because they answer *"Who is near me?"* — a question nobody actually cares about. Being 12 meters from a stranger is not a reason to talk. The technology is solved; the **reason to open the app** is the real product problem.

Beacon's answer: give people **context** and **usefulness**.

---

## Pillar 1 — Temporary Rooms

Chat scoped to a shared context rather than raw proximity.

**Behavior**
- A user creates or joins a room by a name/code tied to a place or situation.
- Everyone in the room shares messages (group chat).
- The room and its messages are **ephemeral** — cleared when the last person leaves or after an inactivity timeout.

**Examples**
- 📚 Library — Silent Floor
- 🚆 Train 12628 — Coach B2
- ☕ Starbucks Indiranagar
- 🎤 Concert — Gate 3

**Why it's better:** common ground exists before the first message.

---

## Pillar 2 — Need Help Nearby

A signature feature nobody has done well. People post short-lived requests visible only within ~50–100 m.

**Behavior**
- Post a request with a category + short text.
- Request expires in 10–30 minutes.
- Only nearby users see it; they can respond and a 1:1 chat opens.

**Examples**
- 🔌 USB-C charger?
- 🎒 Watch my bag for 2 minutes
- 🏸 One more player for badminton
- 🚗 Anyone going to Terminal 2?

**Why it matters:** solves real, immediate problems. This is the "worth building" story — *not about friends, about being useful.*

> Note: this pillar depends on ambient density to be genuinely useful, so it's built **after** Rooms proves out. In a demo it works fine with 2 devices.

---

## Supporting feature — Intent-based Discovery

Inside a room, each user can broadcast **one** lightweight intent.

- ☕ Coffee · 🎮 Gaming · 💼 Networking · 🤝 Friends · 📚 Study partner

Users with matching intents surface to each other.

### Icebreaker mechanism
Chat does not open immediately. First interaction is a single tap:

- 👍 Hello · ☕ Coffee? · 🎮 Want to play? · 📍 Where are you?

Only after the recipient **accepts** does free chat unlock. This dramatically reduces spam and awkwardness.

---

## Explicitly cut (and why)

| Idea | Verdict |
|---|---|
| AI "who you'll get along with" | ❌ Interest matching is set intersection, not AI. No AI needed. |
| Bluetooth Pokémon GO (collect people) | ❌ Novelty; poor retention. |
| Skill Radar | ❌ Too niche for a general app. |
| Event Companion | ⚠️ Strong idea, out of scope for v1 — revisit later. |
| One Question of the day | 🔹 Optional nice-to-have inside a room; not a headline. |

---

## Marketing framing

Do **not** call it a "nearby chat app." Position it as a **real-time local interaction app**:

> **Ephemeral shared-context rooms + get help from people nearby.**

Two pillars, one clear pitch.

---

## Trust & safety (baseline)

- Ephemeral identities (display name only; no mandatory profile).
- Block / report on any user.
- Icebreaker-before-chat gate reduces unsolicited messages.
- Chats expire (default 24 h) unless both users save the connection (optional QR exchange to become permanent contacts).
