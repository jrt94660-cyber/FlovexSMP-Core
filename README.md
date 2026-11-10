# FlovexCore

Modular Paper plugin for FlovexSMP. Java 25, Maven, Paper 26.2+.

## Build
Run `mvn clean verify`; output is `target/FlovexCore.jar`. GitHub Actions builds using Temurin 25 and uploads the artifact.

## Integrations
Vault + ExcellentEconomy are used only for Auction House and player buy-order payments. PlaceholderAPI and Geyser/Floodgate are optional integrations. Player data is stored in SQLite by UUID.

## UI
Java clients use Paper's native Dialog API. Floodgate Bedrock clients use chest-inventory menus.

## Excluded
No Shop, Spawn, AFK, Crates, Broadcasts, Store, Discord, Media, combat-tag, Ender Chest, custom Economy, Shards, or Maintenance modules.