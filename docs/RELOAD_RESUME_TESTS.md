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
