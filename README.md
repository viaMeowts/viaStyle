# viaStyle

Server-side Fabric mod for Minecraft 1.21.10. Replaces the vanilla chat system with a multi-channel setup, adds tab list styling, per-player nick colours, private messages, mentions, social spy, and optional integrations with LuckPerms, BlockBot, Carpet, PAPI, and BanHammer.

No client mod required.

---

## Requirements

- Minecraft 1.21.10
- Fabric Loader >= 0.16.12
- Fabric API

Optional dependencies (auto-detected at runtime):

- LuckPerms  prefix/suffix display, weight-based tab sorting, permission checks
- PlaceholderAPI (PAPI)  use `%placeholder%` tokens in format strings
- BlockBot  Discord bridge
- fabric-carpet  Scarpet event integration and HUD logger detection
- melius-vanish (DrexHD)  vanished players are hidden from suggestions and tab list

---

## Installation

1. Drop `viastyle-<version>+mc1.21.10.jar` into the server's `mods/` folder.
2. Start the server. A default config is generated at `config/viaStyle/viaStyle.toml`.
3. Edit the config as needed, then run `/via reload` in-game or restart the server.

---

## Configuration

All settings live in `config/viaStyle/viaStyle.toml`. The file is human-readable TOML. Missing keys are filled with defaults on next load. All settings can also be changed at runtime through the `/via admin` panel.

### [local]

| key | default | description |
|---|---|---|
| radius | 100.0 | Visibility range in blocks |
| trigger | *(empty)* | Prefix char to enter local mode  empty means local is the default channel |
| prefix | `[L]` | Tag prepended to messages |
| prefix_color | GREEN | Color of the prefix tag |
| name_color | GRAY | Color of the player name |
| message_color | GRAY | Color of the message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |
| noone_heard | false | Notify the sender when no player is within range |
| noone_heard_message | `Nobody heard you.` | Text shown to the sender in that case |

### [global]

| key | default | description |
|---|---|---|
| trigger | `!` | Prefix char to route a message to global chat |
| prefix | `[G]` | Tag prepended to messages |
| prefix_color | YELLOW | Color of the prefix tag |
| name_color | WHITE | Color of the player name |
| message_color | WHITE | Color of the message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |

### [staff]

| key | default | description |
|---|---|---|
| trigger | `\` | Prefix char for staff chat  empty to disable |
| prefix | `[Staff]` | Tag prepended to messages |
| prefix_color | RED | Color of the prefix tag |
| name_color | WHITE | Color of the player name |
| message_color | DARK_RED | Color of the message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |

### [timestamp]

| key | default | description |
|---|---|---|
| show_timestamp | false | Prepend a timestamp to each message |
| timestamp_format | `HH:mm` | Java DateTimeFormatter pattern |
| timestamp_color | DARK_GRAY | Color of the timestamp |

### [integrations]

| key | default | description |
|---|---|---|
| use_placeholder_api | true | Enable PlaceholderAPI support |
| use_ban_hammer | true | Enable BanHammer integration |
| use_luck_perms | true | Enable LuckPerms integration |
| use_scarpet_events | false | Fire PLAYER_MESSAGE Scarpet events on every chat message |

### [blockbot]

| key | default | description |
|---|---|---|
| bridge_mode | `auto` | `auto` enables the bridge when BlockBot is loaded, `none` disables it |
| global_channel | `chat` | BlockBot channel name used for global chat relay |
| local_channel | *(empty)* | BlockBot channel for local chat  empty to disable |
| discord_format | `[Discord] {message}` | Format applied when passthrough is false |
| passthrough | true | `true`  BlockBot formats Discord to MC messages natively; `false`  viaStyle applies discord_format |
| discord_mention_ping | true | Scan broadcast messages and trigger an in-game ping when a player name is mentioned |
| discord_mention_mappings | *(empty)* | Manual `MinecraftName:DiscordUserId` pairs, comma-separated |

### [pm]

| key | default | description |
|---|---|---|
| allow_self_message | false | Allow players to /msg themselves |
| sender_format | `[PM -> {receiver}] {message}` | Format shown to the sender |
| receiver_format | `[PM <- {sender}] {message}` | Format shown to the receiver |
| color | LIGHT_PURPLE | Color of PM messages |

### [nickcolor]

| key | default | description |
|---|---|---|
| enabled | true | Master toggle for the nick colour system |
| in_chat | true | Apply nick colour to chat messages |
| in_tab | true | Apply nick colour in the tab list |
| in_nametag | true | Apply nick colour to the above-head nametag |
| nametag_show_lp_prefix | true | Include the LuckPerms prefix in the nametag |
| nametag_mode | `display` | `display`  TextDisplay entity (full RGB); `team`  16 vanilla colours |
| nametag_color_strategy | `first` | How to pick a colour from gradients: `first` or `average` |
| nametag_orphan_scan_enabled | true | Periodically remove leftover TextDisplay entities |
| nametag_orphan_scan_interval_ticks | 200 | Scan interval in ticks |

### [tablist]

| key | default | description |
|---|---|---|
| sort_mode | `normal` | `normal`  higher LP weight goes lower; `reverse`  higher goes higher; `none`  disabled |
| sort_spectators_to_bottom | false | Push spectators below all other players |

### [mentions]

| key | default | description |
|---|---|---|
| enabled | true | Process @PlayerName mentions in chat |
| sound | true | Play a sound on the mentioned player client |
| bold | false | Bold the @mention in the message |
| color | GOLD | Highlight colour for mentions |

### [language]

| key | default | description |
|---|---|---|
| default_language | `en` | UI language  `en` or `ru` |

### [joinleave]

| key | default | description |
|---|---|---|
| join_format | `&a+ &r{name}` | Broadcast format when a player joins |
| leave_format | `&c- &r{name}` | Broadcast format when a player leaves |
| first_join_format | `&6+ &r{name} &6joined for the first time!` | Shown only on first join |

### [console]

| key | default | description |
|---|---|---|
| log_global_to_console | true | Forward global chat to the server console |
| log_local_to_console | true | Forward local chat |
| log_staff_to_console | true | Forward staff chat |
| log_privates_to_console | true | Forward private messages |

---

## Format tokens

Most format strings support these tokens:

| token | value |
|---|---|
| `{name}` | Player display name |
| `{message}` | Message content |
| `{prefix}` | Channel prefix tag |
| `{timestamp}` | Timestamp (empty when disabled) |
| `{lp_prefix}` | LuckPerms prefix |
| `{lp_suffix}` | LuckPerms suffix |

Legacy `&`-colour codes and hex colours (`&#RRGGBB`) are supported in all format strings. MiniMessage-style tags (`<red>`, `<bold>`, `<gr:#ff0000:#ffaa00>`) are supported in nick colour values and viaSuper format strings.

---

## Features

### Chat channels

Local chat is visible only within the configured radius. Global chat reaches every player. Staff chat is limited to players with the `viastyle.staff` permission node. The active default channel can be toggled per-player with `/chatmode`.

When `noone_heard` is enabled, the sender receives a private hint if no other player was in range.

### Private messages

`/msg <player> <message>` (aliases `/m`, `/w`) sends a private message. `/reply` (alias `/r`) replies to the last person who wrote to you. Ignored players cannot send PMs. Vanished players are excluded from suggestions.

### @Mentions

Type `@PlayerName` in any message to highlight it and play a sound on the recipient's client. Partial matching is used  `@via` matches `viaMeowts`.

When BlockBot is installed, typing `@MinecraftName` in-game can resolve to a Discord user ID so Discord renders it as a real ping. The mapping can be configured manually in `discord_mention_mappings` or populated automatically from incoming Discord messages.

### Ignore system

`/ignore <player>` toggles ignoring. An ignored player's messages and PMs are silently dropped. State persists across restarts.

### Social Spy

`/socialspy` (requires `viastyle.socialspy`) intercepts private messages server-wide. Staff can monitor local and staff channels from any distance.

### Nick colours

Players set a colour via `/nickcolor <colour>`. Accepts named colours, hex codes (`#rrggbb`), and gradient syntax (`#ff0000:#ffaa00`). Stored in `config/viaStyle/nickcolors.json`.

The colour applies to chat, the tab list, and the floating TextDisplay nametag. Admins can set or clear any player's colour with `/nickcolor <player> <colour|reset>`.

`nametag_mode = display` renders nametags as TextDisplay entities (full RGB). `nametag_mode = team` uses scoreboard teams (16 vanilla colours, better compatibility).

### Tab list

viaStyle sends a custom header and footer to every player each tick. Templates live in `config/viaStyle/tablist.json`. Players with active Carpet HUD loggers (`/log tps`, `/log mobcaps`, etc.) are excluded from updates automatically.

Tab sort order is controlled by `sort_mode` and uses LuckPerms weight when available.

### Join / leave messages

Vanilla join and leave broadcasts are replaced with the configured formats. A separate format is broadcast on a player's very first join.

### viaSuper

`/viasuper [text]` sends a dramatic title/subtitle broadcast. Short words appear as a large title; longer words (length >= `viaSuperSubtitleLength`) appear as a subtitle. MiniMessage formatting and gradients are supported in `viaSuperTitleFormat` and `viaSuperSubtitleFormat`.

### Discord bridge (BlockBot)

When BlockBot is installed, viaStyle relays Minecraft chat to Discord via `ChatMessageEvent` with `sendRelayMessage` as a fallback. Both modern and legacy BlockBot API packages are detected automatically via reflection.

Set `passthrough = true` (default) to let BlockBot handle Discord-to-Minecraft formatting natively. Set `passthrough = false` to use viaStyle's `discord_format` instead.

### BanHammer

When BanHammer is loaded, players who attempt to chat while banned receive the standard BanHammer message.

### Carpet / Scarpet integration

Set `useScarpetEvents = true` to fire Carpet's `PLAYER_MESSAGE` event for every message viaStyle processes. Scarpet scripts can intercept, modify, or cancel messages. Carpet HUD logger detection runs regardless of this setting.

---

## Commands

| command | permission | description |
|---|---|---|
| `/via reload` | `viastyle.admin` | Reload config from disk |
| `/via admin` | `viastyle.admin` | Open the in-game admin panel |
| `/msg <player> <message>` | any | Send a private message |
| `/reply <message>` | any | Reply to the last PM sender |
| `/chatmode` | any | Toggle default channel (local / global) |
| `/ignore <player>` | any | Toggle ignoring a player |
| `/nickcolor [player] <colour\|reset>` | `viastyle.nickcolor` / `viastyle.admin` | Set or clear nick colour |
| `/socialspy` | `viastyle.socialspy` | Toggle social spy |
| `/viasuper [text]` | `viastyle.admin` | Broadcast a title/subtitle message |

---

## Permissions

| node | description |
|---|---|
| `viastyle.admin` | Full admin access: config reload, admin panel, viasuper, set other players nick colours |
| `viastyle.staff` | Write and read staff chat |
| `viastyle.socialspy` | Toggle social spy |
| `viastyle.nickcolor` | Set your own nick colour |

---

## Scarpet scripts

Enable `use_scarpet_events = true` in the config. viaStyle fires Carpet's `PLAYER_MESSAGE` event for each message. A script can return `'cancel'` to suppress the message or return a modified string to replace the content.

`tools/scarpet_generator.py` generates the following templates:

| script | description |
|---|---|
| `viastyle_logger.sc` | Log messages to a file with timestamps |
| `viastyle_antispam.sc` | Per-player rate limiter |
| `viastyle_filter.sc` | Word blocker / censor |
| `viastyle_discord.sc` | Queue-file relay for external Discord bots |
| `viastyle_welcome.sc` | MOTD on join |
| `viastyle_commands.sc` | Hash-prefixed in-chat commands (#help, #roll, #tps) |
| `viastyle_stats.sc` | Per-player message and word count tracker |

```
python tools/scarpet_generator.py --all --out /path/to/scripts
```

Copy generated `.sc` files to `<world>/scripts/` and load with `/script load <name>`.

---

## License

MIT. See `LICENSE`.
