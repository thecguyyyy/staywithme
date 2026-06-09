# Agent Instructions

## Required Upstream Reference

Before starting any development work in this repository, first check the latest GitHub source for Player2NPC and use it as a design reference:

- Player2NPC: https://github.com/Goodbird-git/Player2NPC
- PlayerEngine: https://github.com/Goodbird-git/PlayerEngine

Do not rely only on memory or an old local copy. Fetch or inspect the current upstream code before editing, then compare the planned change against the upstream approach.

Player2NPC is a thin NPC/demo layer that delegates most survival behavior to PlayerEngine, AltoClef, Automatone, and Baritone. For any work touching movement, mining, material collection, crafting, smelting, tool management, path recovery, expedition behavior, or LLM task execution, inspect the corresponding PlayerEngine/AltoClef implementation before changing local code.

Useful upstream areas to review:

- `Player2NPC/src/main/java/.../companion/AutomatoneEntity.java`
- `Player2NPC/src/main/java/.../Player2NPC.java`
- `Player2NPC/build.gradle`
- `PlayerEngine/src/autoclef/java/adris/altoclef/TaskCatalogue.java`
- `PlayerEngine/src/autoclef/java/adris/altoclef/commands/GetCommand.java`
- `PlayerEngine/src/autoclef/java/adris/altoclef/tasks/resources/MineAndCollectTask.java`
- `PlayerEngine/src/autoclef/java/adris/altoclef/tasks/resources/SatisfyMiningRequirementTask.java`
- `PlayerEngine/src/autoclef/java/adris/altoclef/tasks/container/SmeltInFurnaceTask.java`

When making architectural decisions, prefer delegating broad Minecraft behavior to PlayerEngine-style task execution instead of rebuilding generic survival automation locally. Keep StayWithMe focused on LLM planning, memory, expedition policy, vanilla-survival constraints, debug UI, and fallback behavior.
