# FlovexCore

FlovexCore is the standalone Paper core for FlovexSMP. It is being rebuilt as a source-first plugin in this repository; it does not load or depend on the old UltimateDonutSMP2 plugin.

## Build

- Paper 26.2+
- Java 25
- Maven 3.9+
- Vault-compatible economy provider for money features (ExcellentEconomy supported through Vault)
- Optional: LuckPerms, PlaceholderAPI, Geyser + Floodgate, LibsDisguises

Run `mvn -B clean verify`. GitHub Actions builds the versioned JAR.

## Included systems

### Economy, shop, and selling
- 54-slot Quick Buy shop backed by the full worth catalog in `worth.yml`
- Shop buy prices use a 20x worth multiplier
- Sell prices are 50% of the resulting shop price; sell value cannot exceed shop price
- Confirmation menu before money is charged or items are removed
- `/sell`, `/sellhand`, `/sellall`, `/worth`, `/sellhistory`, and `/topsell`
- Shard balance, shard payments, kill rewards, and the configured Shard Shop catalog through the Bow item
- Auction House listings/search/buy/claim/cancel and player buy orders with reserved funds, expiry refunds, and claims

### Player and social systems
- TPA, TPAHere, accept/deny/cancel, auto-accept preferences, and a queued Overworld RTP
- `/rtp` native Paper dialog with exactly three world buttons: Overworld, Nether, End
- Friends, private messages, reply, ignore/unignore, profiles, stats, playtime, ping, and leaderboards
- Teams with invites, member limits, team chat, team homes, and friendly-fire control
- Named warps and configurable proxy portals

### Chat, settings, and presentation
- LuckPerms group-priority loading from `luckpermsranks.yml`; this file only controls display order and never creates or edits LuckPerms groups
- Tablist and scoreboard display the selected LuckPerms group prefix only; they do not concatenate inherited prefixes
- FlovexSMP gradient tablist, online count, branded scoreboard, compact money formatting (k/m/b/t/q/qq)
- Chat formatting uses the selected LuckPerms prefix and FlovexSMP blue styling
- Settings categories: Chat, Notifications, PvP, Visuals, Privacy, Scoreboard, General
- Privacy category places TPA Auto first; settings persist per UUID
- Java clients receive native Paper Dialogs; Bedrock clients receive chest-GUI fallbacks where supported

### Moderation and staff
- Reports and report queue, chat logs, chat mute/slowmode/filter, mute/ban/temp punishments, warnings, blacklist records
- Freeze, vanish, staff chat/list/helpop, inventory inspection, teleport, fly, heal/feed, gamemode, god mode, and staff utilities

### Utilities and integrations
- Rules/help/rank information, item rename, clear dropped items, player logs, phantom preference, spawner inspection/management, cuboid selection, voice-chat consent
- UUID-based SQLite player data, preferences, stats, social records, teams, marketplace, moderation, sales, and shard balances
- PlaceholderAPI expansion for kills, deaths, blocks, playtime, team, ping, platform, money, and shards

## Configuration

- `config.yml` — prefix, chat/title style, tablist, RTP, shop, and shard settings
- `luckpermsranks.yml` — highest-to-lowest display priority
- `shop.yml` and `worth.yml` — Quick Buy, Shard Shop, and item values
- `scoreboard.yml`, `settings.yml`, `menus.yml` — scoreboard and player UI
- `auction-house.yml`, `orders.yml` — marketplace limits and expiry
- `teleport.yml`, `teams.yml`, `warps.yml`, `network.yml` — player movement and network systems
- `messages.yml`, `sounds.yml`, `moderation.yml`, `rules.yml` — messages and moderation

## Not part of this core

The separate FlovexSMP plugins remain responsible for Homes, Spawn, AFK, Crates, Broadcasts, Store, Discord, Media, combat-tag, Ender Chest, Duels/duel queue, FFA, Ranked PvP, and Maintenance. FlovexCore does not install or manage LuckPerms groups.
