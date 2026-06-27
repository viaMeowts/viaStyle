# viaStyle

Server-side Fabric mod for Minecraft 1.21.11. Replaces the vanilla chat system with local/global/staff channels, adds per-player nick colours, customisable tab list and nametags, private messaging with sound, mentions, AFK tracking, broadcast commands, join/leave messages, social spy, ignore system, Discord bridge integration, and more.

No client mod required.

---

## Requirements

- Minecraft **1.21.11**
- Fabric Loader `>= 0.16.12`
- Fabric API
- **viaPanel** (separate mod, required for `/viapanel` config UI)

Optional (auto-detected at runtime):

| Dependency | Features enabled |
|---|---|
| LuckPerms | Prefix/suffix in chat and nametag, weight-based tab sorting, permission checks |
| PlaceholderAPI | `%placeholder%` tokens in all format strings |
| BlockBot | Discord chat bridge, Discord mention pings |
| fabric-carpet | HUD logger detection in tab list |
| melius-vanish (DrexHD) | Vanished players hidden from suggestions, tab, and nametags |
| BanHammer | Muted/banned players blocked from chatting and PMs |

---

## Installation

1. Place `viastyle-<version>+mc1.21.11.jar` and `viapanel-<version>+mc1.21.11.jar` into the server's `mods/` folder.
2. Start the server. A default config is generated at `config/viaStyle/viaStyle.toml`.
3. Edit the config as needed, then run `/viaStyle reload` or restart the server.

## Build

viaPanel is built as a separate standalone mod.

1. Build viaPanel: `./gradlew -p viapanel build`
2. Copy `viapanel/build/libs/viapanel-*.jar` to `libs/` in the viaStyle root.
3. Build viaStyle: `./gradlew build`

Output: `build/libs/viastyle-<version>+mc1.21.11.jar`

---

## Features

### Chat Channels

Three independent chat channels with fully customisable formats.

- **Local** — messages visible only within `local_chat_radius` blocks.
- **Global** — messages reach every online player.
- **Staff** — only players with `viastyle.staff` permission see these.

The active default channel can be toggled per-player with `/viaStyle local` / `/viaStyle global`. The trigger character (`!` by default) always routes to global regardless of preference.

If `noone_heard` is enabled, the sender receives a private hint when no other player was in range.

### Private Messages and PM Sound

`/msg <player> <message>` (aliases `/m`, `/w`, `/tell`) sends a private message. `/reply <message>` (alias `/r`) replies to the last sender.

The receiver hears a notification sound (`entity.experience_orb.pickup` by default). Each player can toggle their own PM sound with `/msound` — preference persists across restarts in `config/viaStyle/pm-sound.json`.

BanHammer mute integration: when `pmBanHammerMute` is `true` (default), muted players are also blocked from sending PMs. Set to `false` to allow muted players to contact staff via PMs.

### Mentions

Type `@PlayerName` in any message to highlight the mention and play a sound on the recipient's client. Partial matching is used — `@via` matches `viaMeowts`. Deduplication prevents double-pinging within 2 seconds.

When BlockBot is installed, `@MinecraftName` in Discord messages can be mapped to real Discord pings via `discord_mention_mappings`.

### AFK System

Automatic idle detection with configurable timeout. Players marked AFK receive:

- A suffix appended to their name in chat, tab list, and nametag (`<gray>[AFK]` by default).
- Optional name colour override (`afk_name_color`).
- Bypass via permission (`viastyle.afk.bypass`) or exempt player list.

Movement detection uses player input state (WASD, jump, sneak) — only intentional key presses reset the AFK timer. Environmental forces (water push, knockback, entity push) do not reset AFK.

Manual toggle with `/afk`. Admins can set others AFK with `/afk <player>` or manage the exempt list with `/afk bypass <player>`. Activity resets on movement or chat.

The AFK suffix can be independently disabled via the `afkSuffixEnabled` config option (default `true`), decoupling the toggle from the suffix content.

### Nick Colors and Nametags

Players can set their own nick colour with `/nickcolor <colour>`. Accepts named colours (`red`, `gold`), hex codes (`#ff5555`), and gradients (`#ff0000:#ffaa00`). Colours are stored in `config/viaStyle/nickcolors.json` or via LuckPerms permission nodes (`viastyle.nickcolor.<spec>`).

The colour applies in chat, the tab list, and the above-head nametag. Each can be toggled independently.

Two nametag rendering modes:

| Mode | Description |
|---|---|
| `display` | TextDisplay entity — full RGB, smooth gradients |
| `team` | Scoreboard team prefix — 16 vanilla colours, best compatibility |

Orphan TextDisplay entities are automatically cleaned up by a periodic scan.

### Tab List

Custom header, footer, and player name format configured in `config/viaStyle/tablist.json`. Supports:

- `{name}`, `{ping}`, `{online}`, `{max}`, `{tps}`, `{mspt}`
- `{lp_prefix}`, `{lp_suffix}`, `{afk_suffix}`
- `%player_health%`, `%server_tps_15%` and any PlaceholderAPI token
- Per-viewer vanish-aware player count

Sorting modes: `normal` (higher LP weight = lower), `reverse` (higher = higher), `none`. Spectators can be pushed to the bottom.

### Broadcast System

`/bc <message>` (permission `viastyle.command.broadcast`) sends a server-wide broadcast with:

- Custom header and message formats
- Configurable cooldown per player
- Notification sound (`block.note_block.bell` by default)
- Console sender support (messages sent from console use a configurable name)
- Optional feedback message to the command source

### Join / Leave Messages

Vanilla join/leave broadcasts are replaced with fully customisable formats. A separate format is broadcast on a player's very first join.

Per-player and per-group overrides can be managed through viaPanel or via the `/joinleave` command.

### viaSuper

`/viasuper <text>` sends a dramatic animated title/subtitle broadcast. Short words appear as a large title, longer words (length >= `viaSuperSubtitleLength`) appear as a subtitle. Supports MiniMessage gradients and full formatting. Optional sound effect per word.

### Ignore System

`/ignore <player>` toggles ignoring. An ignored player's messages and PMs are silently dropped. State persists across restarts (per-player file).

### Social Spy

`/socialspy` (permission `viastyle.command.socialspy`) intercepts private messages server-wide. Staff can monitor PM, local, and staff channels. Permission is re-checked on every message — if revoked, spy is auto-disabled.

### Chat Share Placeholders

Type `[item]`, `[pos]`, `[inv]`, or `[ec]` in a message to share your held item, coordinates, inventory, or ender chest. Cooldowns and max-count limits apply. Hover/click formatting is fully customisable.

### Chat MiniMessage

When enabled, players can use MiniMessage tags (`<red>`, `<gradient:#ff0000:#ffaa00>`) in their messages. Can be restricted to players with the `viastyle.chat.minimessage` permission.

### Discord Bridge (BlockBot)

When BlockBot is installed, viaStyle relays Minecraft chat to Discord and can relay Discord chat back to Minecraft. Supports both modern and legacy BlockBot API packages via reflection.

- `passthrough = true` (default) — BlockBot handles Discord-to-Minecraft formatting natively.
- `passthrough = false` — viaStyle applies `discord_format` instead.

Discord messages mentioning `@MinecraftName` trigger an in-game ping (sound + action bar).

### Integrations

| Integration | What works |
|---|---|
| PlaceholderAPI | `%placeholder%` tokens in format strings, tab list, join/leave |
| BanHammer | Muted/banned players blocked from chatting and PMs |
| LuckPerms | Prefix/suffix lookup, weight-based tab sorting, permission checks |
| Carpet | HUD logger suppression in tab list |
| melius-vanish | Vanished players hidden from tab, nametags, PM suggestions |

---

## Configuration

All settings live in `config/viaStyle/viaStyle.toml`. Missing keys are filled with defaults on next load. All settings can also be changed at runtime through `/viapanel`.

### [local]

| Key | Default | Description |
|---|---|---|
| radius | `100.0` | Visibility range in blocks |
| trigger | `""` | Prefix to force local mode (empty = local is default) |
| prefix | `[L]` | Tag prepended to messages |
| prefix_color | `#98FB98` | Color of the prefix tag |
| name_color | `#D9D0D5` | Color of player names |
| message_color | `#D9D0D5` | Color of message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |
| noone_heard | `false` | Notify sender when nobody is in range |
| noone_heard_message | `Nobody heard you.` | Text shown to the sender |

### [global]

| Key | Default | Description |
|---|---|---|
| trigger | `!` | Prefix to route to global chat |
| prefix | `[G]` | Tag prepended to messages |
| prefix_color | `#FCDE9D` | Color of the prefix tag |
| name_color | `#D9D0D5` | Color of player names |
| message_color | `#D9D0D5` | Color of message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |

### [staff]

| Key | Default | Description |
|---|---|---|
| trigger | `\` | Prefix for staff chat (empty = disabled) |
| prefix | `[Staff]` | Tag prepended to messages |
| prefix_color | `#FF5555` | Color of the prefix tag |
| name_color | `#D9D0D5` | Color of player names |
| message_color | `#FF5555` | Color of message text |
| format | `{timestamp}{prefix} {lp_prefix}{name}: {message}` | Full message template |

### [chat_format]

| Key | Default | Description |
|---|---|---|
| minimessage_enabled | `true` | Allow MiniMessage tags in messages |
| minimessage_require_permission | `true` | Require `viastyle.chat.minimessage` to use MiniMessage |
| minimessage_permission | `viastyle.chat.minimessage` | Permission node |

### [timestamp]

| Key | Default | Description |
|---|---|---|
| show | `false` | Prepend a timestamp to each message |
| format | `HH:mm` | Java DateTimeFormatter pattern |
| color | `#B0C4DE` | Color of the timestamp |

### [integrations]

| Key | Default | Description |
|---|---|---|
| use_placeholder_api | `true` | Enable PlaceholderAPI support |
| use_ban_hammer | `true` | Enable BanHammer integration |
| use_luck_perms | `true` | Enable LuckPerms integration |

### [blockbot]

| Key | Default | Description |
|---|---|---|
| bridge_mode | `auto` | `auto` = enable when BlockBot loaded, `none` = disable |
| global_channel | `chat` | BlockBot channel for global chat |
| local_channel | `""` | BlockBot channel for local chat (empty = disabled) |
| discord_format | `[Discord] {message}` | Format (only when passthrough is false) |
| passthrough | `true` | `true` = BlockBot formats Discord-to-MC natively |
| mention_ping | `true` | Ping in-game players when @name appears in broadcast |
| mention_mappings | `""` | Manual `MCNick:DiscordId` pairs, comma-separated |

### [pm]

| Key | Default | Description |
|---|---|---|
| allow_self | `false` | Allow `/msg` to yourself |
| sender_format | `[PM -> {receiver}] {message}` | Format shown to sender |
| receiver_format | `[PM <- {sender}] {message}` | Format shown to receiver |
| color | `#E8CFDF` | Message color |
| sound_enabled | `true` | Play a sound on incoming PM |
| sound_id | `minecraft:entity.experience_orb.pickup` | Sound identifier |
| sound_volume | `1.0` | Sound volume |
| sound_pitch | `1.0` | Sound pitch |
| ban_hammer_mute | `true` | Apply BanHammer mute to PMs |

### [nickcolor]

| Key | Default | Description |
|---|---|---|
| enabled | `true` | Master toggle |
| in_chat | `true` | Apply colour in chat |
| in_tab | `true` | Apply colour in tab list |
| in_nametag | `true` | Apply colour in above-head nametag |
| nametag_mode | `display` | `display` = TextDisplay (full RGB), `team` = 16 colours |
| nametag_lp_prefix | `true` | Show LP prefix in nametag |
| nametag_color_strategy | `first` | `first` or `average` for gradients |
| nametag_orphan_scan_enabled | `true` | Clean up leftover TextDisplay entities |
| nametag_orphan_scan_interval_ticks | `200` | Scan interval (20 ticks = 1 sec) |

### [tablist]

| Key | Default | Description |
|---|---|---|
| sort_mode | `normal` | `normal`, `reverse`, or `none` |
| sort_spectators_to_bottom | `false` | Push spectators below all others |

Tab list templates (header, footer, player name format) are stored in `config/viaStyle/tablist.json`.

### [mentions]

| Key | Default | Description |
|---|---|---|
| enabled | `true` | Process `@PlayerName` mentions |
| sound | `true` | Play sound on mention |
| color | `#FCDE9D` | Highlight colour |

### [chat_placeholders]

| Key | Default | Description |
|---|---|---|
| enabled | `true` | Master toggle |
| item_enabled | `true` | Allow `[item]` |
| pos_enabled | `true` | Allow `[pos]` |
| inv_enabled | `true` | Allow `[inv]` |
| ec_enabled | `true` | Allow `[ec]` |
| cooldown_seconds | `10` | Per-player cooldown |
| max_per_message | `4` | Max placeholders per message |
| expire_seconds | `180` | Inventory/EC snapshot expiry |
| deny_if_no_item | `true` | Block `[item]` when hand is empty |
| let_message_through | `true` | Let message through when placeholder fails |
| item_permission | `viastyle.placeholder.item` | Permission for `[item]` |
| pos_permission | `viastyle.placeholder.pos` | Permission for `[pos]` |
| inv_permission | `viastyle.placeholder.inv` | Permission for `[inv]` |
| ec_permission | `viastyle.placeholder.ec` | Permission for `[ec]` |

### [broadcast]

| Key | Default | Description |
|---|---|---|
| enabled | `true` | Master toggle |
| permission | `viastyle.command.broadcast` | Required to use `/bc` |
| cooldown_seconds | `300` | Per-player cooldown |
| sound_enabled | `true` | Play sound to recipients |
| sound_id | `minecraft:block.note_block.bell` | Sound identifier |
| sound_volume | `1.0` | Sound volume |
| sound_pitch | `1.0` | Sound pitch |
| send_feedback | `true` | Confirm to command source |

### [joinleave]

| Key | Default | Description |
|---|---|---|
| join_format | `<#98FB98>+ <reset>{name}` | Join message format |
| leave_format | `<#FF5555>- <reset>{name}` | Leave message format |
| first_join_format | `<#98FB98>+ <reset>{name} <#D9D0D5>joined for the first time!` | First join format |
| per_player_enabled | `true` | Enable per-player overrides |
| self_permission | `viastyle.joinleave.self` | Bypass per-player join messages |

### [language]

| Key | Default | Description |
|---|---|---|
| default_language | `en` | `en` or `ru` |

### [console]

| Key | Default | Description |
|---|---|---|
| log_global_to_console | `true` | Log global chat |
| log_local_to_console | `true` | Log local chat |
| log_staff_to_console | `true` | Log staff chat |
| log_privates_to_console | `true` | Log PMs |

### [afk]

| Key | Default | Description |
|---|---|---|
| enabled | `true` | Enable AFK detection |
| timeout | `300` | Seconds of inactivity before AFK |
| suffix | `<gray>[AFK]` | Suffix appended to name (MiniMessage) |
| suffix_enabled | `true` | Toggle AFK suffix independently |
| suffix_color | `""` | Color override for suffix |
| name_color | `""` | Color override for AFK name |
| permission | `viastyle.command.afk` | Required for `/afk` |
| bypass_permission | `viastyle.afk.bypass` | Exempt from auto-AFK |
| exempt_players | `""` | Comma-separated UUIDs exempt from auto-AFK |
| enabled_color | `#FCDE9D` | Text color for AFK enabled messages |
| disabled_color | `#98FB98` | Text color for AFK disabled messages |

### [viasuper]

| Key | Default | Description |
|---|---|---|
| word_sound | `false` | Play sound per word |
| subtitle_length | `7` | Words >= this length are shown as subtitle |
| title_format | `<#FCDE9D>{word}` | MiniMessage format for title words |
| subtitle_format | `<#D9D0D5>{word}` | MiniMessage format for subtitle words |

---

## viaPanel Sections

viaStyle registers 19 configuration sections in `/viapanel`:

| Section | Fields |
|---|---|
| Local Chat | `localChatRadius`, `localTrigger`, `localPrefix`, `localPrefixColor`, `localNameColor`, `localMessageColor`, `localFormat`, `localNooneHeard`, `localNooneHeardMessage` |
| Global Chat | `globalTrigger`, `globalPrefix`, `globalPrefixColor`, `globalNameColor`, `globalMessageColor`, `globalFormat` |
| Staff Chat | `staffTrigger`, `staffPrefix`, `staffPrefixColor`, `staffNameColor`, `staffMessageColor`, `staffFormat` |
| Chat Formatting | `chatMiniMessageEnabled`, `chatMiniMessageRequirePermission`, `chatMiniMessagePermission` |
| Timestamp | `showTimestamp`, `timestampFormat`, `timestampColor` |
| Nick Colors | `nickColorEnabled`, `nickColorInChat`, `nickColorInTab`, `nickColorInNametag` |
| Nametag | `nametagMode`, `nametagShowLpPrefix`, `nametagColorStrategy`, `nametagOrphanScanEnabled`, `nametagOrphanScanIntervalTicks` |
| Tab List | `tabSortMode`, `tabSortSpectatorsToBottom` |
| PM | `pmAllowSelfMessage`, `pmSenderFormat`, `pmReceiverFormat`, `pmColor`, `pmSoundEnabled`, `pmSoundId`, `pmSoundVolume`, `pmSoundPitch`, `pmBanHammerMute` |
| Mentions | `mentionsEnabled`, `mentionSound`, `mentionColor` |
| Broadcast | `broadcastEnabled`, `broadcastPermission`, `broadcastCooldownSeconds`, `broadcastHeaderFormat`, `broadcastMessageFormat`, `broadcastConsoleSenderName`, `broadcastCooldownFormat`, `broadcastFeedbackFormat`, `broadcastLogFormat`, `broadcastSoundEnabled`, `broadcastSoundId`, `broadcastSoundVolume`, `broadcastSoundPitch`, `broadcastSendFeedback` |
| Join / Leave | `joinFormat`, `leaveFormat`, `firstJoinFormat`, `joinLeavePerPlayerEnabled` |
| Join/Leave Overrides | `joinLeavePanelPlayerTarget`, `joinLeavePanelPlayerJoinFormat`, `joinLeavePanelPlayerLeaveFormat`, `joinLeavePanelGroupTarget`, `joinLeavePanelGroupJoinFormat`, `joinLeavePanelGroupLeaveFormat` |
| Console | `logGlobalToConsole`, `logLocalToConsole`, `logStaffToConsole`, `logPrivatesToConsole` |
| Language | `defaultLanguage` |
| BlockBot | `discordBridgeMode`, `blockbotGlobalChannel`, `blockbotLocalChannel`, `discordFormat`, `discordPassthrough`, `discordMentionPing`, `discordMentionMappings` |
| Integrations | `usePlaceholderApi`, `useBanHammer`, `useLuckPerms`, `useScarpetEvents` |
| viaSuper | `viaSuperWordSound`, `viaSuperSubtitleLength`, `viaSuperTitleFormat`, `viaSuperSubtitleFormat` |
| AFK | `afkEnabled`, `afkTimeout`, `afkSuffix`, `afkSuffixEnabled`, `afkSuffixColor`, `afkNameColor`, `afkPermission`, `afkBypassPermission`, `afkExemptPlayers`, `afkEnabledColor`, `afkDisabledColor` |

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/viaStyle reload` | `viastyle.command.reload` (or OP 2) | Reload config from disk |
| `/viaStyle local` / `global` | `viastyle.command.chatmode` | Toggle default chat channel |
| `/viaStyle lang [en\|ru]` | `viastyle.command.lang` | Show or change language |
| `/viapanel` | `viastyle.panel` (or OP 2) | Open config UI |
| `/msg <player> <message>` | `viastyle.command.msg` | Send private message |
| `/m`, `/w`, `/tell` | `viastyle.command.msg` | Aliases for `/msg` |
| `/reply <message>` | `viastyle.command.reply` | Reply to last PM sender |
| `/r` | `viastyle.command.reply` | Alias for `/reply` |
| `/ignore <player>` | `viastyle.command.ignore` | Toggle ignoring a player |
| `/unignore <player>` | `viastyle.command.ignore` | Unignore a player |
| `/nickcolor <colour>` | `viastyle.command.nickcolor.preview` | Preview a colour format |
| `/nickcolor set <player> <colour>` | `viastyle.command.nickcolor` (or OP 2) | Set a player's nick colour |
| `/nickcolor remove <player>` | `viastyle.command.nickcolor` (or OP 2) | Remove a player's override |
| `/nickcolor reload` | `viastyle.command.nickcolor` (or OP 2) | Reload nick colours from file |
| `/socialspy` | `viastyle.command.socialspy` | Toggle social spy |
| `/viasuper <text>` | `viastyle.command.viasuper` (or OP 2) | Broadcast title/subtitle |
| `/bc <message>` | `viastyle.command.broadcast` (or OP 2) | Send a server broadcast |
| `/afk` | `viastyle.command.afk` | Toggle own AFK status |
| `/afk <player>` | `viastyle.command.afk` + `viastyle.afk.others` (or OP 2) | Toggle another player's AFK |
| `/afk bypass <player>` | `viastyle.afk.bypass.manage` (or OP 2) | Toggle exempt status |
| `/afk bypass list` | `viastyle.afk.bypass.manage` (or OP 2) | List exempt players |
| `/msound` | `viastyle.command.msound` | Toggle own PM sound |
| `/msound on\|off` | `viastyle.command.msound` | Enable/disable PM sound |
| `/msound status` | `viastyle.command.msound` | Show current PM sound state |
| `/joinleave` | `viastyle.command.joinleave` | Manage join/leave overrides |
| `/placeholders` | -- | Show available chat placeholders help |
| `/viaStyle version` | -- | Show installed viaStyle version |

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `viastyle.command.reload` | OP 2 | `/viaStyle reload` |
| `viastyle.command.chatmode` | All | `/viaStyle local` / `global` |
| `viastyle.command.lang` | All | `/viaStyle lang` |
| `viastyle.command.msg` | All | `/msg` / `/m` / `/w` / `/tell` |
| `viastyle.command.reply` | All | `/reply` / `/r` |
| `viastyle.command.ignore` | All | `/ignore` / `/unignore` |
| `viastyle.command.nickcolor` | OP 2 | `/nickcolor set\|remove\|reload` |
| `viastyle.command.nickcolor.preview` | All | `/nickcolor <colour>` |
| `viastyle.command.socialspy` | OP 2 | `/socialspy` |
| `viastyle.command.viasuper` | OP 2 | `/viasuper` |
| `viastyle.command.broadcast` | OP 2 | `/bc` |
| `viastyle.command.afk` | All | `/afk` |
| `viastyle.afk.bypass` | -- | Exempt from automatic AFK detection |
| `viastyle.afk.bypass.manage` | OP 2 | `/afk bypass <player>` |
| `viastyle.command.msound` | All | `/msound` |
| `viastyle.command.joinleave` | -- | `/joinleave` |
| `viastyle.staff` | -- | Access staff chat |
| `viastyle.panel` | OP 2 | `/viapanel` access to viaStyle config |
| `viastyle.pm.vanished` | OP 2 | Allow PM to vanished players |
| `viastyle.joinleave.self` | -- | Receive own join/leave messages |
| `viastyle.socialspy` | OP 2 | Legacy social spy permission |
| `viastyle.chat.minimessage` | -- | Use MiniMessage tags in chat |
| `viastyle.placeholder.item` | -- | Use `[item]` in chat |
| `viastyle.placeholder.pos` | -- | Use `[pos]` in chat |
| `viastyle.placeholder.inv` | -- | Use `[inv]` in chat |
| `viastyle.placeholder.ec` | -- | Use `[ec]` in chat |
| `viastyle.nickcolor.<spec>` | -- | Set nick colour via LP permission |

---

## Format Tokens and Placeholders

### Chat format tokens

| Token | Description |
|---|---|
| `{name}` | Player display name (clickable, suggests `/msg`) |
| `{message}` | Message content (with `[item]`/`[pos]`/etc. expanded) |
| `{prefix}` | Channel prefix tag |
| `{timestamp}` | Timestamp (empty when disabled) |
| `{lp_prefix}` | LuckPerms prefix (MiniMessage) |
| `{lp_suffix}` | LuckPerms suffix (MiniMessage) |
| `%papi_token%` | Any PlaceholderAPI token (requires PAPI) |

### Tab list tokens

| Token | Description |
|---|---|
| `{name}` | Player name |
| `{ping}` | Player ping in ms |
| `{online}` | Vanish-aware visible player count |
| `{max}` | Server max players |
| `{tps}` | Server TPS (colored) |
| `{mspt}` | Server MSPT (colored) |
| `{lp_prefix}` | LuckPerms prefix |
| `{lp_suffix}` | LuckPerms suffix |
| `{afk_suffix}` | AFK suffix (empty when not AFK or suffix disabled) |
| `{player}` | Player name with nick colour applied (for `playerNameFormat` only) |
| `%papi_token%` | Any PlaceholderAPI token |

### Join / Leave tokens

| Token | Description |
|---|---|
| `{name}` | Player display name |

### viaSuper tokens

| Token | Description |
|---|---|
| `{word}` | The word being displayed |

### Broadcast tokens

| Token | Description |
|---|---|
| `{sender}` | Command sender name |
| `{message}` | Broadcast message text |
| `{count}` | Recipient count |

### Chat Share Placeholders

| Token | Description |
|---|---|
| `[item]` | Share held item (hover shows tooltip) |
| `[pos]` | Share coordinates (click to `/tp`) |
| `[inv]` | Share inventory (hover shows GUI) |
| `[ec]` | Share ender chest (hover shows GUI) |

### Colour Formats

All colour fields support:
- Named colours: `red`, `gold`, `light_purple`, etc.
- Hex: `#FF5555`, `#98FB98`
- Gradients: `<gradient:#ff0000:#ffaa00>text</gradient>` (MiniMessage)
- Legacy: `&c` `&6` `&#FF5555` (auto-converted to MiniMessage)

---

## License

MIT. See `LICENSE`.
