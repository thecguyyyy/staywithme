# Mining Expedition Manual Tests

Use these checks before treating the vanilla mining expedition path as ready for regular play.

## Setup

- Start a survival world with mob griefing enabled.
- Spawn or use an owned companion.
- Keep `/staywithme expeditionstatus` available for quick expedition-only status checks.
- Use `/staywithme status` when inventory, perception, or integration details are needed.
- Use `/staywithme capabilities` to confirm the current high-level task entry points and whether PlayerEngine-first execution is enabled.
- If PlayerEngine is installed, confirm `/staywithme status` reports a PlayerEngine-first controller such as `playerengine_taskcatalogue` before judging broad collection/crafting/mining behavior.
- Use `/staywithme catalogue <query>` to inspect PlayerEngine TaskCatalogue names and StayWithMe alias resolution during acquisition debugging.

## PlayerEngine-First Smoke Runs

Run these before deep fallback expedition testing:

1. Follow and return movement:
   - Run `/staywithme follow`, then move 15-30 blocks away.
   - With PlayerEngine loaded, expect `/staywithme status` to show a PlayerEngine `follow:<player>:2.0` high-level signature while the companion keeps following.
   - Run `/staywithme goto <x> <y> <z>` with a nearby same-dimension coordinate, or ask `/staywithme ask go to <x> <y> <z>`.
   - Expect task summaries to show `GO_TO_POSITION` and status to show a PlayerEngine `goto:x,y,z:1.5` high-level signature while `GetToBlockTask` paths there.
   - Through `/staywithme ask`, try `come back` or `return to me`; expect `RETURN_TO_PLAYER` to use a PlayerEngine `return:<uuid>:3.0` signature and complete once the companion is within three blocks.
   - With PlayerEngine disabled or if PlayerEngine cannot start, expect the old Forge-native navigation fallback to move the companion instead.

2. Wood/material acquisition:
   - Run `/staywithme get crafting_table 1`, `/staywithme get stone_pickaxe 1`, `/staywithme get log 4`, `/staywithme get sticks 4`, `/staywithme get torches 16`, `/staywithme get minecraft:oak_log 4`, or `/staywithme mine minecraft:coal 4`.
   - Expect `/staywithme get ...` and local-parser obtain/fetch requests to report `GET_ITEM` in task summaries, while `/staywithme craft ...` still reports `CRAFT_ITEM`.
   - With PlayerEngine disabled, also try `/staywithme get cobblestone 3`, `/staywithme get cobble 3`, `/staywithme get coal 2`, or `/staywithme get diamonds 1`; these generic get requests should fall back to the local mining workflow rather than failing as uncraftable items.
   - Try `/staywithme mine minecraft:gravel 4` with PlayerEngine loaded; it is not an expedition registry mineral but should fall through to a PlayerEngine broad get if TaskCatalogue recognizes it.
   - Run `/staywithme catalogue log`, `/staywithme catalogue oak_log`, `/staywithme catalogue iron_ore`, or `/staywithme catalogue torch` when a `get` target is rejected.
   - Also try a typo such as `/staywithme catalogue torche`; expect `Input resolution` and `Closest catalogue names` to suggest nearby PlayerEngine names.
   - Expect status to show PlayerEngine runner details such as `runner=active`, `chain=User Task`, and a `highLevel=running(...)` request when PlayerEngine is loaded.
   - If the task completes or drops out of PlayerEngine, check whether `highLevel=` reports `callback_finished(...)`, `inactive_without_finish(...)`, or `missing_catalogue_task(...)`.
   - On failure, run `/staywithme status` and check `lastFailure`; no-workflow failures should include the last PlayerEngine high-level status.
   - If PlayerEngine cannot start the request, expect the old Forge-native workflow to continue instead of the task failing immediately.

3. Player handoff:
   - Run `/staywithme give torch 4`, `/staywithme give bread 1`, or `/staywithme give minecraft:cobblestone 8` with PlayerEngine loaded and the owner in render distance.
   - Through `/staywithme ask`, try `give me 4 torches`, `hand me bread`, or `bring me cobblestone 8`.
   - Expect task summaries to show `GIVE_ITEM`, status to show a PlayerEngine `give:<player>:<target>:<amount>` signature, and the requested items to be dropped near the player after PlayerEngine obtains them if needed.
   - With PlayerEngine disabled, expect `GIVE_ITEM` to fail visibly as PlayerEngine-only instead of entering a Forge-native get/craft workflow.

4. Dropped item pickup:
   - Drop a concrete item stack near the companion, such as torches, cobblestone, bread, or `minecraft:oak_log`.
   - Run `/staywithme pickup torch 1`, `/staywithme pickup minecraft:cobblestone 4`, or ask `pick up dropped torches`.
   - Expect task summaries to show `PICKUP_DROPPED_ITEM`, status to show a PlayerEngine `pickup:<target>:<amount>` signature, and the companion to pick up existing item entities only.
   - Remove all matching dropped items and repeat the command; expect a visible pickup failure rather than mining/crafting the missing item. Use `/staywithme get <item> [amount]` for broad acquisition.
   - With PlayerEngine disabled, expect `PICKUP_DROPPED_ITEM` to fail visibly unless the requested inventory count is already satisfied.

5. Route building materials:
   - Run `/staywithme buildingmaterials 32`, `/staywithme routeblocks 32`, or ask `collect 32 bridge blocks`.
   - Expect task summaries to show `COLLECT_BUILDING_MATERIALS`, status to show a PlayerEngine `building_materials:32` high-level signature, and the companion inventory to gain common placeable route blocks such as cobblestone, cobbled deepslate, dirt, or netherrack.
   - This only prepares blocks for path recovery; it should not start a construction path by itself.
   - With PlayerEngine disabled and not enough carried route blocks, expect a visible PlayerEngine-required failure.
   - Then remove carried cobblestone, cobbled deepslate, dirt, and netherrack, create a short station-return or bridge route that needs one placed block, and trigger the blocked workflow.
   - Expect `/staywithme status` construction details to show `materialRestock=32:<label>` while PlayerEngine collects building materials, then expect construction to replan from the new position after the materials arrive.

6. Inventory deposit:
   - Put several non-tool items in the companion inventory, then run `/staywithme deposit` or `/staywithme stash` with PlayerEngine loaded.
   - Through `/staywithme ask`, try `deposit inventory`, `stash your items`, or `unload inventory`.
   - Expect task summaries to show `DEPOSIT_INVENTORY`, status to show `deposit_inventory`, and PlayerEngine to use a nearby valid container or place a chest before storing non-tool items.
   - Repeat with only tools/equipment or an empty inventory; expect the task to complete immediately as already satisfied.
   - With PlayerEngine disabled, expect `DEPOSIT_INVENTORY` to fail visibly as PlayerEngine-only.

7. Fixed make commands:
   - Run `/staywithme stonepickaxe`.
   - Expect PlayerEngine/TaskCatalogue to handle the recursive log, crafting table, stick, wooden pickaxe, cobblestone, and stone pickaxe chain when available.

8. Food and fuel acquisition:
   - Run `/staywithme food 10` with PlayerEngine loaded.
   - Also try `/staywithme get food 10`; it should normalize to the same `COLLECT_FOOD` task instead of a generic item named `food`.
   - Run `/staywithme meat 10` near passive animals or dropped raw/cooked meat.
   - Also try `/staywithme get meat 10`; it should normalize to the same `COLLECT_MEAT` task instead of a generic item named `meat`.
   - Run `/staywithme fuel 4` and `/staywithme get fuel 4`; both should normalize to `COLLECT_FUEL`.
   - Run `/staywithme smelt raw_iron 1`, `/staywithme smelt gold_ingot 1`, `/staywithme smelt copper_ingot 1`, and `/staywithme smelt charcoal 1` in a world where PlayerEngine can obtain the needed inputs/fuel/furnace.
   - Through `/staywithme ask`, try `get meat` or `hunt animals for food`; these should create `COLLECT_MEAT`.
   - Through `/staywithme ask`, try `collect furnace fuel` or `get smelting fuel`; these should create `COLLECT_FUEL`.
   - Through `/staywithme ask`, try `smelt raw iron`, `smelt raw copper`, or `make charcoal`; these should create `SMELT_ITEM` with output targets `iron_ingot`, `copper_ingot`, or `charcoal`.
   - Expect task summaries to show `COLLECT_FOOD`, status to show a PlayerEngine `food:10` acquisition signature, and the task to finish once carried edible food units are sufficient.
   - For meat, expect task summaries to show `COLLECT_MEAT`, status to show a PlayerEngine `meat:10` acquisition signature, and completion only after enough carried meat food units exist.
   - For fuel, expect task summaries to show `COLLECT_FUEL`, status to show a PlayerEngine `fuel:4` acquisition signature, and completion once carried coal or charcoal reaches the requested count.
   - For smelting, expect task summaries to show `SMELT_ITEM`, status to show a PlayerEngine `smelt:<target>:<count>` acquisition signature, and completion only once the output item count is present in the companion inventory.
   - With PlayerEngine disabled and no carried food/fuel/smelting output, expect a clear failure explaining that broad food, fuel collection, or high-level smelting currently needs PlayerEngine. Asking for a specific item such as `/staywithme get bread 1` or `/staywithme get coal 1` can still use ordinary get/craft behavior.

9. PlayerEngine-only task entries:
   - Run `/staywithme fish` with a fishing rod available or obtainable, then stop it with `/staywithme stop`.
   - Run `/staywithme farm 10` near mature crops, then stop it with `/staywithme stop`.
   - Run `/staywithme explore 48`; with PlayerEngine loaded, expect status to show `explore:48.0` while `TimeoutWanderTask` explores outward, and completion after it has moved roughly the requested distance.
   - At night, run `/staywithme sleep` or `/staywithme night`; during daytime, the task should complete immediately without starting PlayerEngine work.
   - Put the companion in shallow water and run `/staywithme outofwater` or `/staywithme dryland`; expect task summaries to show `GET_OUT_OF_WATER`, status to show `get_out_of_water`, and completion after it reaches dry ground.
   - In a controlled safe test world, put the companion in or next to lava/fire and run `/staywithme escapelava`; expect task summaries to show `ESCAPE_LAVA`, status to show `escape_lava`, and completion after it is no longer in lava or on fire.
   - Place an ordinary fire or soul fire block near the companion, then run `/staywithme putoutfire 8` or `/staywithme extinguish 8`; expect task summaries to show `PUT_OUT_FIRE`, status to show a PlayerEngine `put_out_fire:x,y,z` signature when PlayerEngine is loaded, and completion once nearby fire blocks are gone.
   - Run `/staywithme equiparmor iron`, `/staywithme equiparmor diamond`, or `/staywithme equiparmor minecraft:iron_chestplate`.
   - Through `/staywithme ask`, try `start fishing`, `farm these crops`, `explore farther`, `sleep through the night`, `swim to shore`, `escape lava`, `put out fire`, and `equip iron armor`.
   - Expect task summaries to show `FISH`, `FARM`, `EXPLORE`, `SLEEP_THROUGH_NIGHT`, `GET_OUT_OF_WATER`, `ESCAPE_LAVA`, `PUT_OUT_FIRE`, or `EQUIP_ARMOR`, and `/staywithme status` to expose the PlayerEngine running signature for tasks that are not already satisfied.
   - After armor completion, visually confirm the companion's actual armor slots changed, not just that PlayerEngine reported callback completion.
   - With PlayerEngine disabled, expect PlayerEngine-only tasks to fail visibly instead of falling into unrelated local workflows. `PUT_OUT_FIRE` and `EXPLORE` are exceptions: fire uses close-range Forge-native block destruction, and explore picks a deterministic reachable standable target outward from the current position.

10. Nearby hostile combat:
   - Spawn or find one hostile mob near the companion, then run `/staywithme attack`.
   - With PlayerEngine loaded, expect `/staywithme status` to show a PlayerEngine `attack:<uuid>` high-level signature while `KillEntityTask` approaches and attacks the selected hostile.
   - Expect the task to complete after that selected hostile dies. If another hostile is nearby, it should not immediately retarget as part of the same `ATTACK_NEARBY_HOSTILE` task.
   - With PlayerEngine disabled or if the PlayerEngine attack task cannot start, expect the old local approach-and-attack fallback to run.

11. Hostile retreat and continuous protection:
   - Spawn or find one or more hostile mobs near the companion, then run `/staywithme retreat 16` or `/staywithme flee 16`.
   - Through `/staywithme ask`, try `retreat from the monsters`, `flee`, or `run away from hostiles`.
   - With PlayerEngine loaded, expect task summaries to show `RETREAT_FROM_HOSTILES` and status to show a PlayerEngine `retreat_hostiles:16.0` high-level signature while `RunAwayFromHostilesTask` moves away.
   - Expect the task to complete once no hostile remains within the requested distance.
   - With PlayerEngine disabled and a hostile still nearby, expect a visible PlayerEngine-required failure.
   - Spawn a creeper near the companion, then run `/staywithme creeperretreat 10` or `/staywithme fleecreeper 10`.
   - Through `/staywithme ask`, try `flee creeper` or `avoid creepers`.
   - With PlayerEngine loaded, expect task summaries to show `RETREAT_FROM_CREEPERS` and status to show a PlayerEngine `retreat_creepers:10.0` high-level signature while `RunAwayFromCreepersTask` picks a creeper-safe path.
   - With PlayerEngine disabled, expect the Forge fallback to move to a reachable standable block outside the requested creeper distance, or fail visibly if no safe local retreat point exists.
   - Fire arrows or spawn a skeleton so tracked projectiles pass near the companion, then run `/staywithme dodge 4`.
   - Through `/staywithme ask`, try `dodge arrows` or `avoid incoming projectiles`.
   - With PlayerEngine loaded, expect task summaries to show `DODGE_PROJECTILES` and status to show a PlayerEngine `dodge_projectiles:4.0:3.0` high-level signature while `DodgeProjectilesTask` moves to a safe block.
   - With no tracked projectiles nearby, the task may complete immediately.
   - Spawn an angry skeleton with line of sight to the companion, give the companion at least one throwaway block, then run `/staywithme projectilewall 16` or `/staywithme arrowwall 16`.
   - Through `/staywithme ask`, try `block arrows` or `build an arrow wall`.
   - With PlayerEngine loaded, expect task summaries to show `PROJECTILE_PROTECTION_WALL` and status to show a PlayerEngine `projectile_wall` high-level signature while `ProjectileProtectionWallTask` places cover.
   - With PlayerEngine disabled and a skeleton threat still present, expect a visible PlayerEngine-required failure; with no skeleton threat, the task should complete immediately.
   - Spawn or find several hostile mobs or hostile drops near the companion, then run `/staywithme protect` or `/staywithme hero`.
   - Through `/staywithme ask`, try `protect me`, `guard me`, or `keep watch`.
   - With PlayerEngine loaded, expect task summaries to show `PROTECT_PLAYER` and status to show a PlayerEngine `protect_player` high-level signature while `HeroTask` searches, attacks hostiles, and picks hostile drops.
   - Expect this task to keep running until `/staywithme stop`; it should not complete after the first hostile dies.
   - With PlayerEngine disabled, expect a visible PlayerEngine-required failure instead of falling into a single local attack loop.

12. Expedition target acquisition:
   - Run `/staywithme expedition minecraft:raw_iron 1`.
   - With PlayerEngine active, expect the target acquisition to prefer PlayerEngine `get raw_iron 1`.
   - If PlayerEngine finishes without satisfying the requested item, expect fallback to the existing local expedition workflow.

13. Local parser quantity fallback:
   - Disable or clear the LLM key, then ask through `/staywithme ask` for `go to 10 64 -20`, `mine 4 coal`, `get 16 torches`, `get torches 16`, `give me 4 torches`, `bring me bread 1`, `deposit inventory`, `craft torches 16`, `smelt raw iron`, `make charcoal`, `collect 8 logs`, `collect furnace fuel`, `explore farther`, `swim to shore`, `escape lava`, `retreat from hostiles`, `flee creeper`, `dodge arrows`, `block arrows`, `farm 12`, `sleep through the night`, `equip diamond armor`, `protect me`, and `get food`.
   - Expect the structured task amount to preserve the requested count instead of defaulting to one.

## Baseline Runs

1. Coal expedition:
   - Run `/staywithme expedition minecraft:coal 4`.
   - Expect it to prepare basic supplies, descend or reuse a remembered route, mine coal, and return before completing.
   - Check `supplyStatus`, `torches`, `routeTarget`, `moveWatch`, and `resourceHits` with `/staywithme expeditionstatus`.

2. Iron expedition:
   - Run `/staywithme expedition minecraft:raw_iron 3`.
   - Expect stone-pickaxe preparation, supply chest setup, branch mining, final return, and non-essential inventory unload.
   - Confirm supply chest memory appears in `/staywithme memory`.

3. Diamond expedition:
   - Run `/staywithme expedition minecraft:diamond 1`.
   - Expect iron-pickaxe preparation and target-layer descent.
   - If a prior diamond hit exists, check whether route reuse or direction choice references resource-hit memory.

## Resupply Checks

1. Torch restock:
   - Put coal or charcoal plus logs or planks in the supply chest.
   - Remove most carried torches while mining.
   - Expect return to supply, log-to-plank/stick crafting if needed, torch crafting, and resume.

2. Tool restock:
   - Put cobblestone/iron ingots plus logs or planks in the supply chest.
   - Let or force the active pickaxe below usable durability.
   - Expect the recorded descent/branch route to be followed backward instead of a new return tunnel being opened.
   - Expect pickaxes to be pulled or crafted until the companion carries two usable target-capable pickaxes when materials permit.
   - After resupply, expect the recorded route to replay forward to the interrupted work position before descent or branch mining continues.
   - Break the first of two usable pickaxes underground and expect immediate backup selection without a supply return.
   - If no tool materials exist, expect a clean paused `tool restock unavailable` state instead of a missing-tool loop.

3. Food recovery:
   - Put cooked food or cookable raw food plus fuel/furnace materials in the supply chest.
   - Lower companion hunger/health during an expedition.
   - Expect food pull, food cooking when possible, eating, and recovery wait/resume.

4. Full chest expansion:
   - Fill the active supply chest until it cannot accept non-essential overflow.
   - Give the companion or supply chest enough chest/plank/log material.
   - Expect an extra chest placement and active supply chest memory update.

## Hazard And Route Checks

1. Lava reroute:
   - Expose adjacent lava near the mining route.
   - Expect return to supply, hazard note, direction rotation, and `lavaReroute` status.

2. Water leak avoidance:
   - Place water adjacent to a block the branch tunnel would dig.
   - Expect `fluid` hazard avoidance and route rotation instead of opening a flooded tunnel.

3. Falling-block collapse avoidance:
   - Place gravel or sand directly above a stone block that the staircase or branch tunnel would otherwise dig.
   - Expect route rotation before the supporting stone is broken.
   - Repeat near water and confirm the companion does not open a collapse path that floods or lifts it out of the tunnel.

4. Floor gap repair:
   - Create a one-block floor gap with sturdy support below.
   - Give expendable cobblestone, cobbled deepslate, dirt, or netherrack.
   - Expect floor repair before route rotation.

5. Remembered route replay:
   - Complete one expedition, then run the same resource again.
   - Expect route reuse when endpoints are loaded and standable.
   - Block a remembered endpoint or waypoint and verify route invalidation/reselection.

6. Movement stall guard:
   - Temporarily trap the companion during return or remembered-route travel.
   - After about 45 seconds without block-position progress, expect `moveWatch` to reach the limit and the expedition to pause/fallback instead of waiting forever.

7. Local vertical passage:
   - Let descent open a staircase through solid stone and inspect a downward corner.
   - Expect enough forward head-space to be cleared before the companion enters the lower step.
   - Let ascent reuse or open an upward staircase.
   - Expect the companion to jump onto each prepared one-block rise instead of waiting on global navigation.

8. Floor repair continuation:
   - Create a supported one-block floor gap in the next branch-tunnel cell and provide an expendable repair block.
   - Expect the companion to approach from a neighboring stand position, place the floor, and continue through the repaired cell.
   - Remove all safe neighboring stand positions and expect route rotation instead of repeated walking toward the placement cell.

9. Safe mining footing:
   - Run `/staywithme stonepickaxe` near exposed floor stone and watch the cobblestone collection phase.
   - Expect the companion to mine from a neighboring stand position rather than standing on the target stone and dropping into its own hole.
   - If a crafting-table return has no ordinary stand path, expect bounded construction-route recovery instead of repeated `Moving to the crafting table` messages.

10. Station construction-route recovery:
   - Put the companion in a shallow hole or behind a short diggable obstruction while its workflow needs an existing crafting table or furnace.
   - Expect `/staywithme status` to show `construction=...` while the companion plans and executes adjacent route steps.
   - With LLM disabled or no API key configured, expect `source=local_voxel_astar_incremental` for nearby recovery.
   - Give the companion dirt or cobblestone and add a supported one-block floor gap; expect a repair block to be placed when needed.
   - Remove all usable route blocks, keep PlayerEngine loaded, and repeat the supported gap case; expect the construction route to pause for `construction_building_materials`, gather route blocks, and then replan instead of failing immediately.
   - Add fluid, an unbreakable block, or remove all locally valid routes; expect a visible failure instead of unsafe digging or an infinite loop.

11. Horizontal bridge:
   - Create a two-to-four-block same-height gap with no support below and give the companion expendable cobblestone, cobbled deepslate, dirt, or netherrack.
   - Put a required station or remembered route point across the gap.
   - Expect one bridge block to be placed from the current sturdy floor before each forward move.
   - Remove the repair blocks or replace the gap with lava and expect a visible construction failure instead of movement into the gap.

12. Remote return policy:
   - Put the expedition supply point more than 32 horizontal blocks or 12 vertical blocks from the active mining position, then trigger tool/torch/food resupply.
   - Without command permission level 2, expect `construction=...source=direct_remote_construction` and a sequence of validated horizontal tunnels, stairs, shafts/pillars, or bridges.
   - With command permission level 2, expect a direct teleport only to a loaded, standable return target.
   - Remove or block an old remembered crafting table and verify its loaded world state is checked before a replacement is placed.

## Completion Checks

- When the target amount is reached, expect the companion to return to the supply point or owner before final completion.
- If navigation and validated construction return are both unavailable after the target is already satisfied, expect completion with `complete: remote return unavailable`.
- Confirm `/staywithme memory` records status, resource hits, hazards, branch routes, and supply stations.

## Quick Status Fields

- `supplyStatus`: current high-level expedition phase.
- `moveWatch`: movement stall label and counter.
- `supplyStock`: loaded supply chest inventory summary.
- `routeTarget`, `routeWaypoint`, `routeDepth`: remembered route replay state.
- `travelBreadcrumbs`, `resupplyRoute`, `resumeRoute`: active mining-route recording and resupply return/replay progress.
- `resourceHits`: remembered target-resource hit count.
- `knownHazards`, `lavaReroute`, `lavaOrigin`: safety/reroute state.
- `restockBlocked`: whether torch/tool/food restock was proven unavailable.
