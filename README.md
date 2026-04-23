# Horse Overhaul

Horse Overhaul adds a craftable saddle item, mounted and unmounted horse inventory access, and a custom saddled-horse behavior set for Hytale servers.

The current release targets server build `2026.03.26-89796e57b`.

## Features

- Adds a craftable `Saddle` item (`Horse_Saddle`) with built-in storage.
- Saddling a `Horse` or `Tamed_Horse` also tames it and swaps it to the custom `Horse_Overhaul_Saddled` role.
- Supports horse inventory access in both major play states:
  - unmounted `Primary Action` on a saddled horse opens the horse inventory
  - mounted `Inventory` key opens the same horse inventory
- Supports direct saddle inventory access:
  - `Use` while holding the saddle opens the saddle bag
- Keeps saddled horses from passively wandering away while still preserving normal livestock sleep and grazing behavior.
- Includes the `/horseoverhaul` in-game help command.
- Supports config-driven saddle storage size.
- Supports config-driven enabling or disabling of petting on saddled horses.

## Gameplay

### Crafting

Craft the saddle at the `Farming Bench`.

Recipe:

- `3x Light Leather`
- `1x Copper Ingot`

### Equipping

1. Put the saddle in your hotbar.
2. Use `Secondary Action` on a `Horse` or `Tamed_Horse`.
3. The saddle is equipped and the horse becomes saddled and tame.

### Using The Inventories

- `Use` while holding the saddle opens the saddle's storage directly.
- `Primary Action` on an unmounted saddled horse opens the horse inventory.
- Press your normal `Inventory` key while mounted to open the horse inventory.

The horse inventory currently exposes:

- the equipped saddle slot
- the saddle bag storage
- locked placeholder slots reserved for future horse gear expansion

## Command

- `/horseoverhaul`
  Opens the in-game help window with crafting, usage, and known bug information.

## Config

Horse Overhaul creates a `HorseOverhaul.properties` file beside the mod jar on first startup.

Default config:

```properties
saddle_storage_slots=9
saddled_horse_petting_enabled=false
```

### `saddle_storage_slots`

- Controls how many storage slots the saddle bag has.
- Must be a positive multiple of `9`.
- Saddle rows are derived automatically as `saddle_storage_slots / 9`.
- Default `9` keeps the original layout:
  - saddle bag: `9` slots / `1` row
  - horse inventory: `18` slots / `2` rows

### `saddled_horse_petting_enabled`

- `false` by default.
- When `false`, saddled horses do not expose the livestock pet interaction.
- This prevents petting from interrupting the saddled horse's mount and inventory interaction flow.
- When `true`, the mod uses a dedicated pettable saddled-horse role instead.

Restart the server after changing config values.

## Installation

1. Build or download the jar.
2. Place the jar in the mod load path you use for Hytale server mods.
   For local testing, this is commonly `%APPDATA%\\Hytale\\UserData\\Mods`.
3. Start the server so `HorseOverhaul.properties` can be generated if it does not exist yet.
4. Restart after changing config values.

## Building

Build with Gradle:

```powershell
.\gradlew.bat build
```

Output jar:

```text
build/libs/HorseOverhaul-0.5.0.jar
```

## Known Limitations

- Removing the saddle while still mounted can leave the rider floating client-side until they press the dismount button, even though the player is already dismounted server-side.
- The horse inventory is still prototype-backed by the equipped saddle item rather than a full native horse entity inventory.

## Changelog

Release notes are tracked in [CHANGELOG.md](CHANGELOG.md).
