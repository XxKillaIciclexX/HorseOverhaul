# Pre-Release Changelog

Tracks compatibility work for the Hytale Update 5 pre-release server jar at [libs/pre-release/HytaleServer.jar](libs/pre-release/HytaleServer.jar).

## Update 5 Preparation - 2026-04-23

### Status

- Current `0.5.1` source does **not** compile cleanly against the Update 5 pre-release server jar.
- Direct compile check against `libs/pre-release/HytaleServer.jar` found blocking API changes in the math/vector surface used by horse leash and anchor handling.

### Completed

- Replaced the removed `com.hypixel.hytale.math.vector.Vector3d` usages with `org.joml.Vector3d` in:
  - [src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java>)
  - [src/main/java/me/icicle/plugin/saddle/SaddleActions.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/saddle/SaddleActions.java>)
- Follow-up compile check against `libs/pre-release/HytaleServer.jar` confirms `Vector3d` is no longer part of the blocking error list.
- Replaced the deleted `com.hypixel.hytale.math.vector.Vector3f` usages with `com.hypixel.hytale.math.vector.Rotation3f` in:
  - [src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java>)
  - [src/main/java/me/icicle/plugin/saddle/SaddleActions.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/saddle/SaddleActions.java>)
- Follow-up compile check against `libs/pre-release/HytaleServer.jar` confirms `Vector3f` is no longer part of the blocking error list.
- Replaced the old `distanceSquaredTo(...)` horse-anchor drift check with JOML-compatible `distanceSquared(...)` math in:
  - [src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java>)
- Follow-up compile check against `libs/pre-release/HytaleServer.jar` confirms the old position-distance API is no longer part of the blocking error list.
- Updated `Rotation3f` API usage in:
  - [src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java>)
  Changes completed:
  - `getPitch()`, `getYaw()`, and `getRoll()` replaced with `pitch()`, `yaw()`, and `roll()`
  - `assign(...)` replaced with `set(...)`
- Follow-up compile check against `libs/pre-release/HytaleServer.jar` now passes successfully with warnings only.

### Required Changes

No additional blocking code changes are currently required for the first Update 5 compatibility pass.

### Warning-Only Follow-Ups

- `Entity#getNetworkId()` is deprecated for removal in the pre-release jar.
  Current callsites:
  - [src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/input/SaddleInputInterceptor.java>)

- `Inventory#getSectionById(...)` is deprecated for removal in the pre-release jar.
  Current callsites:
  - [src/main/java/me/icicle/plugin/saddle/SaddleActions.java](</D:/Hytale Mods/HorseOverhaul/src/main/java/me/icicle/plugin/saddle/SaddleActions.java>)

These are not blocking the first compatibility pass, but they should be revisited if Update 5 removes them later in the cycle.

### Checked And Not Currently Impacted

The current codebase does not appear to require changes for the other Update 5 modder-warning items:

- `Player` no longer implementing `CommandSender` / `PermissionHolder`
- `CommandSender#getDisplayName()` renamed to `getUsername()`
- removed `ShutdownReason#withMessage(String)`
- removed old `Matrix4d`
- `Universe#getPlayers()` return type change

### Notes

- This file is intended to be updated incrementally as Update 5 compatibility work is completed.
- Normal player-facing release notes remain in [CHANGELOG.md](CHANGELOG.md).
