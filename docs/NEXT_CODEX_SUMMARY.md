# StayWithMe Next Codex Summary

## Project

- Mod: `staywithme`
- Mod id: `staywithme`
- Minecraft: Forge 1.20.1
- Java: 17
- Main package: `com.thecguyyyy.staywithme`
- Current build command: `.\gradlew.bat build`
- Last verified result: build passes on 2026-06-15 after adding PlayerEngine-backed `CLEAR_LIQUID`, automatic passage-fluid clearing for ordinary resource exploration and mining-expedition tunnel digging, PlayerEngine `GetToYTask` as the first attempt for mining-expedition `DESCEND_TO_LAYER`, one-shot PlayerEngine `GetToBlockTask` attempts before remote/stalled return travel falls back to construction-route recovery, a vanilla charcoal workflow fallback for `COLLECT_FUEL`, limited Forge fallbacks for `GET_OUT_OF_WATER` and `ESCAPE_LAVA`, limited carried-armor fallback for `EQUIP_ARMOR`, limited local retreat fallback for `RETREAT_FROM_HOSTILES`, limited local sidestep fallback for `DODGE_PROJECTILES`, and limited carried-block cover fallback for `PROJECTILE_PROTECTION_WALL`. The carried-armor fallback is now split into `LocalArmorEquipFallback`, local dry/lava-safe stand search is split into `LocalHazardEscapeFallback`, local liquid/fire block handling is split into `LocalBlockSafetyFallback`, local hostile/projectile safety fallback is split into `LocalThreatSafetyFallback`, carried food/meat/fuel satisfaction checks are split into `LocalInventoryFallback`, item alias/inventory/crafting target resolution is split into `LocalItemMatcher`, PlayerEngine movement starts are split into `PlayerEngineMovementRunner`, PlayerEngine armor equip execution is split into `PlayerEngineArmorEquipRunner`, PlayerEngine block placement execution is split into `PlayerEnginePlaceBlockRunner`, PlayerEngine liquid/fire safety execution is split into `PlayerEngineBlockSafetyRunner`, PlayerEngine threat-safety execution is split into `PlayerEngineThreatSafetyRunner`, PlayerEngine remote return/goto attempts are split into `PlayerEngineRemoteTravelRunner`, PlayerEngine Y-layer travel attempts are split into `PlayerEngineYLevelRunner`, PlayerEngine exploration execution is split into `PlayerEngineExploreRunner`, PlayerEngine fuel collection and charcoal fallback routing is split into `PlayerEngineFuelRunner`, PlayerEngine construction-material restock is split into `PlayerEngineConstructionMaterialRestockRunner`, shared construction travel results are split into `ConstructionTravelResult`, PlayerEngine catalogue request mapping is split into `PlayerEngineAcquisitionRequests`, common PlayerEngine get/acquisition execution is split into `PlayerEngineAcquisitionRunner`, counted PlayerEngine task execution is split into `PlayerEngineCountedTaskRunner`, start-only continuous PlayerEngine execution is split into `PlayerEngineStartOnlyTaskRunner`, callback-confirmed PlayerEngine execution is split into `PlayerEngineConfirmedTaskRunner`, PlayerEngine task-with-fallback execution is split into `PlayerEngineFallbackTaskRunner`, PlayerEngine status truncation is split into `PlayerEngineStatusText`, and high-level PlayerEngine task state/start/announcement handling is split into `PlayerEngineTaskState`, so `LocalBehaviorController` can keep shrinking toward PlayerEngine-first orchestration. This sits on top of PlayerEngine-backed `FOLLOW_PLAYER`/`GO_TO_POSITION`/`RETURN_TO_PLAYER`, `GET_ITEM`, `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, construction-route material restock, `GIVE_ITEM`/`DEPOSIT_INVENTORY`, `SMELT_ITEM`, `EXPLORE`, `ATTACK_NEARBY_HOSTILE`/`PROTECT_PLAYER`/`RETREAT_FROM_HOSTILES`/`RETREAT_FROM_CREEPERS`/`DODGE_PROJECTILES`/`PROJECTILE_PROTECTION_WALL`, `SLEEP_THROUGH_NIGHT`/`GET_OUT_OF_WATER`/`ESCAPE_LAVA`/`PUT_OUT_FIRE`, `COLLECT_FOOD`/`COLLECT_MEAT`/`COLLECT_FUEL`, `FISH`/`FARM`/`EQUIP_ARMOR`, `/staywithme capabilities`, PlayerEngine-style command broadening, smarter catalogue plural aliases, generic-get mining fallback, local parser quantity cleanup, and acquisition failure diagnostics.
- Last upstream reference check before this development pass: Player2NPC `ec439c11b4fd31ebd3b1b698f28f632eec4081fc`, PlayerEngine `13fddc140a7ffd60b6bc9d1f084b36adc0674510`.
- Manual reload-resume checklist: `docs/RELOAD_RESUME_TESTS.md`
- In-game API manager guide: `docs/API_CONFIG_UI.md`

## Current Direction

This project is being shifted from a Forge-native survival executor toward a PlayerEngine-first companion shell. The companion still acts through an in-world entity body, but broad Minecraft survival execution should be delegated to PlayerEngine/TaskCatalogue when that mod is loaded. The Forge-native executor remains a fallback and a place for StayWithMe-specific constraints, memory, debugging, and recovery logic.

Core principle:

```text
player natural language / command
  -> LLM or local fallback produces structured task/strategy JSON
  -> StayWithMe chooses strategy, memory, policy, and UI/debug behavior
  -> PlayerEngine/TaskCatalogue executes broad get/craft/mine/smelt survival tasks when available
  -> Forge-native workflows execute only as fallback or for StayWithMe-specific expedition/recovery behavior
```

Do not make the LLM issue per-tick movement, camera, or block-breaking instructions. Prefer PlayerEngine-style task execution for broad survival actions instead of expanding `LocalBehaviorController` further. A bounded construction-route request is still allowed after ordinary navigation fails: LLM returns adjacent relative feet positions once, then Java derives and validates any required digging or supported floor repair before execution.

## Important Existing Commands

- `/staywithme spawn`
- `/staywithme follow`
- `/staywithme goto <x> <y> <z>`
- `/staywithme stop`
- `/staywithme status`
- `/staywithme observe`
- `/staywithme crafttable`
- `/staywithme capabilities`
- `/staywithme sticks`
- `/staywithme chest`
- `/staywithme woodenaxe`
- `/staywithme woodenpickaxe`
- `/staywithme stonepickaxe`
- `/staywithme furnace`
- `/staywithme ironingot`
- `/staywithme ironpickaxe`
- `/staywithme buildingmaterials [count]`
- `/staywithme routeblocks [count]`
- `/staywithme bridgeblocks [count]`
- `/staywithme food [units]`
- `/staywithme meat [units]`
- `/staywithme fuel [count]`
- `/staywithme smelt <iron_ingot|gold_ingot|copper_ingot|charcoal> [amount]`
- `/staywithme fish`
- `/staywithme farm [range]`
- `/staywithme explore [distance]`
- `/staywithme sleep`
- `/staywithme night`
- `/staywithme outofwater`
- `/staywithme dryland`
- `/staywithme escapelava`
- `/staywithme clearliquid <x> <y> <z>`
- `/staywithme clearwater <x> <y> <z>`
- `/staywithme clearlava <x> <y> <z>`
- `/staywithme putoutfire [range]`
- `/staywithme extinguish [range]`
- `/staywithme equiparmor <target>`
- `/staywithme protect`
- `/staywithme hero`
- `/staywithme retreat [distance]`
- `/staywithme flee [distance]`
- `/staywithme creeperretreat [distance]`
- `/staywithme fleecreeper [distance]`
- `/staywithme dodge [distance]`
- `/staywithme projectilewall [range]`
- `/staywithme arrowwall [range]`
- `/staywithme attack`
- `/staywithme fight`
- `/staywithme craft <item> [amount]`
- `/staywithme get <item> [amount]`
- `/staywithme pickup <item> [amount]`
- `/staywithme give <item> [amount]`
- `/staywithme deposit`
- `/staywithme stash`
- `/staywithme catalogue [query]`
- `/staywithme recipes [query]`
- `/staywithme mine <resource> [amount]`
- `/staywithme mineplan <resource> [amount]`
- `/staywithme expedition <resource> [amount]`
- `/staywithme oreinfo <resource>`
- `/staywithme memory`
- `/staywithme memory export`
- `/staywithme memory import <file>`
- `/staywithme memory learnresource <resource> <hint>`
- Client-side `/staywithmeconfig` or the `O` key opens the in-game API manager.

## Current Vanilla Capabilities

The companion can:

- Spawn as `FriendEntity`.
- Follow and stop.
- Pick up nearby item entities into its own 36-slot inventory.
- Craft via active server `RecipeManager` for vanilla `minecraft:crafting` recipes.
- Build long workflows for:
  - crafting table
  - sticks
  - chest
  - wooden axe
  - wooden pickaxe
  - stone pickaxe
  - furnace
  - iron ingot
  - iron pickaxe
- Use a real placed furnace block entity for iron smelting.
- Prefer PlayerEngine/TaskCatalogue for known resource acquisition when available, with Forge-native survival mining as fallback.
- Discover nearby resources through visible/reachable world checks instead of buried-resource scans.
- Report only loaded, exposed breakable logs through local perception and LLM world snapshots.
- Keep survival constraints for fallback mining:
  - correct tool is required for blocks that need one
  - tool durability is consumed
  - mobGriefing must allow block breaking
- Generate `MiningExpeditionPlan` through LLM or local fallback.
- Recover from first-pass crafting-table and furnace return path failures with a bounded local construction route:
  - capture a compressed local voxel model without exposing hidden resource identities
  - request an optional LLM relative-feet route once when configured
  - fall back to a local voxel A* route when LLM is disabled, unavailable, or invalid
  - derive and validate survival-style digging, one-block supported floor repair, and one-step vertical movement locally before each action
- Execute first-pass mining expeditions:
  - prepare required tool chain
  - prepare supply chest for from-scratch expedition chains
  - prepare a 16-torch starter stock for stone/iron-pickaxe expedition chains
  - descend toward a target Y layer with stair-step digging
  - avoid digging straight down
  - avoid adjacent lava/fluid hazards
  - place torches in dark tunnels when available
  - branch mine with local two-high tunnel digging and opportunistic visible-target mining
  - return to supply point when inventory is nearly full
  - unload non-essential items to supply chest while keeping tools, torches, and target resources
  - return to supply point for low health recovery, temporary hostile-threat retreat, or lava reroute, and pause if recovery is unavailable, hostile threat does not clear, the return point is unsafe, or the task is explicitly cancelled

## LLM Role

LLM currently does:

- `/staywithme ask <message>` task planning.
- `/staywithme oreinfo <resource>` ore/resource distribution analysis.
- `/staywithme mineplan <resource> [amount]` high-level mining expedition planning.
- `/staywithme expedition <resource> [amount]` strategy generation before starting executable expedition workflow.
- Bounded stuck-route advice for crafting-table, furnace, and mining-route detachment recovery. The model receives an abstract local voxel snapshot and returns adjacent relative feet positions; Java owns all block-action derivation and validation.

LLM returns structured JSON only. It should not output prose that Java must parse.

Important classes:

- `ai/TaskPlanner.java`
- `llm/OreDistributionAnalyzer.java`
- `llm/OreDistributionAnalysis.java`
- `llm/MiningExpeditionPlanner.java`
- `llm/MiningExpeditionPlan.java`
- `llm/OpenAICompatibleClient.java`
- `llm/ConstructionPathLlmPlanner.java`
- `ai/navigation/ConstructionPathSnapshot.java`
- `ai/navigation/ConstructionRoutePlan.java`
- `ai/navigation/LocalConstructionPathfinder.java`
- `client/StayWithMeLlmConfigScreen.java`
- `network/LlmConfigRequestPacket.java`
- `network/LlmConfigSnapshotPacket.java`
- `network/LlmConnectionTestPacket.java`

## PlayerEngine / Player2NPC Integration

PlayerEngine is now the preferred execution layer when present and enabled. It is still optional at runtime: a normal `FriendEntity` is created when PlayerEngine is missing, while a reflected `PlayerEngineFriendEntity` subclass is created when PlayerEngine is loaded so the entity itself implements the provider interfaces required by PlayerEngine's entity context.

Important classes:

- `embodied/EmbodiedController.java`
- `embodied/DummyEmbodiedController.java`
- `embodied/PlayerEngineEmbodiedController.java`
- `entity/FriendEntityFactory.java`
- `playerengine/PlayerEngineFriendEntity.java`
- `playerengine/PlayerEngineProviderHost.java`
- `playerengine/FriendPlayerEngineController.java`
- `playerengine/FriendAutomatoneBridge.java`
- `playerengine/FriendInventoryProvider.java`
- `playerengine/FriendInteractionProvider.java`
- `playerengine/FriendHungerProvider.java`
- `playerengine/PlayerEngineCompat.java`
- `playerengine/PlayerEngineCatalogueNames.java`

Current behavior:

- If PlayerEngine is loaded, `FriendEntityFactory` reflects `PlayerEngineFriendEntity`, which implements `IAutomatone`, `IInventoryProvider`, `IInteractionManagerProvider`, and `IHungerManagerProvider`, matching the Player2NPC provider-entity shape more closely.
- `FriendAutomatoneBridge` now merges PlayerEngine armor inventory with direct `entity.setItemSlot(...)` armor changes, so `EquipArmorTask` changes are not overwritten when the PlayerEngine tick finishes.
- `FriendPlayerEngineController` creates a real `PlayerEngineController`, calls `TaskCatalogue.getItemTask(...)` for high-level acquisition, wraps `FollowPlayerTask`/`GetToBlockTask`/`GetToEntityTask` for follow/goto/return movement, wraps `PickupDroppedItemTask` for pickup-only dropped item collection, wraps `GetBuildingMaterialsTask` for route/bridge block supply, wraps `GiveItemToPlayerTask` for owner handoff tasks, wraps `StoreInAnyContainerTask` for inventory deposit, wraps `KillEntityTask` for single hostile attack requests, and wraps `HeroTask` for continuous protect/hostile-cleanup mode.
- `PlayerEngineEmbodiedController` now tries PlayerEngine movement/following/returning/mining/acquisition/combat first, then falls back to Forge-native behavior.
- `LocalBehaviorController` now attempts PlayerEngine acquisition before running old local workflows for wood, generic craft, generic mine, mining expedition target acquisition, and the fixed make-tool/make-station commands.
- `/staywithme get <item> [amount]` is a broad acquisition command that accepts vanilla item ids and PlayerEngine catalogue-style names such as `log`, `sticks`, `torches`, `raw_iron`, or `minecraft:oak_log`; with PlayerEngine loaded it should behave like a TaskCatalogue `get` request, and without PlayerEngine it falls back to local crafting/wood workflow support where implemented.
- `/staywithme pickup <item> [amount]` is a PlayerEngine-backed pickup-only command. It starts `PickupDroppedItemTask` for already-dropped item entities and intentionally does not mine, craft, or recursively gather missing ingredients. Local/LLM requests such as `pick up dropped torches` or `loot cobblestone on the ground` can create `PICKUP_DROPPED_ITEM`.
- `/staywithme buildingmaterials [count]`, `/staywithme routeblocks [count]`, and `/staywithme bridgeblocks [count]` start PlayerEngine `GetBuildingMaterialsTask` for throwaway route/bridge/scaffold blocks. This prepares placeable construction materials for path recovery; it does not itself run the construction pathfinder.
- Construction-route recovery now has an internal PlayerEngine material restock path. If validated route planning or execution needs a repair/bridge/pillar block and the companion carries no usable route blocks, it starts `GetBuildingMaterialsTask` for 32 building materials, waits for that high-level task, then replans the construction route from the companion's new position.
- `FOLLOW_PLAYER` now prefers PlayerEngine `FollowPlayerTask`, `GO_TO_POSITION` prefers PlayerEngine `GetToBlockTask`, and `RETURN_TO_PLAYER` prefers PlayerEngine `GetToEntityTask` and completes once the companion is within three blocks of the task player. Movement tasks fall back to Forge-native navigation if PlayerEngine cannot start. Remote/stalled station, breadcrumb, and expedition returns try a target-bound PlayerEngine `GetToBlockTask` once before using validated construction-route digging/bridging or permission-gated teleport fallback.
- `PlayerEngineEmbodiedController.goToBlock(...)` now reports only whether the PlayerEngine high-level `GetToBlockTask` started. It no longer silently starts lower-level movement while returning `true`, so callers can make explicit fallback decisions.
- `/staywithme give <item> [amount]` is a PlayerEngine-only handoff command. It starts `GiveItemToPlayerTask`, which obtains the target through PlayerEngine when needed, follows the task player, and drops the requested item stack to them. Local/LLM requests such as `give me torches` or `bring me bread` can now create `GIVE_ITEM`.
- `/staywithme deposit` and `/staywithme stash` are PlayerEngine-only storage commands. They start `StoreInAnyContainerTask` with the current non-equipped/non-tool inventory targets, reusing a nearby valid container or placing a chest through PlayerEngine when needed.
- High-level task semantics now distinguish `GET_ITEM`, `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, `GIVE_ITEM`, `DEPOSIT_INVENTORY`, and `CRAFT_ITEM`. `/staywithme get` and local/LLM obtain/fetch requests create `GET_ITEM`; `/staywithme pickup` and explicit dropped-item pickup requests create `PICKUP_DROPPED_ITEM`; `/staywithme buildingmaterials`/`routeblocks`/`bridgeblocks` and explicit route-material requests create `COLLECT_BUILDING_MATERIALS`; `/staywithme give` and explicit handoff requests create `GIVE_ITEM`; `/staywithme deposit`/`stash` and unload/store inventory requests create `DEPOSIT_INVENTORY`; `/staywithme craft` and explicit crafting requests create `CRAFT_ITEM`.
- `COLLECT_FOOD` is now a first-class high-level task backed by PlayerEngine's `CollectFoodTask` when PlayerEngine is available. `/staywithme food [units]`, `/staywithme get food <units>`, LLM `GET_ITEM target=food`, and general food/hungry requests use it. Forge-native fallback currently only completes if carried edible food units already satisfy the request, otherwise it fails visibly and suggests asking for a specific food item. The local carried-food checks are isolated in `LocalInventoryFallback`.
- `COLLECT_MEAT` is now backed by PlayerEngine's `CollectMeatTask`. `/staywithme meat [units]`, `/staywithme get meat <units>`, LLM `GET_ITEM target=meat`, and natural-language meat/hunting requests can hunt animals and cook meat through PlayerEngine when available. Forge-native fallback only completes if carried meat already satisfies the request; otherwise it fails visibly as PlayerEngine-required. The local carried-meat checks are isolated in `LocalInventoryFallback`.
- `COLLECT_FUEL` is backed by PlayerEngine's `CollectFuelTask`. `/staywithme fuel [count]`, `/staywithme get fuel <count>`, and natural-language furnace/smelting fuel requests use it. If PlayerEngine is unavailable, cannot start, or finishes without enough coal/charcoal, Forge fallback activates `collect_fuel_charcoal`: collect logs, craft planks/tools, prepare/place a furnace, and smelt logs into charcoal. The fallback state persists through reload via `FuelCharcoalFallbackActive`; before activation `/staywithme status` reports `workflow=collect_fuel_charcoal: standby`. Coal/charcoal and supply-furnace fuel checks are isolated in `LocalInventoryFallback`.
- `SMELT_ITEM` is backed by PlayerEngine's `SmeltInFurnaceTask`. `/staywithme smelt <target> [amount]` and explicit natural-language smelt/cook requests support controlled outputs `iron_ingot`, `gold_ingot`, `copper_ingot`, and `charcoal`; raw ore aliases such as `raw_iron` normalize to the output item. The task prechecks carried output inventory and only completes after the requested output count is present.
- `FISH`, `FARM`, `SLEEP_THROUGH_NIGHT`, and `EQUIP_ARMOR` are now first-class PlayerEngine-backed task types. `/staywithme fish` starts PlayerEngine `FishTask` until stopped, `/staywithme farm [range]` starts `FarmTask` around the companion, `/staywithme sleep` and `/staywithme night` start `SleepThroughNightTask` when it is nighttime, and `/staywithme equiparmor <target>` starts `EquipArmorTask` for armor materials such as `iron`, `diamond`, `netherite`, or a concrete armor item id such as `minecraft:iron_chestplate`. Natural-language planning and local parser fallback can now produce the same task types. `EQUIP_ARMOR` has a limited Forge fallback, isolated in `LocalArmorEquipFallback`, that equips matching armor already in the companion inventory, but still relies on PlayerEngine to obtain missing pieces.
- `EXPLORE` is a PlayerEngine-backed movement/exploration task. `/staywithme explore [distance]` and natural-language explore/scout/search-around requests start `TimeoutWanderTask(distance, true)` when PlayerEngine is available. Forge fallback chooses a deterministic reachable standable target outward from the companion instead of pure random wandering.
- `GET_OUT_OF_WATER`, `ESCAPE_LAVA`, `CLEAR_LIQUID`, and `PUT_OUT_FIRE` are PlayerEngine-backed safety tasks. `/staywithme outofwater`/`dryland` start `GetOutOfWaterTask`, `/staywithme escapelava` starts `EscapeFromLavaTask`, `/staywithme clearliquid <x> <y> <z>`/`clearwater`/`clearlava` start `ClearLiquidTask`, and `/staywithme putoutfire [range]`/`extinguish [range]` target nearby `FIRE`/`SOUL_FIRE` blocks with `PutOutFireTask`. Natural-language requests such as `swim to shore`, `get out of water`, `escape lava`, `clear water at 10 64 -20`, or `put out fire` can produce these entries. `GET_OUT_OF_WATER` has a limited Forge fallback that searches a 12-block radius for loaded, dry, standable, normally navigable ground and moves there; `ESCAPE_LAVA` has a limited Forge fallback that searches for nearby reachable ground with no adjacent lava, fire, campfire, or magma hazard; both local searches are isolated in `LocalHazardEscapeFallback`; `CLEAR_LIQUID` and `PUT_OUT_FIRE` local block actions are isolated in `LocalBlockSafetyFallback`.
- The same liquid-clearing path is now used inside passage digging. When ordinary resource exploration or mining-expedition staircase/branch tunneling detects adjacent water/lava around the next dig target, it first tries PlayerEngine `ClearLiquidTask` or the limited reachable-block fallback before rotating away from the tunnel direction.
- Mining-expedition `DESCEND_TO_LAYER` now attempts PlayerEngine `GetToYTask` once for the nearest target Y in the requested band before digging a local staircase. If the PlayerEngine task cannot start or ends without reaching the band, StayWithMe falls back to the existing survival staircase up/down logic.
- Remote/stalled return travel now tracks a per-target `remote_goto` attempt. While PlayerEngine reports `started(goto:...)` or `running(goto:...)`, StayWithMe lets `GetToBlockTask` continue; `callback_finished` near the target completes the return, while `inactive_without_finish`, PlayerEngine becoming unavailable, a finish away from the target, or about 45 seconds without block-position progress marks that target attempted and falls back to the construction-route executor.
- `/staywithme status` now includes `remoteGoto=target=...,label=...,attempted=...,active=...` so remote return tests can confirm a failed PlayerEngine `goto` is not being restarted every tick.
- `ATTACK_NEARBY_HOSTILE` now prefers PlayerEngine `KillEntityTask` for weapon selection, approach, and attack timing when PlayerEngine is loaded. `/staywithme attack`, `/staywithme fight`, `/staywithme ask attack`, and matching natural-language requests can enter this task. If PlayerEngine cannot start, it falls back to the older local approach-and-attack loop. The task now completes after the selected hostile dies instead of immediately retargeting another nearby hostile.
- `PROTECT_PLAYER` is a continuous PlayerEngine-backed mode. `/staywithme protect`, `/staywithme hero`, and natural-language protect/guard/defend requests start `HeroTask`, which hunts nearby hostile mobs and picks hostile drops until `/staywithme stop`.
- `RETREAT_FROM_HOSTILES` is a PlayerEngine-backed one-shot safety task. `/staywithme retreat [distance]`, `/staywithme flee [distance]`, and natural-language retreat/flee/run-away requests start `RunAwayFromHostilesTask` and complete once no hostile remains within the requested distance. If PlayerEngine is unavailable, cannot start, or ends with a hostile still nearby, `LocalThreatSafetyFallback` tries to move to a reachable standable block outside the requested hostile distance.
- `RETREAT_FROM_CREEPERS` is a creeper-specific safety task. `/staywithme creeperretreat [distance]`, `/staywithme fleecreeper [distance]`, and natural-language avoid/flee-creeper requests start PlayerEngine `RunAwayFromCreepersTask` when available. If PlayerEngine is unavailable, the same small Forge fallback tries to move to a reachable standable block outside the requested creeper distance.
- `DODGE_PROJECTILES` is a PlayerEngine-backed one-shot projectile safety task. `/staywithme dodge [distance]` and natural-language dodge-arrow/projectile requests start `DodgeProjectilesTask` and complete when PlayerEngine reports the current position is safe from tracked projectile paths. Without PlayerEngine, `LocalThreatSafetyFallback` sidesteps incoming projectile trajectories when a reachable safe stand position exists, or retreats from a nearby line-of-sight skeleton if no projectile entity is currently tracked.
- `PROJECTILE_PROTECTION_WALL` is a PlayerEngine-backed reactive cover task. `/staywithme projectilewall [range]`, `/staywithme arrowwall [range]`, and natural-language block-arrow/build-cover requests start `ProjectileProtectionWallTask`, which places throwaway blocks against skeleton arrows. Without PlayerEngine, it completes if no nearby line-of-sight skeleton threat is present, or `LocalThreatSafetyFallback` places cobblestone, cobbled deepslate, dirt, or netherrack between the companion and the nearest skeleton when the placement is reachable.
- PlayerEngine-only pickup/building-material/give/deposit/fish/farm/sleep/protect tasks fail visibly when PlayerEngine is unavailable instead of silently falling into unrelated Forge-native logic. `PICKUP_DROPPED_ITEM` completes when the requested carried item count is satisfied after `PickupDroppedItemTask`; `COLLECT_BUILDING_MATERIALS` completes once current route-repair block inventory reaches the requested count after `GetBuildingMaterialsTask`; `GIVE_ITEM`, `DEPOSIT_INVENTORY`, and `SLEEP_THROUGH_NIGHT` complete when PlayerEngine reports callback completion; `EQUIP_ARMOR` completes only when the requested armor is physically equipped, using PlayerEngine or the carried-armor fallback. `SLEEP_THROUGH_NIGHT` also completes immediately when it is already daytime, and `DEPOSIT_INVENTORY` can complete immediately when there is no depositable inventory. `FISH`, `FARM`, and `PROTECT_PLAYER` are intentionally continuous tasks that should be stopped explicitly.
- `WorldSnapshot` now includes `playerEngineLoaded`, `playerEngineControllerEnabled`, `highLevelTaskEntryPoints`, `npcTaskSummary`, and `lastFailure` so `/staywithme ask` planning can see the current execution mode and recent task outcome instead of relying only on the static system prompt.
- PlayerEngine catalogue names are normalized through `PlayerEngineCatalogueNames`, so common user/LLM forms such as `minecraft:oak_log`, `logs`, `sticks`, `torches`, `cobble`, `iron_ore`, `deepslate_iron_ore`, `lapis`, and `nether_quartz_ore` resolve toward PlayerEngine names such as `log`, `stick`, `torch`, `cobblestone`, `raw_iron`, `lapis_lazuli`, and `quartz`.
- `/staywithme catalogue [query]` lists matching PlayerEngine `TaskCatalogue.resourceNames()` entries when PlayerEngine is loaded, reports how the query resolves through StayWithMe's local alias candidates, and shows closest catalogue names when no substring match exists.
- `/staywithme capabilities` prints the current high-level task entry points, whether PlayerEngine-first execution is enabled, and what remains in Forge fallback. Use it when deciding which entry an LLM/tool layer should call.
- `/staywithme mine <resource> [amount]` still uses registered mining targets for expedition-aware minerals, but if an unknown target is resolvable by PlayerEngine TaskCatalogue it now starts a broad get task instead of failing immediately.
- The same broad-get fallback is wired at the behavior layer for non-expedition `MINE_RESOURCE` tasks, so LLM/local parser output can still use PlayerEngine for catalogue resources that are not in `MiningTargetRegistry`.
- `/staywithme status` now exposes PlayerEngine runner details including current chain, chain task count, status report, `highLevel` task signature, task ticks, and whether a request finished through the normal callback or became inactive without a finish callback.
- Before starting PlayerEngine acquisition, the controller now checks whether the target inventory condition is already satisfied. This check runs even when PlayerEngine is unavailable, so already-complete tasks can finish before fallback work starts.
- Common PlayerEngine catalogue and plural names now have Forge-side inventory matchers too. This lets completion checks recognize broad targets such as `log`, `planks`, `sticks`, `torches`, ore-block aliases that drop raw minerals, and quartz/lapis/redstone aliases after PlayerEngine or fallback acquisition changes inventory.
- Generic `GET_ITEM`/`CRAFT_ITEM` tasks whose target is a registered mining resource now fall back to the same local mining workflow as `/staywithme mine`, so `get cobblestone`, `get coal`, or `get raw_iron` can still execute when PlayerEngine is absent or fails.
- `MiningTargetRegistry` now includes common generic-get aliases such as `cobble`, `cobbles`, `diamonds`, `emeralds`, and plural raw mineral forms so command-style `GET_ITEM` targets can reach local mining fallback without relying on the chat parser.
- If a PlayerEngine high-level request does not start, the behavior layer emits a throttled fallback notice. If no Forge-native workflow exists either, the visible failure includes the last PlayerEngine status such as `missing_catalogue_task(...)`, `catalogue_returned_null(...)`, or `task_controller_unavailable`.
- `missing_catalogue_task(...)`, `missing_pickup_target(...)`, and `missing_give_target(...)` statuses now include local alias candidates plus closest PlayerEngine catalogue matches to speed up in-game debugging.
- `MineActionAdapter` only mines a concrete exposed/reachable target after moving into survival reach. The old type-only `mineAny()` entry point has been removed.
- `FriendEntity` itself still does not directly implement external PlayerEngine interfaces, so missing PlayerEngine does not break core entity class loading.
- If PlayerEngine is unavailable or a PlayerEngine request fails, execution remains Forge-native.

## Workflow System

Important classes:

- `ai/workflow/WorkflowFactory.java`
- `ai/workflow/LongTaskWorkflow.java`
- `ai/workflow/WorkStep.java`
- `ai/workflow/WorkStepType.java`
- `ai/LocalBehaviorController.java`

Current step types:

- `ACQUIRE_ITEM`
- `CRAFT_ITEM`
- `PLACE_BLOCK`
- `SMELT_ITEM`
- `DESCEND_TO_LAYER`
- `BRANCH_MINE_RESOURCE`

`LocalBehaviorController` is large and owns most MVP execution logic. It should be refactored later, but do not split it casually until tests or a clear boundary exist.

## Mining Targets

Important class:

- `ai/mining/MiningTargetRegistry.java`

Supported executable mining targets:

- `minecraft:cobblestone`
- `minecraft:coal`
- `minecraft:raw_iron`
- `minecraft:diamond`
- `minecraft:lapis_lazuli`
- `minecraft:redstone`
- `minecraft:raw_gold`
- `minecraft:emerald`
- `minecraft:raw_copper`
- `minecraft:quartz`

Common ore-block aliases such as `minecraft:iron_ore`, `minecraft:deepslate_iron_ore`, `minecraft:gold_ore`, `minecraft:copper_ore`, `minecraft:diamond_ore`, `minecraft:lapis_ore`, `minecraft:redstone_ore`, `minecraft:emerald_ore`, and `minecraft:nether_quartz_ore` normalize to the supported drop targets above. Unknown modded resources intentionally remain unsupported at executor level until plugin APIs are added.

## Memory

Important classes:

- `memory/FriendMemory.java`
- `memory/ResourceKnowledge.java`
- `memory/JsonMemoryStore.java`

Current memory supports:

- recent conversations
- recent tasks
- player preferences
- learned resource hints
- structured mining expedition memory
- portable notes
- export/import

Recent change:

- First PlayerEngine-first refactor is implemented:
  - root `AGENTS.md` now requires checking latest Player2NPC and PlayerEngine GitHub source before development
  - Forge PlayerEngine jar was inspected directly; high-level APIs are under `com.player2.playerengine.*`
  - entity creation now reflects a PlayerEngine provider subclass only when PlayerEngine is loaded
  - `FriendPlayerEngineController` now wraps `PlayerEngineController` and `TaskCatalogue.getItemTask(...)`
  - `LocalBehaviorController` tries PlayerEngine acquisition before old local workflows for generic survival acquisition tasks
  - `USE_PLAYERENGINE_CONTROLLER` now defaults to true, with Forge fallback still available
- PlayerEngine-first acquisition was tightened:
  - shared catalogue-name normalization now covers generic wood, planks, sticks, stripped logs, ore blocks, raw mineral drops, lapis/quartz aliases, and common pickaxe aliases
  - `/staywithme catalogue [query]` was added for in-game PlayerEngine TaskCatalogue inspection and alias debugging
  - unknown `/staywithme mine` targets can fall through to a PlayerEngine broad get when TaskCatalogue recognizes the item
  - non-expedition `MINE_RESOURCE` tasks can also fall through to PlayerEngine broad acquisition when the local mining registry does not know the target
  - `/staywithme get` now accepts PlayerEngine catalogue-style word targets instead of requiring a ResourceLocation, so smoke tests can use `get log 4`, `get sticks 4`, and `get torches 16`
  - Forge-side inventory satisfaction and crafting planner fallback now normalize common catalogue/plural aliases including `log/logs`, `plank/planks`, `stick/sticks`, `torch/torches`, `cobble/cobblestone`, station plurals, and common pickaxe aliases
  - LLM task planning prompt now allows compact PlayerEngine catalogue-style targets for broad get/craft requests and asks the model to preserve requested quantities
  - `GET_ITEM` was added as a first-class `FriendTaskType`; command `/staywithme get`, unknown `/mine` PlayerEngine fallback, and local parser `get/obtain/fetch/collect <item>` now use it instead of overloading `CRAFT_ITEM`
  - `PICKUP_DROPPED_ITEM` was added as a PlayerEngine-backed pickup-only task; command `/staywithme pickup <item> [amount]` and local/LLM dropped-item pickup requests delegate to PlayerEngine `PickupDroppedItemTask`
  - `COLLECT_BUILDING_MATERIALS` was added as a PlayerEngine-backed route-block supply task; commands `/staywithme buildingmaterials [count]`, `/staywithme routeblocks [count]`, and `/staywithme bridgeblocks [count]` delegate to PlayerEngine `GetBuildingMaterialsTask`
  - `GIVE_ITEM` was added as a PlayerEngine-backed handoff task; command `/staywithme give <item> [amount]` and local/LLM give/hand/drop/bring requests delegate to PlayerEngine `GiveItemToPlayerTask`
  - `DEPOSIT_INVENTORY` was added as a PlayerEngine-backed storage task; commands `/staywithme deposit` and `/staywithme stash` delegate to PlayerEngine `StoreInAnyContainerTask`
  - `COLLECT_FOOD` and `COLLECT_MEAT` were added as first-class `FriendTaskType` entries; commands `/staywithme food [units]` and `/staywithme meat [units]` delegate to PlayerEngine `CollectFoodTask` and `CollectMeatTask`
  - `FISH`, `FARM`, `EQUIP_ARMOR`, and `PROTECT_PLAYER` were added as PlayerEngine-backed task types; commands `/staywithme fish`, `/staywithme farm [range]`, `/staywithme equiparmor <target>`, and `/staywithme protect` delegate to PlayerEngine `FishTask`, `FarmTask`, `EquipArmorTask`, and `HeroTask`, with `EQUIP_ARMOR` retaining a limited carried-armor Forge fallback
  - `/staywithme ask` world snapshots now include PlayerEngine loaded/enabled state plus the high-level task entry point list
  - `FriendPlayerEngineController` records last acquisition status and uses a watchdog so inactive PlayerEngine task chains can fall back instead of leaving a task stuck forever
  - PlayerEngine owner is refreshed before each high-level controller tick so late owner binding does not leave PlayerEngine with a stale/null owner
  - status output includes PlayerEngine task-runner chain/report data for in-game debugging
  - `/staywithme integrations` now reports the StayWithMe `usePlayerEngineController` config value as well as loaded external mods
  - unsupported broad acquisition failures now include task type, target, and amount instead of only saying a workflow is missing
  - `/staywithme status` now includes `lastFailure="..."` after a failed task so the latest failure reason remains visible after `task=none`
  - `COLLECT_WOOD` now honors requested amount and can use PlayerEngine `get log <amount>` when available
  - task execution checks inventory satisfaction before starting PlayerEngine or fallback acquisition
- Local task planning now parses simple requested quantities and generic `get/obtain/fetch/collect/find <item>` requests better. Examples such as `get 16 torches`, `mine 4 coal`, and `collect 8 logs` should preserve the requested count.
- Local get/craft fallback now strips simple counts from either side of the target, so `get 16 torches`, `get torches 16`, and `craft torches 16` normalize toward `torch` x16 instead of inventing item ids like `torches_16`.
- Local generic obtain parsing recognizes `grab ...` as `GET_ITEM`; explicit `give ...`, `hand ...`, `drop ...`, `bring ...`, and `bring me ...` requests now become `GIVE_ITEM` when the target is a concrete item; `deposit/stash/store/unload inventory` requests become `DEPOSIT_INVENTORY`.
- Workflow safety checks now use a throttled perception snapshot through `currentOrRefresh()`. Manual observation still has an explicit immediate refresh, while active tasks no longer rescan the 101x101 exposed-log area every tick.
- Reachable-log acquisition now also throttles its own 101x101 search to once every 10 ticks while far exploration movement continues each tick. The first search and the search after collecting a log remain immediate.
- Visible mineable-resource acquisition now shares one cached target refresh path for direct mining and branch mining. Empty radius-18 searches are throttled to once every 10 ticks, duplicate branch-mining scans are removed, and a successful block break immediately re-enables scanning for the rest of an exposed vein.
- Expedition resource-hit memory now separates per-block observed yield from cumulative inventory progress. Route-direction scoring uses the local hit delta, while expedition `minedAmount` keeps the cumulative total, preventing later hit points from receiving inflated weights.
- Resource-hit scoring caps the contribution of one observed hit, so older save files that already contain cumulative-style hit amounts cannot let a single legacy point dominate route selection forever.
- Reachable resource and log discovery now use two-stage selection: collect loaded/exposed candidates with cheap block checks, sort by distance, then run navigation reachability checks from nearest to farthest until one succeeds. Dense forests no longer trigger path creation for every exposed log in the 101x101 scan.
- Standability, local resource searches, survival block reach/place operations, and expedition passage checks now reject unloaded chunk positions before reading or acting on them.
- Generic standability now rejects fluid in the companion's feet or head space, and survival placement rejects fluid-filled destination blocks. Navigation, remembered-route reuse, and block placement no longer treat underwater or lava-filled spaces as ordinary safe positions.
- Local staircase and branch-tunnel movement now reuse the expedition movement-stall watchdog. A stalled open passage or stalled dig-approach position records a route note and rotates to another direction; a blocker with no safe dig stand position also rotates immediately instead of repeatedly moving toward the companion's current position.
- Expedition staircase, branch tunnel, fluid-hazard, fluid-leak, and floor-repair checks treat unknown neighboring chunks conservatively as unsafe and rotate away instead of probing or digging into unloaded terrain.
- Unknown neighboring chunks are tracked separately from actual fluid hazards. They block passage expansion conservatively but no longer trigger false lava retreats or write fake lava hazard memory.
- `FriendPerception` and LLM `WorldSnapshot` now report only loaded, exposed breakable logs. Buried logs are no longer counted or surfaced as nearby observations.
- Exposed-log perception now scans the 101x101 search area once per refresh for both nearest-log selection and counting, instead of traversing the same block volume twice.
- Resource exposure checks are shared between perception and local mining. A block is considered exposed only when its own chunk is loaded and it has an empty, fluid-free adjacent position in a loaded chunk.
- `MineActionAdapter` remains targeted-survival-only for Forge fallback: it approaches a selected target, checks survival reach, and breaks that block locally.
- Planner prompts, integration status text, config comments, and observed-mining memory notes now describe PlayerEngine-first execution with Forge-native fallback.
- Resource/item command arguments now use Minecraft resource-location parsing, so namespaced IDs such as `minecraft:diamond` work in `/staywithme mine`, `/staywithme mineplan`, `/staywithme expedition`, `/staywithme craft`, `/staywithme oreinfo`, and `/staywithme memory learnresource`.
- Survival resource discovery is visibility-limited. Local execution should not scan buried ore as if using xray; exposed target blocks are checked with reach/ray validation before mining.
- Wood gathering searches a 101x101 horizontal area for exposed/reachable logs. If no reachable wood is found, it advances through deterministic randomized ring exploration instead of pure back-and-forth random walking.
- Current movement/mining/acquisition prefers PlayerEngine when loaded; Forge-native execution remains fallback.
- Mining expeditions now write portable notes for:
  - expedition start
  - supply chest reused/placed
  - target layer reached
  - inventory unloaded into supply chest
  - expedition mining success
- Mining expeditions now also maintain structured `ExpeditionMemory` entries keyed by target resource and dimension. These entries track requested amount, status, strategy mode, target Y layer, required tool, supply point, supply chest, supply furnace, supply crafting table, mine entrance, last known position, tunnel direction, mined amount, route notes, structured hazards, and lifecycle events.
- Mining expeditions now read `ExpeditionMemory` at startup and conservatively reuse remembered positions when they are in the current dimension, loaded, and still valid:
  - standable supply point
  - existing supply chest
  - existing supply furnace
  - existing supply crafting table
  - standable mine entrance
  - last horizontal tunnel direction
- Descent now performs a one-time return to a remembered mine entrance before digging a new staircase, so route reuse does not keep pulling the companion back after it has started descending.
- Supply resupply now has a first-pass furnace station flow:
  - when returning with a nearly full inventory, the companion finds/reuses the supply chest as before
  - before unloading, it tries to reuse a remembered/nearby furnace, pull a furnace/blast furnace from the supply chest, craft a regular furnace from chest cobblestone when a crafting station path exists, or place a carried furnace near the supply chest
  - it can smelt non-target raw iron, raw gold, and raw copper through the real furnace block entity
  - smelted outputs are inserted into the supply chest when possible
  - if there is no furnace or no fuel, it skips smelting and stores raw materials instead of failing the expedition
  - supply furnace placement/reuse and smelting events are written back into `ExpeditionMemory`
- Supply resupply can now stay active across ticks while supply processing is in progress. If the companion lacks the needed raw input or furnace fuel in its own inventory, it can pull one matching item at a time from the supply chest, including coal, charcoal, logs, planks, or sticks as furnace fuel. Furnace input/output already in the station keeps the supply flow active until it is collected or blocked.
- Supply resupply now exposes an explicit `supplyStatus` in `/staywithme status` and persists it through `ControllerState`. The status reports high-level phases such as returning, finding/placing the supply chest, using/placing the supply furnace, smelting, pulling fuel/input, unloading inventory, storing raw materials, complete, or blocked.
- `/staywithme status` now also summarizes the active expedition supply chest stock when loaded: torches, safe food, cookable food, furnace fuel, sticks, planks, logs, pickaxes, crafting tables, and furnaces/blast furnaces. This is meant to make in-game expedition testing and resupply debugging faster.
- Added `/staywithme expeditionstatus`, a focused in-game debug command that reports only the expedition summary plus workflow progress. Use it during manual expedition testing when the full `/staywithme status` line is too noisy.
- Mining expeditions now opportunistically collect finished supply furnace output even when inventory is not currently full. If the companion is already within 12 blocks of the known supply station, it checks the supply furnace output slot, moves close if needed, and transfers finished output directly into the supply chest with a portable memory note.
- Supply resupply can now expand vanilla storage in a limited way. If the current supply chest cannot accept any remaining non-essential expedition overflow, the companion places a carried chest near the supply point, or crafts one from supply-chest planks/logs when a crafting table path exists, remembers it as the active supply chest, and continues unloading on the next supply tick instead of immediately failing on a full chest.
- Supply stations now recognize both vanilla furnaces and blast furnaces. If the companion carries a blast furnace but no normal furnace, it can place and use the blast furnace as the supply smelting station. `SmeltingActionAdapter` accepts blast furnaces and only chooses inputs with valid blasting recipes for that block.
- Supply station smelting now also supports cooking vanilla food when a regular furnace is available. Raw beef, porkchop, mutton, chicken, rabbit, cod, salmon, potatoes, and kelp can be processed into safe expedition food and stored back in the supply chest. Food cooking deliberately requires a normal furnace; blast furnaces remain available for ore processing only.
- Supply station smelting can now convert logs into charcoal in a regular furnace. Furnace fuel is separate from torch-crafting fuel: the furnace may burn logs, planks, sticks, coal, or charcoal, but torch crafting still only consumes coal/charcoal plus sticks. Cookable food is prioritized ahead of charcoal when both are available.
- Stone/iron-pickaxe expedition workflows now reserve enough sticks and coal for the torch step and prepare a 16-torch starter stock. The runtime restock target uses the same workflow constant, so initial preparation and later supply restocking stay aligned.
- Expedition supply restocking can now craft torches at the supply point when the supply chest has sticks plus coal/charcoal, instead of only pulling already-crafted torches from the chest. If the chest has planks but no sticks, the companion can craft sticks from those planks first and then craft torches.
- Torch restock blocked-state detection now considers torch crafting materials in the supply chest. If the chest has coal/charcoal plus sticks, enough planks to craft sticks, or logs that can be converted into planks first, the companion does not mark torch restock unavailable just because there are no finished torch stacks.
- Expedition tool restocking now respects the current target resource's actual harvest requirement instead of accepting any pickaxe. If a usable matching pickaxe is not already in the supply chest, the companion can craft a replacement at the supply point from chest materials: iron pickaxe from iron ingots plus sticks when the target needs iron tier, or stone pickaxe from cobblestone plus sticks for lower-tier targets. For lower-tier targets it prefers spending cobblestone before iron ingots. If the chest has planks but no sticks, it can craft sticks first.
- Supply-point tool restocking can now prepare its own crafting station. If no usable crafting table is nearby, the companion can pull a crafting table from the supply chest or craft one from chest planks, place it near the supply station, remember it in `ExpeditionMemory`, and then continue crafting replacement pickaxes. The resupply flow distinguishes "working" table placement from completed restock, so it does not prematurely resume while still moving to place the station.
- Expedition resupply now handles basic long-run consumables. During active descent/branch mining, low torches, low food with no carried food, or no usable pickaxe durability can trigger a return to the supply point. At the supply chest, the companion tops torches back up toward 16, pulls a usable spare pickaxe, and carries a small food reserve when available; supply crafting can convert chest logs into planks for sticks, crafting tables, pickaxes, torches, and extra chests. If the chest has no matching supplies, persisted `restockBlocked` flags prevent repeated empty return loops. `/staywithme status` reports torch count, carried food count, best pickaxe durability, and the restock-blocked flags.
- If branch mining still lacks a usable target tool after tool restocking has been marked unavailable, the expedition now records a paused `tool restock unavailable` state and stops cleanly instead of looping forever on `missing tool for branch mining`.
- Mining expeditions now have a first-pass low-health recovery state. When health drops below the danger threshold, the companion returns to the supply point, eats carried food if useful, runs the food/supply-station flow if food is missing, waits for natural regeneration when food is high enough, and resumes the expedition after recovering to a safer health level. If natural regeneration is disabled, recovery supplies are unavailable, or health makes no progress for the recovery timeout, it falls back to pausing the expedition instead of looping forever. `/staywithme status` exposes the recovery flag and wait counter, and reload resume persists both.
- Mining expeditions now have a first-pass hostile-threat retreat state. If several hostiles are nearby or a hostile is very close, the companion returns to the supply point and waits instead of completing the task immediately. Once the local hostile threat clears, it resumes the expedition. If the threat stays active too long, it records a paused expedition and stops waiting instead of looping forever. `/staywithme status` exposes the threat-retreat flag and wait counter, and reload resume persists them through `ControllerState`.
- Mining expeditions now use the companion hunger state in the safety loop. If food is low and the companion already carries safe edible vanilla food, it eats immediately, updates the persisted hunger provider, records a portable note, and continues instead of always returning first. Risky vanilla foods such as rotten flesh, spider eyes, poisonous potatoes, pufferfish, raw chicken, and suspicious stew are not treated as expedition food.
- `FriendHungerProvider` now has a first-pass survival tick: exhaustion drains saturation/food, and if natural regeneration is enabled, high food gradually heals the companion while consuming exhaustion. Local block breaking and melee attacks now add small exhaustion costs, so long-running vanilla tasks can gradually consume food. Hunger state, saturation, exhaustion, and regeneration timer remain persisted in entity NBT.
- Expedition descent can now recover when the companion is below the requested Y band. Instead of stalling, the layer executor digs a cautious upward staircase using the same passage risk checks, remembered hazard avoidance, and torch placement loop as downward descent.
- Mining expedition plans and the LLM strategist prompt now include the executable resupply triggers for low food, low torch count, and low usable pickaxe durability, so persisted expedition plans better match the controller behavior.
- Local branch mining now has a first-pass branch pattern instead of only walking a single straight tunnel:
  - keeps a stable main tunnel direction
  - after 12 main-tunnel steps, starts a 5-step side branch
  - alternates side branches left/right unless resource-hit memory gives a clearer side
  - returns to the side-branch anchor before resuming the main tunnel
  - rotates the main tunnel direction and resets the side branch when the next branch passage is unsafe
  - writes branch route notes into `ExpeditionMemory`
- Branch route memory is now partially structured. `ExpeditionMemory` has bounded `branchRoutes` entries with type (`main`/`side`), direction, start/end positions, planned steps, completed steps, and status (`completed`/`interrupted`). Main tunnel segments are recorded when a side branch starts; side branches are recorded when their planned length is reached; interrupted segments are recorded when branch direction rotates for safety.
- Route reuse now consumes structured `branchRoutes` in a conservative first pass. On expedition startup, the controller scans completed branch routes, invalidates bad loaded candidates, builds a lightweight connected-depth map from matching route start/end positions, and scores usable candidates by route progress/depth, connected graph depth, completed steps, route type, and current travel distance. It also builds a waypoint chain from the selected route's predecessor graph and follows those loaded/standable route endpoints before marking the final route target reached. Both main and side routes are supported; side routes also require their anchor to be standable, and after reaching a remembered side endpoint the companion returns to that anchor instead of treating the side dead-end as a new main tunnel. While replaying a remembered branch target, nearby target-resource blocks are still mined opportunistically before continuing route travel. `/staywithme status` exposes `routeTarget`, `routeType`, `routeAnchor`, `routeDepth`, `routeWaypoint`, and whether that target has been reached.
- Remembered route replay now uses the same movement stall watchdog for the remembered mine entrance, waypoint chain, and selected branch route target. If one of these optional replay moves stalls, the controller records/invalidate-skips that route reuse and falls back to local expedition mining instead of waiting forever on a remembered path.
- Expedition route reuse now invalidates stale remembered station positions when the relevant chunk is loaded but the world no longer matches the memory. Remembered supply point, supply chest, supply furnace, and mine entrance are cleared from `ExpeditionMemory` if their standability/block checks fail, with a route note and event recorded.
- Completed branch routes are now also invalidated when route reuse discovers a loaded but unusable endpoint or side-branch anchor. Missing completed-route endpoints/side anchors are marked `invalidated`, and loaded-but-not-standable endpoints/anchors are marked `invalidated` with a route note and event, so future route scans skip them cleanly.
- Expedition memory summaries now break down branch route counts by status: completed, interrupted, and invalidated. This makes `/staywithme memory` more useful when debugging route reuse and invalidation.
- Completed mining expeditions now return before final task completion. When the target amount is satisfied or the workflow finishes, the controller moves back to the remembered supply point first, or to the owner if no supply point is available, then records the expedition as complete. If a reachable supply chest is available at the completion point, it opportunistically unloads non-essential inventory before completing without letting cleanup failure block success. This keeps successful vanilla expeditions from ending with the companion stranded deep in the mine or carrying avoidable tunnel clutter.
- Expedition return movement now has a lightweight stall watchdog. If safety/resupply return movement toward the same target makes no block-position progress for 45 seconds, the expedition records a paused return-path stall and fails visibly instead of waiting forever. If final completion return stalls after the target resource is already satisfied, it records the stall and completes the expedition rather than blocking success indefinitely. `/staywithme status` exposes the active move-watch label and counter.
- `/staywithme status` now exposes more expedition state: resupply active flag, branch main direction, main steps, side direction, side steps remaining, whether it is returning from a side branch, route target, and torch state.
- Torch placement now has a simple spacing strategy. During staircase descent and local branch mining, the controller counts tunnel movement steps and tries to place a torch every 8 underground tunnel steps, while still placing earlier in dark areas. Starting a side branch marks the branch anchor as torch-due so intersections are lit before the side tunnel extends. It skips placement when another torch is nearby, resets the spacing counter when an interval is already covered by a nearby torch, records the torch location in `ExpeditionMemory`, and exposes `torchSteps` / `lastTorch` in `/staywithme status`.
- Expedition passage digging now uses a centralized vanilla expedition risk policy. Local staircase/branch passage digging avoids falling or fragile blocks, powder snow, pointed dripstone, magma/fire/campfires, cactus/berry/wither-rose damage blocks, cobwebs, TNT/tripwire, sculk sensors/shriekers, and vanilla infested blocks instead of treating them as normal tunnel material. Hazard avoidance route notes are written into `ExpeditionMemory`.
- Passage digging now also treats adjacent non-lava fluids as leak hazards. If a proposed tunnel block is next to water or another non-lava fluid, the expedition rotates/records a `fluid` hazard instead of opening the passage and risking a flooded tunnel stall.
- Expedition passage movement can now repair simple one-block floor gaps with carried expendable vanilla blocks before rotating away. It only uses non-target cobblestone, cobbled deepslate, dirt, or netherrack, requires a safe sturdy support below the gap, and still refuses fluid/lava/risky-block cases.
- Survival interaction stand-position selection no longer chooses the target block itself or a position supported by a block that is about to be mined. The interaction layer also refuses to break the companion's current floor block, preventing resource collection from digging away its own footing and dropping into a one-way hole.
- Prepared staircase and branch-tunnel steps now use a one-block direct movement assist instead of asking global path navigation to rediscover each newly opened local transition. Upward steps request a jump, and downward staircase excavation clears the extra forward head-space needed to pass the descending corner.
- Placement adapters now fail when no safe approach position exists instead of repeatedly walking toward the placement cell itself. Crafting-table and furnace return movement also require a safe reachable stand position and fail visibly if movement stalls, avoiding silent infinite station-return loops.
- Crafting-table and furnace return recovery now attempts a bounded construction route before failing. It captures an abstract local voxel model, optionally asks the configured LLM for adjacent relative feet positions, and falls back to local voxel A*. Java validates each live transition and performs only survival-feasible digging, supported one-block floor repair, and one-step vertical movement. `/staywithme status` exposes the active `construction` route state.
- The in-game API manager is now server-backed instead of relying on client-local config reads. Open it with `O` or `/staywithmeconfig`. It has separate `API` and `Advanced` tabs, OpenAI/DeepSeek/Ollama presets, masked replacement-key entry, explicit key clearing, server save feedback, and an async `Test Connection` action. The server only returns whether a key is configured, never the stored secret. Dedicated-server save/test operations require operator permission level 2.
- API-manager save handling now distinguishes a real save acknowledgement from an initial configuration snapshot. Locally edited fields are not overwritten by a late load response, a replacement key remains visible until the server confirms persistence, and `Test Connection` is available after a key is saved even while LLM behavior is still disabled.
- Ordinary workflow resource acquisition no longer stalls forever when no exposed reachable mineral is visible. Every registered mineral now has an exploration dimension, abundance-oriented Y range, and traversal spacing. Stone-pickaxe preparation and other non-expedition mining steps dig survival staircases toward that layer and then use a randomly rotated expanding spiral tunnel traversal. Local fallback expedition plans reuse the same mineral-specific layer profiles instead of always defaulting to diamond depth. The executor only scans newly exposed blocks and validates tools, fluids, hazards, reach, and movement stalls, so it does not gain ore x-ray information.
- Registered vanilla mineral profiles were cross-checked against Minecraft Wiki and the bundled Minecraft 1.20.1 `OrePlacements` source. Practical tunnel bands are now tightened around reliable peaks (`iron` Y=12..20, `gold` Y=-20..-12, `coal` Y=48..64, `emerald` Y=84..104), Nether quartz uses its full uniform Y=10..117 range, and every profile records the true multi-band/biome caveat such as coal/iron high-altitude peaks, mountain-only emeralds, badlands gold, and dripstone-cave copper. Local expedition fallback no longer overrides the registry with broad stale ranges; both expedition and `/oreinfo` LLM prompts receive the validated 1.20.1 distribution, while local `/oreinfo` covers every registered resource from the same authoritative profile.
- Workflow execution now performs a shared inventory check before every acquisition and crafting step. Wood preparation uses a dynamic remaining-material budget that accounts for carried planks, logs, sticks, existing tools, chests, torches, and an available crafting table instead of always gathering the workflow's original fixed log count. Ordinary mineral exploration also records bounded feet-position breadcrumbs; after gathering a buried resource, crafting-table and furnace returns backtrack the carved staircase/tunnel before falling back to normal navigation and short construction recovery.
- Ordinary targeted mineral approach now has a short stall guard. Near approach positions use the direct one-block movement assist needed for newly carved staircase corners; if an exposed target still cannot be reached, it is temporarily rejected so scanning or tunnel exploration can continue instead of locking onto the same block forever.
- Surface wood exploration now explicitly allows floating navigation and safe water-surface waypoints, so an island start can traverse water toward another loaded land area. The spiral waypoint cursor remains local when a terrain segment has no valid endpoint instead of running permanently into distant unloaded rings. Wood gathering can also clear reachable exposed leaves adjacent to an observed log before retrying the trunk.
- Ordinary underground exploration now protects the support blocks beneath the current feet position and recorded breadcrumb route from targeted stone collection. It repairs supported one-block floor gaps when possible, opens descending staircases from reachable upper blocks first, and keeps stone as a valid cobblestone-producing source without mining away its return route.
- Ordinary mining workflows can dynamically insert a replacement-tool subworkflow if a wooden, stone, or iron pickaxe breaks during later resource acquisition. Existing inventory checks skip already-satisfied preparation steps, and reload recovery avoids restoring a stale dynamic step index when the saved workflow shape differs from the rebuilt base workflow.
- Ordinary resource exploration now distinguishes surface starts from underground traversal. Even when the current Y coordinate is already inside a mineral abundance band, a sky-exposed companion descends toward a lower tunnel anchor before starting horizontal traversal. Safety-triggered direction rotation no longer expands the traversal segment length; only completed movement segments do.
- Passage digging and visible targeted mineral breaking now reject blocks that would release a falling block directly above the mined cell. This supplements direct sand/gravel rejection and adjacent-fluid checks. If ordinary mining is displaced into water, it first backtracks along recorded route breadcrumbs toward a dry passage instead of spinning through direction changes in the water.
- Ordinary mineral exploration now has abnormal mining-route detachment recovery. It counts repeated signals such as fluid displacement, blocked breadcrumb backtracking, repeated unsafe direction rotation at the same position, movement stalls, missing dig approaches, and large vertical offsets after reaching the tunnel anchor. Recovery stays local first by moving back to the latest dry, standable breadcrumb. After repeated failures, it captures the compact construction voxel snapshot plus recent breadcrumbs, optionally asks the configured LLM to classify the detachment and suggest adjacent feet positions, then validates every dig, one-block floor repair, and movement step with the same construction route executor. If no validated route exists, it holds a visible `blocked:no_safe_return_route` state. `/staywithme status` exposes `detachCount`, `detachSource`, `detachTarget`, and `detachStatus` in `resourceExplore`.
- Ordinary detachment recovery no longer mistakes repeated direction rotation during the initial surface staircase search for leaving an established mine route. Once recovery is active, an unrecoverable old breadcrumb route also no longer blocks forever: if the companion's current position is dry and standable, it discards the stale return target, records the current position as a new route anchor, rotates direction, and resumes survival mining from there.
- Coal acquisition now has a charcoal fallback for ordinary workflows. `minecraft:coal` counts coal or charcoal for workflow satisfaction, torch crafting, and furnace fuel. If coal exploration runs for several route turns or a long breadcrumb trail without finding reachable coal ore, the controller inserts a bounded charcoal subworkflow: collect extra logs, keep enough raw logs reserved, craft planks for fuel, prepare/place a regular furnace if needed, and smelt logs into charcoal using planks. This gives iron-smelting and torch-preparation workflows a vanilla survival fallback when coal ore is not found quickly.
- Charcoal fallback no longer starts underground wood exploration from the mine tunnel. Before collecting replacement logs, the ordinary workflow backtracks along recorded resource breadcrumbs and then moves toward a surface wood-gathering anchor, preferring the known crafting table, known furnace if it is at surface, owner position, or a reachable surface waypoint. The charcoal subworkflow also reserves enough extra logs for plank fuel and reuses an existing furnace station instead of forcing a second furnace craft.
- Resource breadcrumb return now removes stale/non-standable breadcrumbs, moves to exact breadcrumb positions instead of stopping merely nearby, and monitors overall breadcrumb-count progress independently of the currently selected target. If ordinary navigation cannot traverse a breadcrumb or return progress stalls, the existing validated short construction-route executor digs or repairs the local return step; failed stale steps are discarded so charcoal surface return and station return cannot loop forever.
- The validated construction-route fallback now searches up to 100 blocks horizontally and 30 blocks vertically per route segment instead of only 10/6. Targets outside one segment are clamped to the search boundary and automatically continue through additional segments after each intermediate endpoint. Its local voxel A* search runs incrementally with bounded per-tick node and time budgets plus a 512-step route limit, so long underground returns do not require a multi-million-cell snapshot or freeze the server thread. The LLM remains restricted to its short local voxel snapshot and is skipped for targets outside that model; long routes are planned by the incremental local search.
- Construction routing now includes validated pure-vertical movement as a high-cost fallback after ordinary paths and stair routes. Safe shaft descent may mine the block directly below only when the next landing has a sturdy floor and the shaft has no adjacent fluid, falling block, block entity, risky block, or unbreakable block. Pillar ascent first clears the two-block head space, then uses a timed jump-and-place state that consumes an allowed repair block under the companion. Consecutive vertical moves are represented in A*, revalidated during execution, exposed in construction status, and bounded by an action timeout.
- The companion's ordinary Minecraft navigation and construction routing now share a maximum allowed fall distance of 6 blocks. A construction fall step must move one horizontal block off the current ledge, pass through a completely open and dry vertical column, avoid nearby fluid hazards, and land on a sturdy non-falling, non-risky floor. The local A*, LLM route contract, and entity navigation all use the same 6-block maximum, and longer construction falls remain invalid.
- Survival block breaking now emits a one-shot event when a pickaxe actually breaks. On the next controller tick, the companion checks for a usable same-tier-or-better backup; with no backup, ordinary workflows immediately insert the appropriate wooden/stone/iron pickaxe recovery before continuing, while mining expeditions immediately enter supply-station return and restock handling. Starting a new task discards stale break events from an earlier task.
- Underground charcoal wood gathering no longer requires the known surface crafting-table/furnace stand position to already be reachable by ordinary navigation. If breadcrumb return is exhausted or unavailable, a fixed known surface station can become a construction-route target, and ordinary surface-return failure invokes the same validated stair, floor-repair, pillar-up, and shaft-down planner.
- Ordinary workflow station placement now filters crafting-table/furnace placement candidates through reachability or a navigable stand position and prefers positions the companion can currently reach. If raw-iron smelting cannot reliably return from a deep mining route to an old surface furnace, the workflow now inserts a local furnace-station recovery (`cobblestone -> furnace -> place furnace`) instead of failing with a long-range construction-route error.
- Ordinary targeted resource acquisition now detects near-target approach ping-pong. If the companion keeps switching between nearby visible targets of the same resource while its own block position does not change, it rejects that small target cluster for a short cooldown and resumes tunnel exploration instead of looping on `Moving to cobblestone ...`. `/staywithme status` exposes `approachWatch` and `rejectCluster` in `resourceExplore` for this debugging path.
- Workflow execution now stops the current tick immediately when a nested station-return or construction-route failure clears the active task/workflow. Crafting-table placement revalidates the active task/workflow before completing a placement, so a failed return cannot continue into a null workflow. This fixes the 2026-06-04 server-tick crash in `executePlaceCraftingTableStep`.
- Crafting-station recovery now prefers the remembered table from the active workflow or expedition, including when its chunk is initially unloaded, before considering a locally carried replacement table. Remote return uses a shared three-level travel policy: nearby ordinary navigation, validated construction travel, and direct teleport only when the task owner has command permission level 2. Expedition safety/resupply return, completion return, remembered mine entrance, remembered route waypoints, and station return use the same policy.
- Construction travel now has a deterministic remote fallback beyond 32 horizontal or 12 vertical blocks, and after local A* exhaustion. It advances in validated 16-step segments using horizontal tunneling, up/down stairs, safe shaft/pillar actions, and horizontal bridging while loading only the immediately modeled route neighborhood. Direct segments have an eight-segment no-progress guard.
- Horizontal bridging is now a real survival action rather than supported-gap repair only. The planner may extend a same-height bridge across air or non-lava fluid one block at a time; execution requires a sturdy current support block, validates the support face/reach, consumes an allowed inventory block, and revalidates every transition.
- Mining expedition safety now treats immediate adjacent lava as an active reroute trigger. If the companion is next to lava during an expedition, it records the hazard origin, returns to the supply point, interrupts the current branch/stair direction at the hazard position, clears remembered route resume state, rotates direction, and resumes instead of immediately completing the task. If the supply return point is also unsafe, it pauses. `/staywithme status` exposes `lavaReroute` and `lavaOrigin`.
- Active expedition mining now records a bounded, loop-compressed stand-position trail during descent and branch mining. Tool/inventory/torch/food resupply freezes that trail, follows it backward toward the supply station, then replays it forward to the exact interrupted work position before the workflow may descend or branch-mine again. Damaged intermediate route segments use validated construction recovery; an unreachable final work position pauses instead of opening a duplicate descent tunnel. The route and replay indices persist in controller NBT, and `/staywithme status` exposes `travelBreadcrumbs`, `resupplyRoute`, and `resumeRoute`.
- Expedition supply restocking now targets two usable target-capable pickaxes. The companion continues mining with its backup after the first pickaxe breaks and only returns when no usable pickaxe remains; whenever any supply return occurs, it pulls or crafts up to one active plus one backup pickaxe when materials permit. Failure to make the optional second pickaxe does not block an expedition that still has one usable pickaxe.
- Expedition hazard avoidance is now partly structured. `ExpeditionMemory` stores bounded hazard notes with type and position for lava/risky-block avoidance, and remembered branch route reuse invalidates or skips completed route candidates whose endpoint, side anchor, or waypoint chain is too close to a remembered hazard. Active staircase/branch digging also keeps a bounded in-task hazard cache and rotates before stepping into known danger zones. `/staywithme status` exposes `knownHazards`; `/staywithme memory` summaries include hazard counts by type.
- Expedition memory now records bounded target resource hit points with resource id, position, route type, direction, and observed amount. Visible target blocks mined during branch mining write success memory as local mining progresses, and remembered route scoring is biased toward completed routes near prior resource hits. New branch mining can choose an initial main direction from remembered hit evidence, new side branches can choose the side direction with stronger resource-hit evidence instead of only alternating left/right, and main/staircase direction rotation can prefer the counter-clockwise alternative when prior hits clearly favor it over the default clockwise turn. `/staywithme status` exposes the remembered resource-hit count while an expedition task is active.
- `FriendEntity` now persists the active `FriendTask` into entity NBT, including task type, player UUID/name, target, amount, message JSON, and reason. On entity reload, it delays restoration until the next server tick and then restarts the task through `FriendBrain`, letting the existing workflow and `ExpeditionMemory` route reuse rebuild transient controller state.
- Reload resume now validates ownership before restarting the saved task. If the saved task has a player UUID, the entity owner must match and the player must currently be available on the server; otherwise the task is not resumed and the companion enters error state with a message. Successful restoration also sends a visible "Resumed saved task after reload" message through the normal companion chat path.
- Reload resume now also persists first-pass workflow progress. When an active task is saved, `FriendEntity` writes `ControllerState` with the current workflow id and step index. On reload, `LocalBehaviorController` restores that index only if the recovered task type, target, amount, and recreated workflow id still match, so completed preparation steps are not restarted unnecessarily.
- `ControllerState` now also persists lightweight transient execution context for reload resume: crafting table target, furnace target, expedition supply point, mine entrance, supply chest, current dig target, remembered route target, branch counters/anchors/directions, resupply-active/recovery-active flags and recovery wait counter, threat-retreat flags and wait counter, lava-reroute flag and origin, known hazard cache, restock-blocked flags for torches/tools/food, and torch spacing counters. On restore, these positions are only accepted if the saved dimension matches, chunks are loaded, and the relevant block/standability check still passes.
- Recovered tasks are now validated before restart beyond ownership. Mining tasks must still target a supported executor resource, mining expeditions must still match the current dimension, and workflow tasks must be rebuildable before `FriendBrain.startTask()` is called. Failures surface a specific resume error instead of restarting into an immediately broken workflow.
- If a recovered task owner is offline, the companion no longer fails the saved task immediately. It keeps the task pending in `WAITING_FOR_OWNER`, emits a throttled waiting message, and persists the pending task/controller state again on later saves so dedicated-server restarts do not discard it before the owner logs back in.
- `/staywithme status` now reports a pending recovered task as `pending recovery: ...` when no current task is active yet, so owner-waiting resume state is visible in game.
- Explicit stop clears any pending recovered task as well as active transient controller state, so a waiting reload-resume task does not restart after the player cancels it.
- Added `docs/RELOAD_RESUME_TESTS.md`, a focused in-game/manual checklist for craft workflow resume, expedition resume, owner-offline wait, supply station resume, and explicit stop behavior.
- Added `docs/EXPEDITION_MANUAL_TESTS.md`, a focused in-game checklist for vanilla mining expedition startup, resupply, hazard handling, remembered route replay, movement stall guards, completion return, and status fields.

This is still intentionally simple. Future work should deepen active expedition resume after reload and richer route graphs.

## Key Current Limitations

- No automated visual/gameplay test coverage yet.
- Visibility is currently an exposed-face heuristic plus reach/navigation validation, not a full BFS field-of-view model. Surface exploration and underground tunnel digging remain deterministic local executors.
- `LocalBehaviorController` is too large.
- Expedition descent is conservative and simple.
- No full persistent route graph replay across restarts; branch routes are structured and latest-main-end reuse exists, but the companion does not yet traverse the whole graph.
- Resume-after-world-reload is still first-pass, but active task, workflow index, station targets, remembered-route waypoint progress, and selected expedition executor state are restored from entity NBT. Some low-level action adapter internals are still reconstructed after reload.
- Supply station automation can process chest-backed furnace/blast-furnace queues, can prepare a vanilla crafting table for supply pickaxe restocking, and can craft/place a regular furnace from chest cobblestone, but it still only handles vanilla machines and recipes and does not yet use modded machines.
- No lava bridging yet. Fluid handling now includes explicit coordinate liquid clearing through PlayerEngine `ClearLiquidTask`, a limited reachable-block Forge fallback, and an automatic pre-dig clearing attempt for ordinary resource exploration and expedition tunnel passages. Broader route-policy decisions still treat uncleared fluid as a hazard and rotate/return rather than forcing unsafe tunneling.
- Construction-route recovery is first-pass and intentionally bounded. It currently activates for crafting-table, furnace, ordinary mining-route, underground surface return, expedition return, and remembered-route replay; searches incrementally across 100-block horizontal / 30-block vertical segments; has a deterministic remote direct fallback; supports cautious digging, supported one-block floor repair, horizontal bridging, pure vertical pillar/shaft moves, and validated falls up to 6 blocks; and still needs to be wired into general wood exploration and player-follow return paths.
- Torch spacing is simple fixed-interval placement; it now has low-torch restocking, but does not yet account for branch geometry or full torch inventory budgeting.
- Branch mining has fixed main/side lengths, but it does not yet reason about ore-density feedback.
- Combat remains basic.
- The companion cannot travel across dimensions by itself.
- Modpack support is not pluginized yet.
- PlayerEngine full controller is now bound through a reflected provider entity when PlayerEngine is loaded; fallback behavior still needs focused cleanup and in-game verification.

## Completed From 2026-06-04 TODO

1. Abnormal tunnel-detachment detection and recovery is implemented for ordinary mineral exploration:
   - Counts repeated fluid displacement, breadcrumb-backtrack failure, unsafe rotation without position progress, movement stall, missing dig approach, and vertical anchor offset signals.
   - First attempts local return to the latest dry, standable breadcrumb.
   - After repeated failure, sends compact voxel snapshot plus recent route breadcrumbs to the configured LLM for failure classification and adjacent-feet route advice.
   - Reuses constrained construction route execution for `tryReturnToMiningRoute(...)`; Java validates all movement, digging, and supported floor repair.
   - Falls back to local voxel A* when LLM is disabled, unavailable, or invalid, and holds a visible blocked state if no safe route exists.
   - `/staywithme status` reports detachment count, source, target breadcrumb, and status in `resourceExplore`.

## Recommended Next Steps

1. Keep improving vanilla first.
2. Deepen structured `ExpeditionMemory` route reuse:
   - make route graph replay smarter about unreachable/interrupted intermediate waypoints
   - eventually move from waypoint replay to a true route graph executor
3. Deepen reload resume:
   - persist selected low-level action adapter internals only if reload testing proves it is needed
4. Deepen supply station behavior:
   - support modded smelting plugins later
5. Improve branch mining:
   - route graph replay
   - make the centralized hazard policy configurable
   - ore-density feedback for choosing next branch direction
6. Refactor `LocalBehaviorController` into smaller executors:
   - `CraftingWorkflowExecutor`
   - `MiningWorkflowExecutor`
   - `ExpeditionExecutor`
   - `CombatExecutor`
7. After vanilla is solid, add plugin interfaces:
   - `ResourceAcquisitionPlugin`
   - `RecipeExecutionPlugin`
   - `MachineInteractionPlugin`
   - pack-specific plugins for Create/Mekanism/AE2/etc.

## Quick Test Commands

In game:

```text
/gamerule mobGriefing true
/staywithme spawn
/staywithme status
/staywithme catalogue raw_iron
/staywithme pickup torch 1
/staywithme buildingmaterials 32
/staywithme fuel 4
/staywithme smelt raw_iron 1
/staywithme explore 48
/staywithme outofwater
/staywithme escapelava
/staywithme clearliquid <x> <y> <z>
/staywithme putoutfire 8
/staywithme retreat 16
/staywithme creeperretreat 10
/staywithme dodge 4
/staywithme projectilewall 16
/staywithme protect
/staywithme stop
/staywithme crafttable
/staywithme stonepickaxe
/staywithme ironpickaxe
/staywithme mineplan minecraft:diamond 1
/staywithme expedition minecraft:diamond 1
/staywithme status
/staywithme memory
```

Expected observations:

- Companion creates tools from its own inventory workflow.
- Expedition creates/reuses a supply chest.
- Status shows expedition supply/entrance/direction/dig target.
- If inventory gets full, companion returns and unloads non-essential items.
- Target resource, tools, and torches are retained during unloading.

## Build Verification

Last build command run:

```powershell
.\gradlew.bat build
```

Result: successful on 2026-06-15 after adding PlayerEngine-backed `CLEAR_LIQUID` with `/staywithme clearliquid`/`clearwater`/`clearlava`, local/LLM planner routing, a limited reachable-liquid Forge fallback, automatic pre-dig liquid clearing in resource exploration and expedition passage digging, PlayerEngine `GetToYTask` before local `DESCEND_TO_LAYER` stair digging, remote/stalled return `GetToBlockTask` attempts before construction-route fallback, `COLLECT_FUEL` charcoal fallback workflow, `GET_OUT_OF_WATER` dry-stand fallback, `ESCAPE_LAVA` lava-safe-ground fallback, carried-armor `EQUIP_ARMOR` fallback, local hostile/projectile retreat fallback, carried-block projectile cover fallback, and fallback-helper splits via `LocalArmorEquipFallback`, `LocalHazardEscapeFallback`, `LocalBlockSafetyFallback`, `LocalThreatSafetyFallback`, `LocalInventoryFallback`, `LocalItemMatcher`, `PlayerEngineMovementRunner`, `PlayerEngineArmorEquipRunner`, `PlayerEnginePlaceBlockRunner`, `PlayerEngineBlockSafetyRunner`, `PlayerEngineThreatSafetyRunner`, `PlayerEngineRemoteTravelRunner`, `PlayerEngineYLevelRunner`, `PlayerEngineExploreRunner`, `PlayerEngineFuelRunner`, `PlayerEngineConstructionMaterialRestockRunner`, `ConstructionTravelResult`, `PlayerEngineAcquisitionRequests`, `PlayerEngineAcquisitionRunner`, `PlayerEngineCountedTaskRunner`, `PlayerEngineStartOnlyTaskRunner`, `PlayerEngineConfirmedTaskRunner`, `PlayerEngineFallbackTaskRunner`, `PlayerEngineStatusText`, and `PlayerEngineTaskState` with centralized PlayerEngine start/announcement handling. Existing PlayerEngine-backed entries include `GET_ITEM`, `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, construction-route material restock, `GIVE_ITEM`/`DEPOSIT_INVENTORY`, `SMELT_ITEM`, `EXPLORE`, combat/safety tasks, sleep, food/meat/fuel collection, fish/farm/equip, execution-mode fields in `WorldSnapshot`, `/staywithme capabilities`, catalogue diagnostics, generic-get mining fallback, inventory precheck, and local planner quantity parsing.
