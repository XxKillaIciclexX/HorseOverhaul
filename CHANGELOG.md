# Changelog

## 0.5.0 - 2026-04-22

Changes since `0.4.3`.

### Changed

- Completed the first end-to-end `Horse_Overhaul_Saddled` inventory access flow so the combined horse inventory can now be opened while unmounted and while riding, using the same prototype-backed saddle and bag data model.
- Reworked mounted horse inventory opening to resolve the currently ridden saddled horse on the world thread and retry only until the first successful open, reducing the earlier multi-open flicker when replacing the mounted player's pocket-crafting page.
- Reworked mounted saddle removal to stage the horse unsaddle after the mounted inventory closes, separating the dismount/reset path from the later role swap so removing the saddle no longer causes the horse to disappear from the world.

### Fixed

- Fixed the long-standing high-priority gap where mounted players could not open the horse inventory at all; mounted `Horse_Overhaul_Saddled` horses now expose the same combined inventory view as unmounted horses.
- Fixed the mounted inventory open instability where repeated forced window replacement attempts could cause visible flicker during horse inventory open.
- Fixed the mounted unsaddle regression where removing the saddle from the horse inventory could close the window and leave the horse disappearing during the saddled-to-tamed role transition.

### Known Bugs

- High Priority: Removing the saddle while still mounted can leave the rider visually mounted on the now-unsaddled horse until they manually dismount, even though the horse inventory closes and the horse remains in the world.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.

## 0.4.3 - 2026-04-22

Changes since `0.4.2`.

### Changed

- Reworked `Horse_Overhaul_Saddled` to use a local no-wander livestock template so the saddled horse keeps the stock livestock sleep and grazing lifecycle without reintroducing passive roaming.
- Replaced the passive idle wander branch in the local livestock template with a stationary idle loop for saddled-horse behavior control.

### Fixed

- Fixed the regression where saddled horses would stand motionless forever by restoring night sleep and daytime eating behavior on `Horse_Overhaul_Saddled`.
- Fixed the regression where `Primary Action` inventory-open attempts could still cause the saddled horse to flee by teaching the saddled-horse damage check to ignore friendly player hits.
- Fixed the follow-up role validation regression where `Horse_Overhaul_Saddled` could disappear on saddle because the custom damage-check component failed NPC asset validation at load time.

### Known Bugs

- High Priority: Mounted horse inventory access is still unresolved; the horse inventory prototype only supports unmounted saddled horses.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.

## 0.4.2 - 2026-04-22

Changes since `0.4.1`.

### Fixed

- Patched the remaining saddle and horse inventory hotbar-slot duplication path by denying cross-window swaps that involve the player's currently active hotbar slot while those prototype inventory windows are open.
- Fixed the related inventory desync where an active-slot swap could appear to succeed temporarily, then restore the original saddle or horse inventory item on reopen while leaving the swapped item in the player's possession.
- Restored direct saddle removal from the horse inventory window now that the active-hotbar cross-window dupe path is blocked, allowing the equipped saddle slot to function normally again.

### Known Bugs

- High Priority: Mounted horse inventory access is still unresolved; the horse inventory prototype only supports unmounted saddled horses.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.

## 0.4.1 - 2026-04-22

Changes since `0.4.0`.

### Changed

- Pinned the plugin manifest `ServerVersion` to the exact local Hytale server build `2026.03.26-89796e57b` so the game no longer treats the plugin target as a version-range mismatch on join.
- Refined the horse-inventory locked-slot placeholder presentation with dedicated lock-icon item art and updated item resource properties for clearer in-inventory feedback.
- Updated the `/horseoverhaul` help window copy and layout with a dedicated `Usage` section, including instructions for opening the saddle inventory with the `Use` key while holding the saddle.

### Known Bugs

- High Priority: Mounted horse inventory access is still unresolved; the horse inventory prototype only supports unmounted saddled horses.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.
- Medium Priority: Because the saddle-slot dupe exploit was patched at the prototype container level, the equipped saddle is currently displayed read-only in the horse inventory window instead of being removable from that window.

## 0.4.0 - 2026-04-22

Changes since `0.3.1`.

### Changed

- Reworked the unmounted `Horse_Overhaul_Saddled` inventory prototype from separate native windows into a single combined native horse inventory window so the equipped saddle is shown directly in the horse inventory above the saddle bag contents.
- Updated the horse inventory prototype so the saddled horse's storage view now presents the saddle and bag state together instead of opening only the saddle bag on its own.
- Temporarily hardened the prototype horse gear slot into a read-only display slot while the horse inventory system remains prototype-backed, preventing direct saddle removal from that transient window until a safer horse-backed slot flow is implemented.
- Refreshed the horse-inventory placeholder item presentation so the reserved locked slots now use dedicated lock-icon item art instead of a generic prototype placeholder look.
- Updated horse item metadata to better match the intended player-facing presentation, including saddle item property cleanup in the shipped item resource definitions.

### Fixed

- Fixed the prototype bug where unmounted `Primary Action` could open only the saddle bag and leave the horse gear window missing or visually broken; the horse inventory now reliably shows the saddle itself in the horse inventory view.
- Fixed the horse inventory teardown path so unsaddling now explicitly closes open inventory windows instead of only changing the page state.
- Patched the saddle duplication exploit triggered by removing the equipped saddle from the open horse inventory and placing it into the active hotbar slot used to open the inventory.

### Known Bugs

- High Priority: Mounted horse inventory access is still unresolved; the horse inventory prototype only supports unmounted saddled horses.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.
- Medium Priority: Because the saddle-slot dupe exploit was patched at the prototype container level, the equipped saddle is currently displayed read-only in the horse inventory window instead of being removable from that window.

## 0.3.1 - 2026-04-22

Changes since `0.3.0`.

### Added

- Added a first-pass horse inventory prototype for unmounted `Horse_Overhaul_Saddled` horses using a native 1-slot `Horse Gear` window alongside the existing saddle bag window.

### Changed

- Unmounted saddled-horse inventory access is now prototyped on `Primary Action` so left-clicking a saddled horse opens its horse inventory instead of treating the interaction as an attack.
- The horse inventory prototype now exposes the equipped saddle as a horse-side slot first, then opens the saddle bag from that equipped saddle state instead of only opening the bag directly.
- Removing the saddle from the prototype `Horse Gear` slot now clears the horse's equipped-saddle state, closes the inventory view, and reverts the horse back to the `Tamed_Horse` role.
- Refined the `/horseoverhaul` help window presentation by removing the framed section boxes, removing the saddle icon border, and increasing the `Horse Overhaul` title size.

### Known Bugs

- High Priority: Mounted horse inventory access is still unresolved; the new horse inventory prototype only targets unmounted saddled horses.
- High Priority: The horse inventory implementation is still a prototype backed by the horse's stored equipped-saddle item state rather than a full native horse entity inventory layout.

## 0.3.0 - 2026-04-20

Changes since `0.2.3`.

### Added

- Added the `/horseoverhaul` player help command to open an in-game Horse Overhaul help window.
- Added a dedicated custom help page that explains where to craft the saddle, what it costs, how to equip it, and the current known bugs.

### Changed

- On April 21, 2026, verified that the locally installed release `HytaleServer.jar` still reports `2026.03.26-89796e57b`, matching the previous target server build.
- Widened the plugin manifest `ServerVersion` requirement to `>=2026.03.26-89796e57b` after confirming no Horse Overhaul code changes were needed for the currently installed release server jar.

### Fixed

- Fixed the `/horseoverhaul` command permissions so normal Adventure and Creative players can use it without operator privileges.
- Fixed the help window's CustomUI document lookup failures by loading it from a dedicated `Common/UI/Custom/Pages/HorseOverhaulHelp.ui` page asset.
- Fixed repeated help-window rendering and load failures caused by external UI document and texture dependencies by switching the page to self-contained local panel styling.

### Known Bugs

- Critical Priority: Secondary Action on a saddled horse is still not opening the saddle inventory as intended.
- High Priority: Saddle inventories are currently inaccessible while mounted, including attempts to access the equipped saddle while riding.

## 0.2.3 - 2026-04-20

Changes since `0.2.2`.

### Fixed

- Fixed the `0.2.2` regression where `Secondary Action` with the saddle could leave the saddle in the player's inventory because `Horse_Overhaul_Saddled` failed validation and never loaded as a valid role.
- Fixed saddled horses continuing to wander back to their tame location; the validated standalone `Horse_Overhaul_Saddled` role now keeps the saddled horse from inheriting livestock wander behavior.

### Known Bugs

- Critical Priority: Secondary Action on a saddled horse is still not opening the saddle inventory as intended.
- High Priority: Saddle inventories are currently inaccessible while mounted, including attempts to access the equipped saddle while riding.

## 0.2.2 - 2026-04-20

Changes since `0.2.1`.

### Changed

- Saddled-horse leash-anchor syncing now follows the horse's server-side transform while mounted instead of relying on cached mounted movement telemetry.
- Saddled-horse dismount handling now retries leash-anchor refreshes on short delayed intervals after dismount so the horse's final server transform can be reapplied after mount-system updates complete.
- Recently ridden saddled horses are now tracked for several minutes after riding and have their leash anchor re-centered repeatedly from the horse's current server transform as a stronger workaround against hidden leash resets.
- Saddled-horse leash-anchor updates now use the NPC API's `saveLeashInformation(...)` path when rotation data is available instead of only mutating leash fields directly.
- The `Horse_Overhaul_Saddled` role no longer participates in horse flock membership, preventing flock-driven leash updates from overwriting the post-dismount wander anchor.
- The `Horse_Overhaul_Saddled` role is now defined as a standalone `Generic` role with `BodyMotion: Nothing` while idle, instead of inheriting `Template_Livestock` and its built-in livestock wander logic.

### Fixed

- Replaced the invalid `WanderRadius: 0` override that caused `Horse_Overhaul_Saddled` role validation failures during role changes.
- Reduced saddled-horse wander to a minimal validated radius and disabled daytime naps so horses no longer try to sleep immediately after dismount.
- Fixed a `0.2.2` regression where the standalone `Horse_Overhaul_Saddled` role still referenced a nonexistent `Sleep` state, causing the role to fail validation at load and preventing saddle equips from consuming the saddle item.
- Fixed the `SaddleInputInterceptor` regression that was reading entity-store components off the packet thread during mounted saddle handling, which was spamming `Assert not in thread!` errors in server logs.

### Known Bugs

- Critical Priority: Secondary Action on a saddled horse is still not opening the saddle inventory as intended.
- High Priority: Saddle inventories are currently inaccessible while mounted, including attempts to access the equipped saddle while riding.
- High Priority: Saddled horses can still wander back to their original tame or saddle location instead of remaining centered on the player's last dismount location, even after repeated post-dismount leash-anchor refresh attempts.

## 0.2.1 - 2026-04-20

Changes since `0.2.0`.

### Fixed

- Fixed the visual bug where the saddle rendered on the player's head while selected in the hotbar; it now uses a handheld item-model attachment setup and displays as a held item.

### Known Bugs

- Critical Priority: Secondary Action on a saddled horse is currently not opening the saddle inventory as intended.

## 0.2.0 - 2026-04-19

Changes since `0.1.1` development began.

### Added

- Added persistent equipped-saddle state tracking on horse entities through a custom `EquippedSaddleComponent`.
- Added server-side input interception for saddle use and right-click saddle equip actions.
- Added a saddle-to-horse equip interaction for `Horse` and `Tamed_Horse` targets.
- Added the `Horse_Overhaul_Saddled` livestock model with a default saddle attachment.
- Added the `Horse_Overhaul_Saddled` role variant so equipped horses can swap to a saddled appearance.
- Added a dedicated code path intended to open the equipped saddle inventory directly from a saddled horse instead of from the saddle item stack.
- Added temporary in-game diagnostics for saddled-horse interaction handling so the incoming interaction type and inventory-open result can be verified during testing.
- Added a `PlayerMouseButtonEvent`-based saddled-horse right-click handler so horse inventory access can be tested through the engine's event pipeline instead of only through packet interception.

### Changed

- Pressing `F` while holding the saddle now opens the saddle inventory through the native item-use flow.
- Right-clicking a valid horse with the saddle now consumes one saddle item, stores it on the horse, and requests the saddled role swap.
- The saddled horse role now uses the `Horse_Overhaul_Saddled` appearance so the saddle attachment is visible by default.
- Secondary-action handling now distinguishes between saddle-item actions and horse-target actions so horse interactions can be extended without replacing the existing saddle equip flow.
- Saddle inventory access is being moved toward entity-based interaction on the saddled horse, rather than relying only on the carried saddle item.
- Saddled-horse inventory detection now keys off the persisted equipped-saddle component instead of requiring an exact runtime role-name match.
- The saddled-horse inventory window now uses a generic container window and a dedicated 9-slot display container that syncs changes back into the equipped saddle item metadata.
- The packet filter has been narrowed back to the saddle item's own `Use` and `Secondary` handling so horse-side inventory experiments do not override default world-click behavior unnecessarily.
- Saddled-horse inventory-open attempts are now routed through the engine's player mouse-button event path, with temporary debug chat messages emitted from that handler during verification.

### Fixed

- Prevented saddle equip attempts from applying to non-horse targets or to horses that already have an equipped saddle.
- Fixed a regression where right-clicking an unsaddled horse with the saddle in the active hotbar slot could remove the player from the world instead of applying the saddle equip flow cleanly.
- Restored the intended priority so using the saddle item on an unsaddled horse resolves through the equip path before any saddled-horse inventory-open logic is considered.
- Prevented the saddled-horse inventory-open interception from preempting the saddle equip flow when the player is actively holding the saddle item.
- Removed the experimental mouse-packet interception for saddled-horse inventory open after it proved unstable and risked reintroducing the unsaddled-horse right-click regression during testing.

### Known Bugs

- ~~The saddle currently equips to the character's head instead of being held in the hands.~~ Fixed in `0.2.1`.
- Critical Priority: Secondary Action on a saddled horse is currently not opening the saddle inventory as intended.

## 0.1.1 - 2026-04-19

Changes since `0.1.0`.

### Added

- Added the `Saddle` item with placeholder icon, model, and texture assets.
- Added a crafting recipe for `Saddle` using `Light Leather` x3 and `Copper Ingot` x1.
- Added crafting metadata for the saddle with `Armorers Bench` as the requirement, `crafting` as the type, `chestplates` as the category, `3` seconds craft time, `common` quality, and max stack `1`.
- Added a 9-slot inventory container to the saddle item.
- Added a custom held-item interaction to open the saddlebag from the player's inventory screen.

### Changed

- Replaced the standalone custom saddle UI with the native Hytale inventory page.
- The saddlebag now opens in the pocket-crafting area of the vanilla inventory layout instead of a separate custom window.
- The saddlebag uses the engine's native inventory window behavior for drag and drop between the player inventory and the bag.

### Fixed

- Fixed the plugin load failure caused by compiling with a newer Java class version than the Hytale server runtime supports.
- Pinned the Gradle Java target to Java 25 so the built jar matches the current Hytale runtime expectations.
- Added `ServerVersion` metadata to the plugin manifest for the currently targeted Hytale server build: `2026.03.26-89796e57b`.
- Removed the now-unneeded custom saddle UI resource flow in favor of the native inventory page and item container window.
