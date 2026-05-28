# StayWithMe Next Codex Summary

## Project

- Mod: `staywithme`
- Mod id: `staywithme`
- Minecraft: Forge 1.20.1
- Java: 17
- Main package: `com.thecguyyyy.staywithme`
- Current build command: `.\gradlew.bat build`
- Last verified result: build passes.
- Manual reload-resume checklist: `docs/RELOAD_RESUME_TESTS.md`

## Current Direction

This project is being developed as a survival-first embodied AI companion mod. The companion must act through an in-world entity body and should obey survival constraints. LLM is treated as a high-level planner, not a tick-level movement controller.

Core principle:

```text
player natural language / command
  -> LLM or local fallback produces structured task/strategy JSON
  -> local workflow/controller executes deterministic actions
  -> PlayerEngine/Baritone is used when available
  -> Forge survival fallback keeps the mod runnable without external runtime mods
```

Do not make the LLM issue per-tick movement, camera, or block-breaking instructions.

## Important Existing Commands

- `/staywithme spawn`
- `/staywithme follow`
- `/staywithme stop`
- `/staywithme status`
- `/staywithme observe`
- `/staywithme crafttable`
- `/staywithme sticks`
- `/staywithme chest`
- `/staywithme woodenaxe`
- `/staywithme woodenpickaxe`
- `/staywithme stonepickaxe`
- `/staywithme furnace`
- `/staywithme ironingot`
- `/staywithme ironpickaxe`
- `/staywithme craft <item> [amount]`
- `/staywithme recipes [query]`
- `/staywithme mine <resource> [amount]`
- `/staywithme mineplan <resource> [amount]`
- `/staywithme expedition <resource> [amount]`
- `/staywithme oreinfo <resource>`
- `/staywithme memory`
- `/staywithme memory export`
- `/staywithme memory import <file>`
- `/staywithme memory learnresource <resource> <hint>`

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
- Mine known resources with PlayerEngine/Baritone first, survival fallback second.
- Keep survival constraints for fallback mining:
  - correct tool is required for blocks that need one
  - tool durability is consumed
  - mobGriefing must allow block breaking
- Generate `MiningExpeditionPlan` through LLM or local fallback.
- Execute first-pass mining expeditions:
  - prepare required tool chain
  - prepare supply chest for from-scratch expedition chains
  - prepare a 16-torch starter stock for stone/iron-pickaxe expedition chains
  - descend toward a target Y layer with stair-step digging
  - avoid digging straight down
  - avoid adjacent lava/fluid hazards
  - place torches in dark tunnels when available
  - branch mine using PlayerEngine/Baritone or local two-high tunnel fallback
  - return to supply point when inventory is nearly full
  - unload non-essential items to supply chest while keeping tools, torches, and target resources
  - return to supply point for low health recovery, temporary hostile-threat retreat, or lava reroute, and pause if recovery is unavailable, hostile threat does not clear, the return point is unsafe, or the task is explicitly cancelled

## LLM Role

LLM currently does:

- `/staywithme ask <message>` task planning.
- `/staywithme oreinfo <resource>` ore/resource distribution analysis.
- `/staywithme mineplan <resource> [amount]` high-level mining expedition planning.
- `/staywithme expedition <resource> [amount]` strategy generation before starting executable expedition workflow.

LLM returns structured JSON only. It should not output prose that Java must parse.

Important classes:

- `ai/TaskPlanner.java`
- `llm/OreDistributionAnalyzer.java`
- `llm/OreDistributionAnalysis.java`
- `llm/MiningExpeditionPlanner.java`
- `llm/MiningExpeditionPlan.java`
- `llm/OpenAICompatibleClient.java`

## PlayerEngine / Player2NPC Compatibility

PlayerEngine is optional and isolated.

Important classes:

- `embodied/EmbodiedController.java`
- `embodied/DummyEmbodiedController.java`
- `embodied/PlayerEngineEmbodiedController.java`
- `playerengine/FriendPlayerEngineController.java`
- `playerengine/FriendAutomatoneBridge.java`
- `playerengine/FriendInventoryProvider.java`
- `playerengine/FriendInteractionProvider.java`
- `playerengine/FriendHungerProvider.java`
- `playerengine/PlayerEngineCompat.java`

Current behavior:

- If config enables PlayerEngine and it is loaded, movement/pathing/mining can use PlayerEngine/Automatone/Baritone.
- If PlayerEngine is unavailable or fails, fallback remains Forge-native.
- `FriendEntity` does not directly implement external PlayerEngine interfaces, to keep runtime safe without PlayerEngine.

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

Unknown modded resources intentionally remain unsupported at executor level until plugin APIs are added.

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
- Mining expedition safety now treats immediate adjacent lava as an active reroute trigger. If the companion is next to lava during an expedition, it records the hazard origin, returns to the supply point, interrupts the current branch/stair direction at the hazard position, clears remembered route resume state, rotates direction, and resumes instead of immediately completing the task. If the supply return point is also unsafe, it pauses. `/staywithme status` exposes `lavaReroute` and `lavaOrigin`.
- Expedition hazard avoidance is now partly structured. `ExpeditionMemory` stores bounded hazard notes with type and position for lava/risky-block avoidance, and remembered branch route reuse invalidates or skips completed route candidates whose endpoint, side anchor, or waypoint chain is too close to a remembered hazard. Active staircase/branch digging also keeps a bounded in-task hazard cache and rotates before stepping into known danger zones. `/staywithme status` exposes `knownHazards`; `/staywithme memory` summaries include hazard counts by type.
- Expedition memory now records bounded target resource hit points with resource id, position, route type, direction, and observed amount. Visible target blocks mined during branch mining now write the same success memory as PlayerEngine/Baritone mining, and PlayerEngine/Baritone branch mining records partial inventory gains instead of waiting until the full requested amount is reached. Remembered route scoring is biased toward completed routes near prior resource hits, new branch mining can choose an initial main direction from remembered hit evidence, new side branches can choose the side direction with stronger resource-hit evidence instead of only alternating left/right, and main/staircase direction rotation can prefer the counter-clockwise alternative when prior hits clearly favor it over the default clockwise turn. `/staywithme status` exposes the remembered resource-hit count while an expedition task is active.
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
- `LocalBehaviorController` is too large.
- Expedition descent is conservative and simple.
- No full persistent route graph replay across restarts; branch routes are structured and latest-main-end reuse exists, but the companion does not yet traverse the whole graph.
- Resume-after-world-reload is still first-pass, but active task, workflow index, station targets, remembered-route waypoint progress, and selected expedition executor state are restored from entity NBT. Some low-level action adapter internals are still reconstructed after reload.
- Supply station automation can process chest-backed furnace/blast-furnace queues, can prepare a vanilla crafting table for supply pickaxe restocking, and can craft/place a regular furnace from chest cobblestone, but it still only handles vanilla machines and recipes and does not yet use modded machines.
- No lava bridging or fluid handling beyond avoidance, the centralized vanilla risk policy, and first-pass retreat/reroute triggers.
- Torch spacing is simple fixed-interval placement; it now has low-torch restocking, but does not yet account for branch geometry or full torch inventory budgeting.
- Branch mining has fixed main/side lengths, but it does not yet reason about ore-density feedback.
- Combat remains basic.
- The companion cannot travel across dimensions by itself.
- Modpack support is not pluginized yet.
- PlayerEngine full controller is not deeply bound; current use is guarded pathing/mining adapter.

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

Result: successful.
