# Reload Resume Manual Test Checklist

Use a disposable world with:

```text
/gamerule mobGriefing true
/staywithme spawn
```

Run `.\gradlew.bat build` before packaging/testing.

## Craft Workflow Resume

1. Start `/staywithme ironpickaxe`.
2. Wait until the companion has completed at least one visible workflow step, such as placing a crafting table or furnace.
3. Save and quit the world, then reload it.
4. Run `/staywithme status`.
5. Expected:
   - `task=` shows the same saved task.
   - `workflow=` resumes at a later step instead of starting from step 1 when the saved step index is still valid.
   - `station=` and `furnace=` reuse valid saved stations when present.

## Broad PlayerEngine Task Resume

1. With PlayerEngine loaded, start `/staywithme get torches 16`, `/staywithme pickup torch 1` with a dropped torch nearby, `/staywithme buildingmaterials 32`, `/staywithme give torch 4`, `/staywithme deposit`, `/staywithme food 10`, `/staywithme meat 10`, `/staywithme fuel 4`, `/staywithme smelt raw_iron 1`, or `/staywithme clearliquid <x> <y> <z>` against a reachable water/lava source.
2. Save and quit while `/staywithme status` shows an active `GET_ITEM`, `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, `GIVE_ITEM`, `DEPOSIT_INVENTORY`, `COLLECT_FOOD`, `COLLECT_MEAT`, `COLLECT_FUEL`, `SMELT_ITEM`, `GET_OUT_OF_WATER`, `ESCAPE_LAVA`, `CLEAR_LIQUID`, `PUT_OUT_FIRE`, `RETREAT_FROM_HOSTILES`, `RETREAT_FROM_CREEPERS`, `DODGE_PROJECTILES`, or `PROJECTILE_PROTECTION_WALL` task.
3. Reload the world and run `/staywithme status`.
4. Expected:
   - `task=` shows the recovered `GET_ITEM`, `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, `GIVE_ITEM`, `DEPOSIT_INVENTORY`, `COLLECT_FOOD`, `COLLECT_MEAT`, `COLLECT_FUEL`, `SMELT_ITEM`, `GET_OUT_OF_WATER`, `ESCAPE_LAVA`, `CLEAR_LIQUID`, `PUT_OUT_FIRE`, `RETREAT_FROM_HOSTILES`, `RETREAT_FROM_CREEPERS`, `DODGE_PROJECTILES`, or `PROJECTILE_PROTECTION_WALL` task type and original amount.
   - PlayerEngine-first execution restarts when available.
   - `PICKUP_DROPPED_ITEM` resumes as pickup-only work; if the dropped item despawned or is no longer reachable, it should fail visibly instead of broad-getting or crafting the item.
   - `COLLECT_BUILDING_MATERIALS` resumes with `GetBuildingMaterialsTask` until current route-repair block inventory satisfies the saved count.
   - `GIVE_ITEM` resumes against the saved task player name and should drop the requested item near that player once the item is obtained.
   - `DEPOSIT_INVENTORY` resumes by recomputing current non-tool inventory targets and stores them in a valid nearby or newly placed container.
   - `COLLECT_FUEL` restarts PlayerEngine-first when available. If it had already activated charcoal fallback before reload, `workflow=collect_fuel_charcoal...` should resume from the saved workflow index.
   - `SMELT_ITEM` resumes as PlayerEngine `SmeltInFurnaceTask` for the saved output target and completes only after the output item count is present.
   - `CLEAR_LIQUID` resumes against the saved `x,y,z` coordinate and completes only once that coordinate's fluid state is empty.
   - If PlayerEngine is unavailable after reload, `GET_ITEM` falls back to any rebuildable Forge workflow, `COLLECT_FUEL` falls back to the vanilla charcoal workflow, `GET_OUT_OF_WATER` falls back to a nearby dry reachable stand position, `ESCAPE_LAVA` falls back to nearby reachable lava-safe ground, `CLEAR_LIQUID` can only continue through the limited reachable-liquid block-placement fallback or already-cleared state, `PUT_OUT_FIRE` falls back to close-range Forge-native extinguishing, `RETREAT_FROM_HOSTILES`/`RETREAT_FROM_CREEPERS` fall back to reachable local retreat points, `DODGE_PROJECTILES` falls back to a reachable local sidestep/retreat point, and `PROJECTILE_PROTECTION_WALL` completes if no skeleton threat remains or places local carried-block cover when possible, while `PICKUP_DROPPED_ITEM`, `COLLECT_BUILDING_MATERIALS`, `GIVE_ITEM`, `DEPOSIT_INVENTORY`, `COLLECT_FOOD`/`COLLECT_MEAT`, and `SMELT_ITEM` fail visibly unless the current state already satisfies the request.

## Movement Task Resume

1. With PlayerEngine loaded, start `/staywithme goto <x> <y> <z>` toward a reachable same-dimension coordinate far enough away that it will not complete immediately.
2. Save and quit while `/staywithme status` shows `GO_TO_POSITION`.
3. Reload the world and run `/staywithme status`.
4. Expected:
   - `task=` shows the recovered `GO_TO_POSITION` target formatted as `x,y,z`.
   - PlayerEngine-first execution restarts with a `goto:x,y,z:1.5` high-level signature when available.
   - If PlayerEngine is unavailable after reload, the Forge-native navigation fallback attempts to continue toward the coordinate instead of rejecting the saved task.

## PlayerEngine-Only Task Resume

1. With PlayerEngine loaded, start `/staywithme fish`, `/staywithme farm 10`, `/staywithme explore 48`, `/staywithme protect`, `/staywithme sleep` at night, or `/staywithme equiparmor iron`.
2. Save and quit while `/staywithme status` shows `FISH`, `FARM`, `EXPLORE`, `PROTECT_PLAYER`, `SLEEP_THROUGH_NIGHT`, or `EQUIP_ARMOR`.
3. Reload the world and run `/staywithme status`.
4. Expected:
   - `task=` shows the recovered PlayerEngine-only task type and target/range.
   - `FISH`, `FARM`, and `PROTECT_PLAYER` restart as continuous PlayerEngine tasks and should be stopped explicitly.
   - `EXPLORE` restarts with PlayerEngine when available; without PlayerEngine it should choose a reachable deterministic fallback target instead of failing immediately.
   - `SLEEP_THROUGH_NIGHT` restarts while it is still nighttime and completes immediately if the world is already daytime after reload.
   - `EQUIP_ARMOR` completes once the requested armor is physically equipped. If PlayerEngine reports completion but the armor slot is still wrong, Forge fallback may equip carried matching armor before completion.
   - If PlayerEngine is unavailable after reload, validation rejects broad PlayerEngine-only tasks with a visible PlayerEngine-required message instead of attempting unrelated Forge-native work. `EXPLORE` can still choose a reachable deterministic fallback target, and `EQUIP_ARMOR` can resume only when the requested armor is already equipped or carried.

## Expedition Resume

1. Start `/staywithme expedition minecraft:diamond 1`.
2. Wait until the expedition has created or reused a supply chest and started descending or branch mining.
3. Save and quit the world, then reload it.
4. Run `/staywithme status` and `/staywithme memory`.
5. Expected:
   - The task resumes after reload.
   - `expedition=` shows remembered supply/entrance/route fields when they are still valid.
   - `routeTarget`, `routeType`, `routeAnchor`, `routeDepth`, and `routeWaypoint` are present when branch route memory is usable.
   - Invalid loaded route stations or branch route endpoints are marked in memory instead of being reused forever.
   - If hazards were observed before reload, `knownHazards=` is present in status and `/staywithme memory` reports hazard counts.

## Owner Offline Wait

1. On a server, start a long task with the owner online.
2. Save/restart while the owner is offline.
3. Inspect `/staywithme status` from another player or after login.
4. Expected:
   - The companion enters `WAITING_FOR_OWNER` instead of `ERROR`.
   - `task=` shows `pending recovery: ...`.
   - When the owner returns, the task resumes if validation still passes.

## Supply Station Resume

1. Start an expedition that creates a supply chest and furnace or blast furnace.
2. Put raw iron/gold/copper plus fuel into the supply station flow.
3. Save/reload while supply processing is active or while finished output sits in the furnace output slot.
4. Run `/staywithme status`.
5. Expected:
   - `supplyStatus=` describes the active phase.
   - The saved supply chest/furnace targets are reused if still valid.
   - If the companion is near the supply station, finished furnace output is moved into the supply chest.
   - If no furnace is placed but the supply chest has a furnace/blast furnace, the companion pulls it and places it.
   - If no regular furnace exists but the supply chest has cobblestone and a crafting table path, the companion can craft and place a regular furnace for food/charcoal processing.

## Safety Recovery Resume

1. Start an expedition and create a low-health or hostile-threat retreat situation.
2. Save/reload while `recovery=`, `threatRetreat=`, or `lavaReroute=` is active.
3. Run `/staywithme status`.
4. Expected:
   - Recovery wait counters and threat wait counters resume from saved `ControllerState`.
   - Lava reroute keeps `lavaOrigin=` long enough to rotate away after returning to the supply point.
   - Long recovery or hostile waits eventually pause instead of looping forever.

## Explicit Stop

1. Create a pending recovered task, preferably by reloading while the owner is offline.
2. Use sneak-right-click or `/staywithme stop`.
3. Save/reload again.
4. Expected:
   - The pending recovered task is cleared.
   - The companion does not restart the canceled task.
