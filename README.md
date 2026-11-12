# FlovexCore

FlovexCore is the modular core plugin for FlovexSMP. It is a standalone Paper plugin and does not depend on UltimateDonutSMP2.

## Build requirements

- Paper 26.2+
- Java 25
- Maven 3.9+
- Vault + ExcellentEconomy for marketplace payments
- Optional PlaceholderAPI
- Optional Geyser + Floodgate for Bedrock platform detection
- Optional LibsDisguises for the `/disguise` bridge

Run:

```bash
mvn -B clean package
```

The output is `target/FlovexCore.jar`. GitHub Actions builds on Temurin Java 25 and uploads an artifact named **FlovexCore**.

## Implemented feature groups

### Marketplace
- Built-in item selling, sell menus, sell commands, worth pricing, and sell statistics are excluded; use your separate selling plugin.
- Auction House: list, search, view own listings, cancel listings, buy listings, and claim purchases/proceeds
- Player buy orders with reserved payments, cancellation and expiry refunds
- Vault economy bridge (ExcellentEconomy through Vault) is used only for marketplace transactions

### Player systems
- UUID-based SQLite player records and persistent playtime/statistics
- TPA, TPAHere, accept/deny/cancel, separate TPA and TPAHere auto-accept settings
- Friends, private messages, reply, ignore/unignore and player profiles
- Player settings, scoreboard toggle, statistics, playtime and leaderboards
- Teams with invites, member limits, team chat, team homes and friendly-fire toggle
- Named warps and warp management
- Configurable BungeeCord portals for proxy server transfers

### Moderation and staff
- Player reports and staff report queue
- Persistent chat logs, mute/temp-mute, ban/temp-ban, warn, kick, unban/unmute, blacklist and offense records
- Chat mute, clear, enable/disable, slowmode and blocked-word filtering
- Freeze, vanish, staff chat/list/helpop, inventory inspection, teleport, fly, heal, feed, gamemode, god mode and other staff utilities

### Utilities
- ping, rules/help/rank information, item rename, clear dropped items and player logs
- Optional disguise bridge when LibsDisguises is installed
- Optional voice-chat consent preference storage
- Phantom-spawn preference, spawner inspection/management and staff cuboid selection utility

## Java and Bedrock UI

Java players receive interactive Paper native Dialog menus. Floodgate-detected Bedrock players receive chest-inventory fallbacks. Menus are command-backed, so the same feature commands remain available on both platforms.

## Integrations and placeholders

- Vault is accessed only by the Auction House and Orders services; FlovexCore does not add a balance, pay command, economy or currency.
- ExcellentEconomy is supported through its Vault economy provider.
- PlaceholderAPI is optional. FlovexCore registers these placeholders when PlaceholderAPI is present:
  - `%flovexcore_kills%`
  - `%flovexcore_deaths%`
  - `%flovexcore_blocks_broken%`
  - `%flovexcore_blocks_placed%`
  - `%flovexcore_playtime%`
  - `%flovexcore_team%`
  - `%flovexcore_ping%`
  - `%flovexcore_platform%`

## Data and configuration

Player data is stored in `plugins/FlovexCore/flovexcore.db` and keyed by UUID. SQLite is loaded through Paper's plugin library mechanism.

Portal transfers use the BungeeCord plugin-message channel and require a BungeeCord-compatible proxy with the destination server names configured in `network.yml`.

Configuration files include:

- `config.yml` — global feature defaults
- `database.yml` — SQLite file and connection settings
- `auction-house.yml`, `orders.yml` — marketplace limits and prices
- `scoreboard.yml`, `settings.yml`, `menus.yml` — player UI and defaults
- `teleport.yml` — teleport controls
- `teams.yml`, `warps.yml`, `network.yml` — team, warp and proxy portal settings
- `messages.yml`, `sounds.yml`, `moderation.yml`, `rules.yml` — text, audio, moderation and rules

Permissions are declared in `plugin.yml`. Staff access defaults to OP; player feature permissions default to true.

## Explicit exclusions

Homes, RTP, RTP queue, Duels, duel queue, FFA and Ranked PvP are handled by separate FlovexSMP plugins and are not registered or implemented by FlovexCore.

FlovexCore intentionally does **not** implement Shop, Spawn, AFK, Crates, Broadcasts, Store, Discord, Media, combat-tag, Ender Chest, a custom Economy, Shards, or Maintenance. It does not create a new currency.

## FlovexCore 1.0.5 rebuild

The `flovexcore-rebuild` branch adds LuckPerms rank priority display, a tablist header/footer, and `/rtp` with Overworld, Nether, and End choices. The LuckPerms groups remain managed by LuckPerms; `luckpermsranks.yml` only controls display priority.
