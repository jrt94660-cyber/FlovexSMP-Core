# FlovexMarket

Standalone Paper 26.2 / Java 25 market plugin. No UltimateDonutSMP dependency.

## Build
Run `mvn clean verify` with JDK 25. Output: `target/FlovexMarket-1.0.0.jar`.

## Integrations
Vault economy bridge (ExcellentEconomy is supported through its Vault economy service); optional PlaceholderAPI; optional Geyser/Floodgate detection with inventory GUIs as the common Java/Bedrock fallback; bundled SQLite.

## Commands
`/ah`, `/ah sell <price>`, `/ah search <text>`, `/ah claim`; `/orders`, `/orders create <material> <amount> <unit-price>`, `/orders cancel <id>`; `/sell`, `/sellall`, `/sellhand`, `/sellmulti`, `/worth [material]`; `/settings`.

Config: config.yml, scoreboard.yml, settings.yml, auction-house.yml, orders.yml, worth.yml.
