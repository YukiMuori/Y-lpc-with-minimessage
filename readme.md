![LPC Banner](https://cdn.varilx.de/raw/fwtRZS.png)

<p align="center">
  <a href="https://modrinth.com/plugin/lpc-chat">
    <img src="https://raw.githubusercontent.com/vLuckyyy/badges/main/avaiable-on-modrinth.svg" alt="Available on Modrinth" />
  </a>
</p>

# LPC Chat Suite V5 ✨
**A complete MiniMessage chat formatting, social, moderation and statistics suite for LuckPerms.**

> Built on [MiniMessage](https://docs.advntr.dev/minimessage/format.html), tightly integrated with LuckPerms,
> PlaceholderAPI and (optionally) Nexo glyphs. Private messages, social spy, staff chat, @staff/@everyone mentions,
> notifications, slow mode, clear chat, ignore, chat statistics (SQLite) and Discord integration — all in one plugin.

---

## 🧩 Compatibility

**One jar runs on everything.** LPC ships a **single universal jar**:

| | |
|---|---|
| **Minecraft** | 1.21.x – 26.2 *(incl. 1.21.11)* |
| **Server** | Paper, Folia or Spigot |
| **Java** | 21+ (runs on 21 **and** 25) |

Requirements:
- **LuckPerms** *(required)*
- **PlaceholderAPI** *(optional)* – extra placeholders
- **Nexo** *(optional)* – for `<glyph:name>` emoji support
- **EssentialsX / SuperVanish / PremiumVanish / CMI** *(optional)* – vanish integration auto-detected
- A companion **Discord bot plugin** *(optional)* – provides a `DiscordService` bridge; LPC itself does not bundle a Discord client

---

## ✅ Features

**Chat Formatting**
- Full MiniMessage support with group- and track-specific formats
- Per-rank message styling and per-rank gradient names (`{gradient-name}`)
- Advanced player hover tooltip + click-to-message (`/w <name>`)
- Optional PlaceholderAPI integration and `[item]` placeholder
- Per-world toggle via `disabled-worlds`

**Private Messages**
- `/msg`, `/w`, `/tell` and `/reply` (`/r`) with smart last-partner tracking
- SocialSpy for staff (`/socialspy`) to see all DMs
- Vanish-aware: vanished players are not revealed to players who cannot see them
- Respects ignore lists

**Social**
- **@Mention pings** – highlight online names + sound/action-bar ping
- **@staff** – ping only staff members (permission configurable)
- **@everyone** – protected by permission, per-player cooldown and max-per-message limits
- **Emoji shortcuts** (`:heart:` → ❤) and **Nexo glyph aliases** (`:cuore:` → `<glyph:heart>`)
- **Clickable links** (openUrl only, never runCommand)

**Staff Tools**
- **Staff chat** (`/sc`, `/staffchat`) – toggleable channel, or one-shot messages
- **Ignore system** (`/ignore`) – per-player block list for chat, DMs, mentions and notifications
- **Slow mode** (`/lpc slowmode <seconds|off>`) with bypass permission
- **Clear chat** (`/clearchat`, `/cc`) – visual-only; logs preserved
- **Notifications** – sound + actionbar per event type; individually toggleable

**Moderation** *(off by default)*
- Anti-spam, repeat, caps, profanity, anti-advertising filters
- Per-player mute (`/lpc mute`) + LuckPerms mute node support

**Statistics**
- SQLite backend (`plugins/LPC/data.db`), fully async (never blocks the main thread)
- `/lpc stats [player]` — messages sent, DMs, mentions, glyphs, blocks, etc.

**Discord** *(optional)*
- Service abstraction (`DiscordService`) for a companion bot plugin to implement
- MC→Discord and Discord→MC hooks for global chat, staff chat, join/quit/death events
- **No Discord library is bundled in LPC**; the token is never hardcoded

**Server messages** *(off by default)*
- MiniMessage join / quit / first-join / death messages

**Security**
- Player messages are **never** parsed with the full MiniMessage parser; a restricted (cosmetic-only) parser is used, AND a component-level sanitizer strips any surviving click/hover/insertion events.
- All click/hover events in player names and links are generated server-side; players can never inject a run_command action.
- Discord, PlaceholderAPI and item-name input is all treated as untrusted and sanitized.

---

## ⌨️ Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/lpc reload` | `lpc.reload` | Reload the configuration |
| `/lpc version` | – | Show version info |
| `/lpc slowmode <seconds\|off>` | `lpc.slowmode` | Set global chat cooldown |
| `/lpc clearchat` | `lpc.clearchat` | Visually clear chat for all players |
| `/lpc notifications [type]` | `lpc.notifications` | Toggle notifications (all / per type) |
| `/lpc stats [player]` | `lpc.stats` / `lpc.stats.others` | View chat statistics |
| `/lpc mute/unmute` | `lpc.mute` | Mute/unmute a player |
| `/msg <player> <message>` | `lpc.msg` | Send a private message (aliases `/w`, `/tell`) |
| `/reply <message>` | `lpc.reply` | Reply to last PM (alias `/r`) |
| `/ignore [player]` | `lpc.ignore` | Toggle ignore or list ignored players |
| `/staffchat [message]` | `lpc.staffchat` | Toggle staff mode or send a staff message (alias `/sc`) |
| `/socialspy` | `lpc.socialspy` | Toggle SocialSpy |

---

## 🧑‍💼 Permissions

Key permissions (full list in `plugin.yml`):

| Permission | Default | Description |
|------------|---------|-------------|
| `lpc.chatcolor` | false | Use cosmetic MiniMessage tags in chat |
| `lpc.itemplaceholder` | op | Use `[item]` placeholder |
| `lpc.emoji` / `lpc.glyph` | true | Use emojis / Nexo glyph aliases |
| `lpc.msg` / `lpc.reply` | true | Send / reply to private messages |
| `lpc.msg.bypass` | op | Bypass a target's PM disabled toggle |
| `lpc.ignore` / `lpc.ignore.bypass` | true / op | Ignore players / cannot be ignored |
| `lpc.socialspy` / `lpc.socialspy.bypass` | op / op | Use SocialSpy / hidden from SocialSpy |
| `lpc.staffchat` / `lpc.staffchat.spy` | op / op | Write staff chat / read without mode |
| `lpc.mention.staff` / `.staff.use` | op / op | Receive / send `@staff` pings |
| `lpc.mention.everyone` / `.everyone.bypass` | op / op | Send `@everyone` / bypass cooldown |
| `lpc.slowmode` / `.slowmode.bypass` | op / op | Set slow mode / bypass it |
| `lpc.clearchat` / `.clearchat.bypass` | op / op | Clear chat / bypass clearing |
| `lpc.stats` / `lpc.stats.others` | true / op | View own/others stats |
| `lpc.vanish.see` | op | See vanished players in PM/mentions/spy |
| `lpc.admin` | op | Grants all admin permissions |

---

## ⚙️ Configuration highlights

```yaml
# Player hover tooltip (applied to the {name} placeholder)
player-hover:
  enabled: true
  click-to-message: true
  lines:
    - "<gradient:#FED83D:#BE2086>{name}</gradient>"
    - "<gray>Rank: <white>{prefix}</white>"
    - "<gray>World: <white>{world}</white>"
    - ""
    - "<yellow>Click to message"

# Private messages
private-messages:
  format-sender: "<gray>[<gold>You</gold> → <white>{receiver}</white>]</gray> <white>{message}"
  format-receiver: "<gray>[<white>{sender}</white> → <gold>You</gold>]</gray> <white>{message}"

# SocialSpy
social-spy:
  format: "<dark_gray>[SocialSpy]</dark_gray> <gray>{sender} → {receiver}: <white>{message}</white>"

# Staff chat
staff-chat:
  format: "<gradient:#FED83D:#BE2086>STAFF</gradient> <gray>{name} » <white>{message}</white>"

# @everyone (permission + cooldown protected)
mentions:
  everyone:
    enabled: true
    permission: "lpc.mention.everyone"
    cooldown: 60
    max-per-message: 1

# Notifications
notifications:
  private-message:
    sound: "entity.experience_orb.pickup"
    actionbar: "<gold>✉ <white>New message from <yellow>{sender}</yellow>"
  mention:
    sound: "entity.experience_orb.pickup"
    pitch: 1.2
    actionbar: "<gold>✦ <white>You were mentioned by <yellow>{sender}</yellow>"
  everyone:
    sound: "entity.player.levelup"
    actionbar: "<gradient:#FED83D:#BE2086>✦ @everyone</gradient>"

# Glyphs (Nexo)
glyphs:
  enabled: true
  registry:
    heart:
      aliases: [":cuore:", ":heart:"]
      fallback: "❤"

# Slow mode / clear chat
slow-mode:
  default-seconds: 0
clear-chat:
  lines: 100

# Statistics (SQLite, async)
statistics:
  enabled: true

# Discord integration (disabled; requires a companion bot plugin)
discord:
  enabled: false
```

See `config.yml` for the complete documentation of every section.

---

## 🪄 Placeholders

Standard placeholders in formats: `{message}`, `{name}`, `{displayname}`, `{gradient-name}`, `{world}`,
`{prefix}`, `{suffix}`, `{prefixes}`, `{suffixes}`, `{username-color}`, `{message-color}`.

PlaceholderAPI `%...%` placeholders are supported when PAPI is installed, in operator-authored formats
only — never expanded against player chat text.

---

## Nexo Glyph Setup

1. Install [Nexo](https://www.spigotmc.org/resources/nexest-items-nexo.100880/) and define your glyphs there.
2. In LPC's `config.yml`, list the `:alias:` → glyph mappings under `glyphs.registry`.
3. Players type the alias (e.g. `:cuore:`) and it is replaced with `<glyph:heart>` on the server side.
4. When Nexo is not installed the `fallback:` string is used instead (e.g. `❤`), so chat never breaks.

---

## Discord Setup

LPC does **not** include a Discord bot directly — it exposes a `DiscordService` interface that a companion
plugin can implement and register via `LPC#setDiscordService(...)`. This keeps LPC small and avoids bundling
JDA for servers that do not need Discord. Set `discord.enabled: false` to leave the bridge disabled (default).

The Discord → Minecraft path must treat Discord user input as **untrusted**: plain text only, no MiniMessage
parsing, no click/hover events.

---

## 🚀 Installation

1. Stop your server.
2. Drop `LPC-<version>.jar` into your `/plugins` folder.
3. Start the server to generate `config.yml`.
4. Edit the config to your liking.
5. Run `/lpc reload` to apply changes ✅.

---

## 🛠️ Building

```bash
./gradlew shadowJar
# output: build/libs/LPC-<version>.jar
```

Requires JDK 21+.

---

## 📌 Notes

- **Developed by [Veylor-Development](https://veylor.net)**.
- **Can players use MiniMessage in chat?** Yes — grant the `lpc.chatcolor` permission. Only **cosmetic**
  tags are honoured; interactive tags (`click`, `hover`, `insertion`, …) are always stripped.
- **Not affiliated with LuckPerms.**
- Legacy version available at: [GitHub Legacy LPC](https://github.com/wikmor/LPC)

---

## 📄 License

Released under the MIT License.
