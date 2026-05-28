# Mining Expedition Manual Tests

Use these checks before treating the vanilla mining expedition path as ready for regular play.

## Setup

- Start a survival world with mob griefing enabled.
- Spawn or use an owned companion.
- Keep `/staywithme expeditionstatus` available for quick expedition-only status checks.
- Use `/staywithme status` when inventory, perception, or integration details are needed.

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
   - Expect a replacement pickaxe to be pulled or crafted.
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

3. Floor gap repair:
   - Create a one-block floor gap with sturdy support below.
   - Give expendable cobblestone, cobbled deepslate, dirt, or netherrack.
   - Expect floor repair before route rotation.

4. Remembered route replay:
   - Complete one expedition, then run the same resource again.
   - Expect route reuse when endpoints are loaded and standable.
   - Block a remembered endpoint or waypoint and verify route invalidation/reselection.

5. Movement stall guard:
   - Temporarily trap the companion during return or remembered-route travel.
   - After about 45 seconds without block-position progress, expect `moveWatch` to reach the limit and the expedition to pause/fallback instead of waiting forever.

## Completion Checks

- When the target amount is reached, expect the companion to return to the supply point or owner before final completion.
- If the final return path stalls after the target is already satisfied, expect completion with `complete: return path stalled`.
- Confirm `/staywithme memory` records status, resource hits, hazards, branch routes, and supply stations.

## Quick Status Fields

- `supplyStatus`: current high-level expedition phase.
- `moveWatch`: movement stall label and counter.
- `supplyStock`: loaded supply chest inventory summary.
- `routeTarget`, `routeWaypoint`, `routeDepth`: remembered route replay state.
- `resourceHits`: remembered target-resource hit count.
- `knownHazards`, `lavaReroute`, `lavaOrigin`: safety/reroute state.
- `restockBlocked`: whether torch/tool/food restock was proven unavailable.
