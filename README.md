# MCCI Tweaks

MCCI Tweaks is a client-side Fabric mod that collects quality-of-life features for MCC Island.

Download the current mod from [GitHub Releases](https://github.com/grovaualreadyknowa/mcci-tweaks/releases/latest).

## Features

- Fishtance automation with a configurable target instance
- Toggleable flashing for MCC Island interfaces
- Material Market inventory tracking
- Chat Separator: splits MCCI chat into All, Party, Rooms, PM, Local, Trade and Plobby tabs, with Champion's Crown level muting, Info message muting and per-category All Chat filters
- In-game configuration through Mod Menu

## Requirements

- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- Fabric API
- Fabric Language Kotlin 1.14.1+kotlin.2.4.20 or newer
- Java 25
- Mod Menu (optional, recommended for configuration)

## Migrating from Chat Separator

Chat Separator is now built into MCCI Tweaks, so remove the standalone `chatsep` jar from your mods folder (the two can't be loaded together). Your existing `config/chatsep.json` settings are imported automatically the first time MCCI Tweaks loads, and the old file is left untouched.

## Source layout

The mod is written in Kotlin (`src/main/kotlin`). Mixins stay in Java (`src/main/java/**/mixin`), which is the usual setup for Kotlin Fabric mods because Mixin works most reliably with Java classes.

## License

MCCI Tweaks is available under the MIT License. See `LICENSE-mcc-no-flash`.
