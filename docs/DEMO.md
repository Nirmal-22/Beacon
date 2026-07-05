# Beacon — 60-second demo script (2 phones)

Phone A = yours, Phone B = a friend's. Both onboarded, radar on, side by side.

| ⏱ | Action | What the camera sees |
|---|---|---|
| 0:00 | Open Beacon on both | Sonar pulse scanning… then each phone **pings** as the other appears on People |
| 0:08 | A: set intent ☕ Coffee. B: set intent ☕ | B's row on A jumps up: "Also here for Coffee — say hi!" |
| 0:15 | A: tap B → tap ☕ icebreaker | B gets a notification: "A wants to chat ☕" |
| 0:20 | B: accept | Chat unlocks. Exchange two messages — ticks go ✓ → ✓✓ → **blue** as B reads |
| 0:30 | A: Rooms tab → + → "Demo Cafe" | Room appears on B's Nearby rooms in seconds, "1 person here" |
| 0:35 | B: tap to join → both chat in the room | Presence line: "2 here — A, B"; typing indicator flickers |
| 0:42 | B: leave room; A: leave room | Room vanishes everywhere — messages purged (ephemerality) |
| 0:48 | B: Help tab → + → 🔌 "USB-C charger?" (10 min) | A gets "🔌 B needs help nearby"; feed shows countdown |
| 0:54 | A: "I can help" → sends a message | Direct chat opens instantly — the two pillars in one minute |

Optional beats if time allows: anonymous mode toggle (name becomes "Anon Fox 🦊" live on the other phone), notification Stop button, radar-off invisibility.

**Recording tips:** screen-record both phones (`adb shell screenrecord` works over Wi-Fi adb), side-by-side edit; kill both apps before starting so session identities are fresh.
