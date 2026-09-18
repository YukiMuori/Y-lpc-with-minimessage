# Changelog

## 5.0.0 — LPC Chat Suite V2

**Minecraft:** 1.21.x – 26.2 · **Java:** 21+ · **API version:** 1.21 (universal jar)

Major feature release — transforms LPC from a chat formatter into a complete chat suite while
fully preserving backward compatibility with 4.x configuration and behaviour.

### Added

- **Private messaging** — `/msg`, `/w`, `/tell` and `/r`/`/reply` with automatic last-partner
  tracking. Vanish-aware, ignore-aware, PM-disable toggle, sound/actionbar notification.
- **Reply system** — remembers last incoming and outgoing conversation partners per player.
- **SocialSpy** — `/socialspy` lets staff see private messages, with format configurable and
  a bypass permission (`lpc.socialspy.bypass`) so DMs can be hidden from spy.
- **Ignore system** — `/ignore [player]` to toggle; blocks chat, private messages, mentions
  and notifications (configurable); `lpc.ignore.bypass` makes a player un-ignorable.
- **Staff chat** — `/staffchat` / `/sc` (toggle mode or one-shot messages); permission-gated
  channel with `/sc <message>`; formatted header and staff-chat notification.
- **@staff mention** — pings players with the configured staff permission, bypassed vanish/ignore.
- **@everyone mention** — protected by permission, per-player cooldown, and max-per-message
  limit; bypass permission available.
- **Advanced player hover** — server-controlled hover tooltip on the `{name}` placeholder,
  fully configurable lines, PlaceholderAPI-aware, with hardcoded suggest_command to `/w <name>`.
- **Chat notifications** — centralized, per-event sound + actionbar system (PM, mention,
  @everyone, @staff); individually toggleable via `/lpc notifications [type]`.
- **Vanish awareness** — `VanishService` with auto-detection of EssentialsX, SuperVanish/
  PremiumVanish, and CMI; vanished players are not pinged, messaged, or revealed via SocialSpy
  to players who cannot see them.
- **Slow mode** — `/lpc slowmode <seconds|off>` global chat cooldown with bypass permission.
- **Clear chat** — `/lpc clearchat` / `/cc` sends blank lines to visually clear chat;
  `lpc.clearchat.bypass` exempts staff.
- **Nexo Glyph aliases** — rich glyph registry (`glyphs.registry`) supporting multiple aliases
  per glyph (`:cuore:` → `<glyph:heart>`), safe restricted parser, automatic fallback when
  Nexo is absent; legacy emoji config still works.
- **Chat statistics** — async SQLite storage (`plugins/LPC/data.db`), tracking messages, DMs,
  mentions, glyphs, links, staff messages, Discord messages, and blocks; `/lpc stats [player]`.
- **Discord integration** — `DiscordService` abstraction so a companion bot plugin can bridge
  MC↔Discord without LPC hard-depending on JDA. Join/quit/death and staff chat relays.
- **Internal API / events** — cancellable Bukkit events: `LPCChatMessageEvent`,
  `LPCPrivateMessageEvent`, `LPCMentionEvent`, `LPCStaffChatEvent`, `LPCGlyphEvent`,
  `LPCIgnoreEvent`, for addon plugins to hook into.
- **Modular command architecture** — subcommands split into separate classes; quick commands
  (`/sc`, `/cc`, `/socialspy`, `/msg`, `/r`, `/ignore`) registered individually.
- **Optional plugin hooks architecture** — `PlaceholderAPIHook`, `NexoHook`, `VanishService`,
  `DiscordService` for clean integration with optional dependencies.
- Per-player toggle state for PMs, staff-chat mode, and social spy.

### Changed

- `{name}` placeholder is now injected as a component so hover/click set by `PlayerHoverService`
  (and future hooks) attaches correctly to the player name in the rendered chat line.
- Chat rendering now applies per-viewer ignore filtering (ignored players' messages are hidden).
- Mention pings now respect ignore lists, vanish state, and `lpc.mention.exempt`.
- Default config bumped to `config-version: 5`. All new sections are documented.

### Security

- Player messages still only go through the restricted cosmetic MiniMessage parser AND
  `ComponentSanitizer.stripInteractive`, so click/hover/insertion events are impossible from
  player input. New glyph and special-mention rendering uses the same restricted parser
  server-side; the glyph name is validated against a safe character set before building tags.
- Discord bridge input is expected to be plain-text; DiscordService implementations must not
  parse Discord user input as MiniMessage.

### Backward Compatibility

- Every existing 4.x feature is preserved: chat formatting, prefix/suffix, group & track
  formats, gradient names, message styles, PlaceholderAPI, item placeholder, mentions, emoji,
  clickable links, join/quit/death messages, moderation, mute, update checker, disabled worlds.
- Existing config options keep their defaults; new features default to sensible values and
  can be individually disabled.

---

## 4.1.0

**Released:** 2026-06-16
**Minecraft:** 26.1.2 · **Java:** 25 · **API version:** 26.1.2

Native Folia support with region-aware scheduling while retaining full Paper/Spigot compatibility.

### Added

- `folia-supported: true` in `plugin.yml`.
- Cross-platform scheduler abstraction in `de.ayont.lpc.scheduler`.
- Folia detection in `LPC` with dedicated log output.
- `onDisable` cancels all scheduled tasks cleanly.

### Changed

- `UpdateChecker` and `MentionService.pingAll` schedule via the platform scheduler.

## 4.0.0

**Released:** 2026-06-15
**Minecraft:** 26.1.2 · **Java:** 25 · **API version:** 26.1.2

Major rewrite release with a new MiniMessage chat formatter, an integrated moderation toolkit, and flexible configuration.

### Added

- MiniMessage chat formatter with trusted-vs-untrusted separation.
- Group & track formats.
- `[item]` placeholder with Paper hover tooltip.
- Placeholder support: `{prefix}`, `{suffix}`, `{prefixes}`, `{suffixes}`, `{world}`, `{displayname}`, `{username-color}`, `{message-color}`, `{gradient-name}`.
- Optional PlaceholderAPI integration.
- @Mentions with sound + action-bar ping (Paper).
- Emoji replacement (`:heart:` → ❤).
- Clickable links (openUrl only).
- Per-rank message styles, gradient names.
- Join / quit / first-join / death messages in MiniMessage.
- Moderation toolkit (anti-spam, repeat, caps, profanity, anti-advert).
- Mute system with `/lpc mute/unmute`.
- Modrinth update checker.
- Paper, Folia & Spigot compatibility.
- Unit tests.

### Security

- Player messages cannot inject MiniMessage tags or PlaceholderAPI placeholders.
- Interactive tags are always stripped from player input.

