# staywithme

`staywithme` 是一个 Forge 1.20.1 AI companion mod。它的目标不是做一个只会聊天的机器人，而是在 Minecraft 世界中提供一个有实体身体、能跟随玩家、理解玩家指令并执行基础动作的 companion NPC。

## 架构

```text
玩家说话
   |
LLM 慢速理解任务
   |
生成结构化任务
   |
本地行为控制器执行
   |
Minecraft 世界 API 感知方块、实体、位置、背包
   |
任务完成或失败时再问 LLM
```

LLM 只负责高层任务规划。本地行为控制器负责 tick 级移动、寻路、攻击、方块交互和状态更新。

## 为什么不能每 tick 调 LLM

Minecraft server tick 必须稳定运行。LLM 请求有网络延迟、费用、失败率和速率限制，如果每 tick 调用会阻塞或拖慢服务器，并且会让 NPC 的动作控制变得不可预测。本项目只在 `/staywithme ask`、任务开始、任务失败重规划、任务完成总结或重要事件发生时触发慢速规划；tick 内只执行本地状态机。

## 当前 MVP 命令

- `/staywithme spawn`：在玩家附近生成 companion。
- `/staywithme follow`：让最近的 companion 跟随玩家。
- `/staywithme goto <x> <y> <z>`：让最近的 companion 前往当前维度的指定方块坐标，优先使用 PlayerEngine `GetToBlockTask`。
- `/staywithme stop`：停止最近 companion 的当前任务。
- `/staywithme crafttable`：让 companion 自己找原木、制作工作台并放到地上。
- `/staywithme craftingtable` / `/staywithme workbench`：`crafttable` 的别名。
- `/staywithme sticks`：让 companion 获取木材并制作木棍。
- `/staywithme chest`：让 companion 获取木材并制作一个箱子。
- `/staywithme woodenaxe` / `/staywithme woodaxe`：让 companion 获取木材、准备工作台并制作木斧。
- `/staywithme woodenpickaxe` / `/staywithme woodpickaxe`：让 companion 获取木材、准备工作台并制作木镐。
- `/staywithme stonepickaxe` / `/staywithme stonepick`：让 companion 获取木材、做木镐、挖圆石并制作石镐。
- `/staywithme furnace`：让 companion 获取木材、做木镐、挖圆石并制作熔炉。
- `/staywithme ironingot` / `/staywithme iron`：让 companion 从零开始尝试制作工具、挖圆石/煤/铁矿、放置熔炉并熔炼铁锭。
- `/staywithme ironpickaxe` / `/staywithme ironpick`：让 companion 从零开始准备木镐、石镐、熔炉、煤和 3 个铁锭，然后制作铁镐。
- `/staywithme craft <item> [amount]`：按服务端 `RecipeManager` 中的原版 `minecraft:crafting` 配方动态规划并制作指定物品，例如 `minecraft:wooden_shovel`。
- `/staywithme status`：显示最近 companion 的状态和当前任务。
- `/staywithme observe`：刷新并显示 companion 当前本地感知到的世界摘要。
- `/staywithme recipes`：显示当前服务端 RecipeManager 已加载的 recipe type 统计。
- `/staywithme recipes <query>`：按配方 id、recipe type、serializer、输出物品或 ingredient 查询当前加载的配方。
- `/staywithme mine <resource> [amount]`：执行第一版可行动采矿策略；已知资源会映射到可挖方块组，优先使用 PlayerEngine/Baritone mine process，失败时回落到本地生存交互。
- `/staywithme attack` / `/staywithme fight`: attack one nearby hostile mob, preferring PlayerEngine `KillEntityTask` when available.
- `/staywithme sleep` / `/staywithme night`: sleep through the night with PlayerEngine `SleepThroughNightTask`; daytime completes immediately.
- `/staywithme clearliquid <x> <y> <z>` / `/staywithme clearwater <x> <y> <z>` / `/staywithme clearlava <x> <y> <z>`: clear a water or lava block at a concrete coordinate with PlayerEngine `ClearLiquidTask`, falling back to a carried throwaway block when reachable.
- `/staywithme mineplan <resource> [amount]`：生成高层采矿远征 JSON 策略并写入长期记忆；LLM 未配置时使用本地原版策略 fallback。
- `/staywithme expedition <resource> [amount]`：先生成远征策略，再启动当前可执行采矿 workflow；低层移动/挖掘仍由本地控制器和 PlayerEngine/Baritone 执行。
- `/staywithme oreinfo <resource>`：异步询问 LLM 分析矿物/资源分布策略，返回 JSON 后写入长期记忆；LLM 未配置时使用本地 fallback。
- `/staywithme ask <message>`：把玩家输入发送给任务规划器；LLM 未启用时使用本地 fallback。
- `/staywithme memory`：显示当前玩家的简单 JSON 记忆。
- `/staywithme memory learnresource <resource> <hint>`：手动写入一条可跨世界迁移的资源知识，例如钻石或模组矿物的获取经验。
- `/staywithme memory export`：导出当前 companion 记忆到 `config/staywithme/memory_exports/`。
- `/staywithme memory import <file>`：从 `config/staywithme/memory_imports/` 导入一个 companion 记忆 JSON。

也可以在聊天中用轻量触发词唤起 companion，例如：

```text
friend follow me
companion collect wood
companion make a crafting table
companion make sticks
companion make a chest
companion make a wooden axe
companion make a wooden pickaxe
companion get iron
companion make an iron pickaxe
伙伴 跟着我
伙伴 做一个工作台
```

## 工作台任务测试

工作台任务是当前长任务链 MVP。它不读取玩家背包，而是使用 companion 自己的 36 格内部背包：

当前实现已经抽成 workflow：

1. `ACQUIRE_ITEM minecraft:logs x1`
2. `CRAFT_ITEM minecraft:planks x4`
3. `CRAFT_ITEM minecraft:crafting_table x1`
4. `PLACE_BLOCK minecraft:crafting_table x1`

执行细节：

1. 找到附近原木。
2. 如果 PlayerEngine/Baritone 可用且配置开启，先尝试使用 mine process 采集原木。
3. 如果 PlayerEngine/Baritone 不可用或采集空转，则移动到原木旁边的可站立位置并使用 survival fallback 破坏。
4. 通过 vanilla recipe manager 把原木合成为对应木板；失败时才使用旧 fallback。
5. 通过 vanilla recipe manager 消耗 4 个木板制作 1 个工作台；失败时才使用旧 fallback。
6. 在玩家或 companion 附近寻找可放置的实体方块上方空间。
7. 通过统一 place adapter 靠近目标并放置工作台。

额外可测试 workflow：

```text
/staywithme sticks
ACQUIRE_ITEM minecraft:logs x1
CRAFT_ITEM minecraft:planks x2
CRAFT_ITEM minecraft:sticks x4

/staywithme chest
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:chest x1

/staywithme woodenaxe
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:sticks x2
CRAFT_ITEM minecraft:wooden_axe x1

/staywithme woodenpickaxe
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:sticks x2
CRAFT_ITEM minecraft:wooden_pickaxe x1

/staywithme stonepickaxe
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:sticks x4
CRAFT_ITEM minecraft:wooden_pickaxe x1
ACQUIRE_ITEM minecraft:cobblestone x3
CRAFT_ITEM minecraft:stone_pickaxe x1

/staywithme ironingot
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:sticks x4
CRAFT_ITEM minecraft:wooden_pickaxe x1
ACQUIRE_ITEM minecraft:cobblestone x11
CRAFT_ITEM minecraft:stone_pickaxe x1
CRAFT_ITEM minecraft:furnace x1
PLACE_BLOCK minecraft:furnace x1
ACQUIRE_ITEM minecraft:coal x1
ACQUIRE_ITEM minecraft:raw_iron x1
SMELT_ITEM minecraft:iron_ingot x1

/staywithme ironpickaxe
ACQUIRE_ITEM minecraft:logs x3
CRAFT_ITEM minecraft:planks x12
CRAFT_ITEM minecraft:crafting_table x1
PLACE_BLOCK minecraft:crafting_table x1
CRAFT_ITEM minecraft:sticks x6
CRAFT_ITEM minecraft:wooden_pickaxe x1
ACQUIRE_ITEM minecraft:cobblestone x11
CRAFT_ITEM minecraft:stone_pickaxe x1
CRAFT_ITEM minecraft:furnace x1
PLACE_BLOCK minecraft:furnace x1
ACQUIRE_ITEM minecraft:coal x1
ACQUIRE_ITEM minecraft:raw_iron x3
SMELT_ITEM minecraft:iron_ingot x3
CRAFT_ITEM minecraft:iron_pickaxe x1

/staywithme mine minecraft:diamond 1
如果 companion 没有铁镐，会先执行铁镐前置链，再回到钻石采集。
```

铁锭链中的熔炼现在不是瞬间模拟：companion 会使用已经放在世界里的真实熔炉方块实体，把 raw iron 放入输入槽、coal 放入燃料槽，等待原版熔炉 tick 完成后再从输出槽取出 iron ingot。使用炉子时会朝向炉子并挥手；如果炉子已经点燃，则不会重复塞入额外燃料。

成功挖到圆石/煤/raw iron 或成功用真实熔炉产出 iron ingot 后，companion 会把这次观察写入 `ResourceKnowledge`，后续可以通过 `/staywithme memory` 查看并通过 `/staywithme memory export` 导出。

测试前确保附近 16 格内有树，并且：

```text
/gamerule mobGriefing true
/staywithme spawn
/staywithme observe
/staywithme crafttable
/staywithme status
/staywithme recipes chest
/staywithme craft minecraft:wooden_shovel
/staywithme craft wooden_sword 1
/staywithme stonepickaxe
/staywithme ironingot
/staywithme ironpickaxe
/staywithme mineplan minecraft:diamond 1
/staywithme expedition minecraft:diamond 1
/staywithme mine minecraft:diamond 1
/staywithme mine coal 4
/staywithme oreinfo minecraft:diamond
/staywithme recipes create:
```

也可以通过 LLM 或本地 fallback 触发：

```text
/staywithme ask make a crafting table and put it down
```

## 采矿远征策略

主动下矿洞、分层挖矿、避险、回家补给不会由 LLM 每 tick 发送低层指令。当前设计是：

```text
LLM 或本地 fallback
   |
生成 MiningExpeditionPlan JSON
   |
本地控制器检查工具、血量、背包、敌对生物、维度、可执行目标
   |
PlayerEngine/Baritone 或 survival fallback 执行移动、挖掘、放置、攻击、回到玩家
```

`MiningExpeditionPlan` 只描述高层策略，例如目标维度、推荐 Y 层、策略模式、前置工具、安全规则和补给触发条件。它不会包含“每 tick 转向多少度”或“下一帧挖哪个像素”这种低层控制。

当前可用命令：

```text
/staywithme mineplan minecraft:diamond 1
/staywithme expedition minecraft:diamond 1
```

`mineplan` 只生成/保存策略；`expedition` 会生成策略并启动远征 workflow。当前第一版远征执行器会：

1. 按目标资源补齐木镐/石镐/铁镐前置链。
2. 记录远征开始位置作为补给点/矿道入口，`/staywithme status` 会显示该位置。
3. 从零开始的远征链会多准备一个箱子，并在补给点附近放置为 supply chest。
4. 如果不在目标 Y 层，先寻找附近已存在的可站立目标层位置。
5. 找不到现成目标层时，用 survival fallback 挖一格一阶的向下楼梯，不会直挖脚下。
6. 到层后优先让 PlayerEngine/Baritone 搜索目标矿物；不可用时挖两格高水平分支矿道。
7. 石镐/铁镐远征链会尝试准备少量火把；矿道过暗且背包有火把时，会在脚下可放置位置放火把。
8. 通道选择会避开相邻熔岩/流体；遇到不安全方向会旋转方向。
9. 执行中如果背包只剩 1 个以内空格，会回补给点，把非必要物品卸入 supply chest，保留目标资源、工具和火把，然后继续当前远征。
10. 如果 companion 血量过低或附近敌对生物威胁过高，会回到补给点并停止当前采矿任务，避免继续冒险。

## LLM 配置

配置文件会生成在 `config/staywithme-server.toml`。

OpenAI:

```toml
[llm]
enabled = true
baseUrl = "https://api.openai.com/v1"
apiKey = "你的 API key"
model = "gpt-4o-mini"
timeoutSeconds = 20
cooldownSeconds = 5
```

DeepSeek:

```toml
[llm]
enabled = true
baseUrl = "https://api.deepseek.com"
apiKey = "你的 DeepSeek API key"
model = "deepseek-chat"
```

Ollama OpenAI-compatible endpoint:

```toml
[llm]
enabled = true
baseUrl = "http://localhost:11434/v1"
apiKey = "ollama"
model = "llama3.1"
```

如果 `enabled=false` 或 `apiKey` 为空，mod 不会崩溃，会使用本地 fallback 解析：

- `follow me` / `跟着我` -> `FOLLOW_PLAYER`
- `stop` / `停止` -> `STOP`
- `wood` / `木头` -> `COLLECT_WOOD`
- `sticks` / `木棍` -> `MAKE_STICKS`
- `chest` / `箱子` -> `MAKE_CHEST`
- `wooden axe` / `木斧` -> `MAKE_WOODEN_AXE`
- `wooden pickaxe` / `木镐` -> `MAKE_WOODEN_PICKAXE`
- `stone pickaxe` / `石镐` -> `MAKE_STONE_PICKAXE`
- `furnace` / `熔炉` -> `MAKE_FURNACE`
- `iron ingot` / `铁锭` -> `MAKE_IRON_INGOT`
- `attack` / `攻击` -> `ATTACK_NEARBY_HOSTILE`

LLM 必须返回 JSON：

```json
{
  "action": "FOLLOW_PLAYER",
  "target": null,
  "amount": 0,
  "message": null,
  "reason": "The player asked the companion to follow."
}
```

## Build

Windows:

```powershell
.\gradlew.bat build
```

macOS/Linux:

```bash
./gradlew build
```

## Optional integrations

The Gradle project now declares optional compile-time hooks for:

- Architectury API Forge `9.2.14`
- PlayerEngine CurseForge project `1322604`, file `8047177`
- SmartBrainLib CurseForge project `661293`, file `5654964`
- Baritone API jars placed manually in `libs/`

These dependencies are intentionally optional at runtime. The default MVP still uses the Forge-native `DummyEmbodiedController` so the mod remains playable without PlayerEngine, Baritone, or SmartBrainLib installed.

For development runs, `gradle.properties` defaults to:

```properties
include_optional_integrations_in_dev_runtime=true
```

That means `runClient` will load Architectury, PlayerEngine, and SmartBrainLib from Gradle so `/staywithme integrations` can verify them. Set it to `false` to test the pure Forge fallback path.

The PlayerEngine adapter is controlled by the generated server config:

```toml
[integrations]
usePlayerEngineController = false
useSmartBrainLibBehaviors = false
useBaritoneWhenAvailable = false
```

Set `usePlayerEngineController = true` only when PlayerEngine is loaded. The current adapter uses PlayerEngine's bundled Automatone/Baritone `IBaritone` layer for movement/pathing and creates a guarded compatibility bridge for PlayerEngine-style inventory, interaction, and hunger managers.

The full `PlayerEngineController` is intentionally not force-enabled yet. Fabric Player2NPC exposes the NPC itself as `IAutomatone`, `IInventoryProvider`, `IInteractionManagerProvider`, and `IHungerManagerProvider`. In Forge, PlayerEngine is still optional, so `FriendEntity` does not directly implement those external interfaces. Instead, `playerengine/FriendAutomatoneBridge` owns the PlayerEngine manager objects when the dependency is present, while the entity remains loadable without PlayerEngine.

Current embodied foundation:

- `FriendPerception`: local world sensing for nearby logs, hostiles, dropped items, standable blocks, dimension, and inventory state.
- `FriendInventoryProvider`: 36-slot player-like inventory wrapper with selected slot and main-hand access.
- `FriendInteractionProvider`: unified interaction entry point for reach checks, block breaking, block placing, attacking, swinging, and future item use.
- `FriendHungerProvider`: lightweight hunger state compatible with the PlayerEngine bridge, defaulting to full food.
- `MineActionAdapter`: tries PlayerEngine/Baritone mining first, then falls back to local survival block breaking.
- `PlaceActionAdapter`: unified reach/move/place flow for block placement.
- `CraftingActionAdapter`: uses Minecraft's vanilla crafting recipes against the companion inventory, with local fallback for the current workbench chain. Recipes that do not fit the 2x2 inventory grid now require a reachable crafting table station.
- `SmeltingActionAdapter`: uses a real placed furnace block entity. The companion inserts smeltable input into slot 0, inserts fuel into slot 1, waits for vanilla furnace ticking, and collects the output from slot 2 into its own inventory.
- `RecipeCatalog`: directly reads the active server `RecipeManager`, including datapack and modded recipes. It can query all loaded recipe types; the current executor only performs `minecraft:crafting` recipes.
- `VanillaCraftingPlanner`: builds simple long-task workflows from loaded vanilla crafting recipes. It recursively plans craftable ingredients, adds log acquisition for wood chains, keeps non-craftable ingredients as "must already be in companion inventory" requirements, and inserts crafting-table placement when a recipe needs a 3x3 grid.
- `RecipeExecutionPlugin`: reserved extension interface for future pack-specific executors. Create or other mod integrations should add plugins here instead of hard-coding behavior into the vanilla workflow controller.
- `FriendMemory` / `ResourceKnowledge`: portable long-term memory for the companion. It stores player relationship history, task history, portable notes, and learned resource hints that can be exported and imported across worlds.
- `OreDistributionAnalyzer`: asynchronously asks an OpenAI-compatible LLM for strict JSON mining distribution/strategy analysis and writes the result into portable resource memory. It uses local vanilla fallbacks when LLM is disabled or on cooldown.
- `ai.workflow`: small reusable workflow model for long embodied tasks such as acquire item -> craft item -> place block.
- `SurvivalWorldInteractor`: fallback survival-like block reach, block breaking progress, drops, and placement.
- `LocalBehaviorController`: still owns the current MVP tasks and falls back to local survival behavior whenever PlayerEngine cannot handle an action safely.

In game, run:

```text
/staywithme integrations
```

to see which external mods are loaded in the current instance.

## API Config UI

In the client, press `O` or run:

```text
/staywithmeconfig
```

This opens a small API configuration screen for:

- enabling/disabling LLM planning
- OpenAI / DeepSeek / Ollama presets
- base URL
- model
- API key update
- timeout and cooldown
- experimental integration toggles

The API key field is intentionally blank when the screen opens. Leave it blank to keep the current server value, type a new key to replace it, or type `CLEAR` to remove the key.

## 当前限制

- 第一版主要是 Forge 原生实体 + 状态机，并带有 PlayerEngine/Automatone 兼容桥。
- PlayerEngine 还没有完全接管实体 tick 和任务执行。
- Baritone/PlayerEngine pathing 可以用于移动，mine adapter 已经先尝试 PlayerEngine/Baritone 后回落 survival。
- place adapter 已经统一放置入口，但放置实现仍主要使用 Forge survival fallback。
- crafting adapter 已经优先使用 vanilla recipe manager，工作台链路仍保留旧 fallback。
- recipe catalog 已经直接读取服务端 `RecipeManager`，可以观测整合包和数据包注册的 recipe type，例如查询 `create:`。
- 当前自动执行器只会执行 `minecraft:crafting` 类型；Create 这类机械/加工配方以后通过 `RecipeExecutionPlugin` 插件式接入。
- 工作台、木棍、箱子、木斧、木镐链路已经迁移到 `ai.workflow`，`/staywithme status` 会显示当前 workflow 步骤。
- 需要 3x3 的合成会先寻找附近可触达工作台；没有工作台时，对应 workflow 可以制作并放置一个工作台，再在该工作台上下文中使用 recipe manager 匹配配方。
- 破坏方块前会从 companion 背包中选择更合适的主手工具；放置方块前会切到对应物品 slot。
- SmartBrainLib 还没有接入。
- 木头采集、工作台、木棍、箱子、木斧、木镐制作仍是简化长任务链：搜索附近 log 方块、移动/挖掘、用内部背包合成并按 workflow 执行。
- 战斗是简化实现：搜索附近 hostile mob、靠近并使用原生近战攻击。

## 下一步计划

- 接入 PlayerEngine 作为真正具身控制层。
- 接入 Baritone 做路径和采集。
- 接入 SmartBrainLib 做行为树/传感器。
- 增加长期记忆。
- 增加语音输入输出。
- 增加皮肤和 companion 个性设定。

## PlayerEngine 接入方向

当前代码已经有 `embodied/EmbodiedController.java`、`PlayerEngineEmbodiedController` 和 `playerengine/*` 兼容层。MVP 会在 PlayerEngine 可用且配置开启时尝试使用 PlayerEngine/Automatone pathing；失败时回落到 Forge 原生 navigation、attack 和 survival interaction。

1. `FriendEntity` 仍保持可独立加载，不直接实现 PlayerEngine 外部接口。
2. `FriendAutomatoneBridge` 在 PlayerEngine 存在时持有 PlayerEngine inventory、interaction 和 hunger manager。
3. `FriendPlayerEngineController` 负责安全初始化、tick、pathing 和失败熔断。
4. `LocalBehaviorController` 继续通过统一 provider 执行任务，避免任务代码直接绑定 PlayerEngine API。

## Current Development Focus

The active development focus is now PlayerEngine-first execution. `StayWithMe` should act as the LLM planning, memory, policy, UI, debugging, and fallback layer, while broad Minecraft survival behavior is delegated to PlayerEngine/TaskCatalogue whenever PlayerEngine is installed.

Reference model from Fabric Player2NPC:

- entity-level provider interfaces for inventory, interaction, and hunger
- player-like `LivingEntityInventory`
- player-like `LivingEntityInteractionManager`
- guarded server tick for the controller
- item pickup and equipment behavior close to a survival player

Current Forge implementation:

- PlayerEngine remains optional at runtime.
- `FriendEntity` stays loadable without PlayerEngine.
- When PlayerEngine is loaded, `FriendEntityFactory` reflects a `PlayerEngineFriendEntity` subclass that implements PlayerEngine inventory, interaction, and hunger provider interfaces.
- `FriendPlayerEngineController` now creates a real `PlayerEngineController`, calls `TaskCatalogue.getItemTask(...)` for high-level acquisition, and wraps PlayerEngine tasks such as dropped item pickup, route building-material supply, furnace smelting, fuel collection, exploration, water/lava escape, coordinate liquid clearing, fire block extinguishing, item handoff, inventory deposit, armor equip, farming/fishing, single-hostile combat, hostile retreat, creeper-specific retreat, projectile dodging, projectile walling, and continuous HeroTask protection.
- Movement/pathing/following/goto/returning/mining/acquisition/combat try PlayerEngine first when enabled, then fall back to Forge-native behavior.
- PlayerEngine acquisition uses shared catalogue-name normalization for common user/LLM forms such as logs, sticks, ore blocks, raw mineral drops, lapis, quartz, and pickaxe aliases.
- `/staywithme follow`, `/staywithme goto <x> <y> <z>`, and return-to-player requests now prefer PlayerEngine `FollowPlayerTask` / `GetToBlockTask` / `GetToEntityTask` before using Forge-native navigation fallback. Remote or stalled station/expedition returns now try PlayerEngine `GetToBlockTask` once before falling back to validated construction-route recovery.
- `/staywithme get <item> [amount]` now accepts PlayerEngine-style catalogue words such as `log`, `sticks`, and `torches` as well as vanilla item ids, with local fallback normalization for common aliases when PlayerEngine is unavailable.
- `/staywithme pickup <item> [amount]` is a PlayerEngine-backed pickup-only entry that uses `PickupDroppedItemTask` for existing dropped item entities and does not mine, craft, or broad-get missing items.
- `/staywithme buildingmaterials [count]`, `/staywithme routeblocks [count]`, and `/staywithme bridgeblocks [count]` use PlayerEngine `GetBuildingMaterialsTask` to prepare throwaway route/bridge/scaffold blocks for path recovery.
- Construction-route recovery can now request PlayerEngine building-material restock internally when a validated route needs bridge, repair, or pillar blocks and the companion has none, then it replans from the new position.
- `/staywithme explore [distance]` uses PlayerEngine `TimeoutWanderTask` when available, with a deterministic Forge fallback that searches for a reachable standable target outward from the current position.
- `/staywithme protect` and `/staywithme hero` use PlayerEngine `HeroTask` to continuously hunt nearby hostile mobs and pick hostile drops until stopped.
- Broad obtain requests now use a dedicated `GET_ITEM` task type instead of overloading `CRAFT_ITEM`; explicit `/staywithme craft` requests still use `CRAFT_ITEM`.
- General food requests use a dedicated `COLLECT_FOOD` task type and `/staywithme food [units]`, backed by PlayerEngine `CollectFoodTask` when PlayerEngine is installed. Carried food/meat inventory satisfaction checks now live in `LocalInventoryFallback`.
- General fuel requests use a dedicated `COLLECT_FUEL` task type and `/staywithme fuel [count]`, backed by PlayerEngine `CollectFuelTask` when PlayerEngine is installed. If PlayerEngine is unavailable or fuel collection finishes without enough coal/charcoal, Forge fallback runs the vanilla charcoal workflow by collecting logs, crafting planks/tools, preparing a furnace, and smelting charcoal. Coal/charcoal inventory checks now live in `LocalInventoryFallback`.
- Explicit furnace smelting requests use `SMELT_ITEM` and `/staywithme smelt <target> [amount]`, backed by PlayerEngine `SmeltInFurnaceTask` for `iron_ingot`, `gold_ingot`, `copper_ingot`, and `charcoal`. Raw ore aliases such as `raw_iron` normalize to the expected output item.
- Sleep/night requests use `SLEEP_THROUGH_NIGHT`, backed by PlayerEngine `SleepThroughNightTask` when the world is not already daytime.
- Water and lava safety requests use `GET_OUT_OF_WATER`, `ESCAPE_LAVA`, and `CLEAR_LIQUID`, backed by PlayerEngine `GetOutOfWaterTask`, `EscapeFromLavaTask`, and `ClearLiquidTask`. `GET_OUT_OF_WATER` has a limited Forge fallback that moves to a nearby dry reachable stand position. `ESCAPE_LAVA` has a limited Forge fallback that moves to nearby reachable ground with no adjacent lava, fire, campfire, or magma hazard. These two local stand-position searches now live in `LocalHazardEscapeFallback`. `CLEAR_LIQUID` targets concrete coordinates such as `10,64,-20`; its carried-block Forge fallback now lives in `LocalBlockSafetyFallback`.
- Ordinary resource exploration and mining-expedition passage digging now try the same liquid-clearing path when a planned dig target has adjacent water/lava risk. If PlayerEngine or the limited reachable-block fallback cannot clear it, the old safety behavior remains: rotate away and remember/avoid the hazard.
- Mining-expedition `DESCEND_TO_LAYER` now tries PlayerEngine `GetToYTask` once for the target Y band before opening a local staircase. If PlayerEngine cannot start or ends without reaching the band, the existing survival staircase up/down fallback continues.
- Nearby fire-block requests use `PUT_OUT_FIRE`, backed by PlayerEngine `PutOutFireTask` when available, with `LocalBlockSafetyFallback` moving into close reach before extinguishing `FIRE` or `SOUL_FIRE` blocks when PlayerEngine is unavailable.
- Armor requests use `EQUIP_ARMOR`, backed by PlayerEngine `EquipArmorTask` for obtaining and equipping missing pieces. If PlayerEngine is unavailable or cannot start, `LocalArmorEquipFallback` can equip matching armor already carried in the companion inventory.
- Generic get/craft tasks targeting registered mining resources now fall back to the local mining workflow, so broad requests such as `get cobblestone` or `get coal` are not treated as impossible crafting recipes when PlayerEngine is absent.
- `/staywithme status` reports PlayerEngine runner/chain/acquisition diagnostics, including watchdog states that allow fallback when a PlayerEngine task chain becomes inactive without a finish callback.
- No-workflow failures for broad acquisition include the last PlayerEngine get status so catalogue misses and controller start failures are visible in game.
- `/staywithme catalogue [query]` can inspect PlayerEngine TaskCatalogue names and StayWithMe alias resolution in game.
- `/staywithme mine <resource> [amount]` keeps registered mineral behavior, but unknown targets that PlayerEngine TaskCatalogue can resolve now fall through to broad PlayerEngine acquisition.
- Local planning now preserves simple requested quantities for fallback `get`, `mine`, `craft`, `smelt`, and wood-collection requests.
- Mining obeys survival drops: fallback block breaking will not destroy blocks that require a correct tool unless the companion inventory contains a suitable tool, and local fallback block breaking consumes tool durability.
- Crafting now uses vanilla crafting recipes before falling back to the previous hard-coded workbench path.
- Runtime recipe catalog now reads the active server RecipeManager, including modded and datapack recipe types.
- `/staywithme craft <item> [amount]` can now generate a workflow from loaded `minecraft:crafting` recipes instead of requiring a dedicated command for every vanilla item.
- Companion memory now has a portable schema with learned resource knowledge and export/import commands, so future worlds can inherit what the companion learned.
- Stone pickaxe, furnace, iron ingot, and iron pickaxe workflows now exist. They use PlayerEngine/Baritone mining first when available, then local survival fallback for reachable blocks.
- `/staywithme oreinfo <resource>` now performs async LLM JSON analysis for ore distribution and stores the strategy in memory.
- `/staywithme mine <resource> [amount]` turns known vanilla resources into executable `ACQUIRE_ITEM` workflows. If the required pickaxe is missing, the workflow now prepends the wooden/stone/iron pickaxe preparation chain before mining. Supported first-pass targets include cobblestone, coal, raw iron, diamond, lapis, redstone, raw gold, emerald, raw copper, and quartz. Unknown targets that PlayerEngine TaskCatalogue can resolve fall through to broad acquisition; unresolved modded resources are still left for future resource/pack plugins.
- `/staywithme mineplan <resource> [amount]` and `/staywithme expedition <resource> [amount]` now use a `MiningExpeditionPlan` JSON contract. LLM output is limited to high-level strategy; local code remains responsible for survival-safe execution, safety interrupts, descending to the target layer, branch mining, and returning to the player for resupply.
- `MINING_EXPEDITION` workflows now have `DESCEND_TO_LAYER` and `BRANCH_MINE_RESOURCE` steps. The descent executor makes a staircase instead of digging straight down; the branch executor uses PlayerEngine/Baritone when available and falls back to local two-high tunnel digging.
- Mining expeditions now remember a supply/entrance point for the current task, set up a supply chest for from-scratch expedition chains, unload non-essential inventory when full, prepare torches for stone/iron-pickaxe expedition chains, place torches in dark tunnels when available, and rotate away from adjacent lava/fluid hazards.
- Recipes that require a 3x3 grid now use a reachable crafting-table station; workflows such as chest can create and place one first.
- Workbench, sticks, chest, wooden axe, wooden pickaxe, stone pickaxe, furnace, iron ingot, iron pickaxe, and executable mining tasks now run through a reusable workflow model.
- Inventory selection now exposes selected slot/main hand behavior for breaking and placing.
- Place still uses the unified interaction provider and survival fallback. Nearby hostile attack now prefers PlayerEngine `KillEntityTask` when available, then falls back to the local survival attack loop; continuous protect mode uses PlayerEngine `HeroTask`.
- Hostile retreat requests use `RETREAT_FROM_HOSTILES`, backed by PlayerEngine `RunAwayFromHostilesTask` when available. If PlayerEngine is unavailable or cannot start, `LocalThreatSafetyFallback` moves to a reachable standable block outside the requested hostile distance.
- Creeper-specific retreat requests use `RETREAT_FROM_CREEPERS`, backed by PlayerEngine `RunAwayFromCreepersTask` when available, with the same limited Forge retreat fallback scoped to the nearest creeper.
- Projectile dodge requests use `DODGE_PROJECTILES`, backed by PlayerEngine `DodgeProjectilesTask`, and complete once PlayerEngine reports the companion is safe from tracked projectile paths. If PlayerEngine is unavailable or cannot start, `LocalThreatSafetyFallback` sidesteps incoming projectile paths or retreats from the nearest line-of-sight skeleton.
- Projectile wall requests use `PROJECTILE_PROTECTION_WALL`, backed by PlayerEngine `ProjectileProtectionWallTask`, to place throwaway-block cover against skeleton arrows. If PlayerEngine is unavailable or cannot start, `LocalThreatSafetyFallback` places carried throwaway blocks between the companion and the nearest line-of-sight skeleton when a reachable placement exists.

Next steps:

- Verify PlayerEngine-first smoke tests in game: `crafting_table`, `stone_pickaxe`, `raw_iron`, `/staywithme smelt raw_iron 1`, and `diamond`.
- Continue reducing `LocalBehaviorController` into fallback-specific executors and PlayerEngine boundary helpers; carried armor equip, water/lava escape stand-search, local block-safety, local threat-safety, local carried-inventory satisfaction checks, basic material/tool counters, construction floor-repair material filtering, supply-furnace block selection, free-slot counting, generic supply-container item count/move helpers, and expedition pickaxe usability/durability checks, item alias/inventory/crafting target resolution, reload-resume PlayerEngine/fallback availability validation, PlayerEngine movement starts, PlayerEngine armor equip running, PlayerEngine water/lava escape running, PlayerEngine block placement running, PlayerEngine block-safety running, PlayerEngine threat-safety running, PlayerEngine hostile attack/protect running, PlayerEngine route-block/food/meat collection running, PlayerEngine furnace smelting running, PlayerEngine fish/farm/sleep routine running, PlayerEngine item pickup/give/deposit running, PlayerEngine remote return/goto attempts, PlayerEngine Y-layer travel attempts, PlayerEngine explore running, PlayerEngine fuel collection with charcoal fallback routing, PlayerEngine construction-material restock, PlayerEngine acquisition request mapping, common PlayerEngine get/acquisition running, counted PlayerEngine task running, start-only PlayerEngine task running, callback-confirmed PlayerEngine task running, PlayerEngine task-with-fallback running, PlayerEngine status text formatting, and PlayerEngine task state/start/announcement handling are now split out as narrow helpers.
- Keep StayWithMe expedition memory and safety policy, but prefer PlayerEngine for recursive material acquisition.
- Add pack/plugin integration only above the PlayerEngine/fallback boundary.
