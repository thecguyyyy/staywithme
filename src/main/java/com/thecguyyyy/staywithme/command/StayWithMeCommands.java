package com.thecguyyyy.staywithme.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.ai.FriendTask;
import com.thecguyyyy.staywithme.ai.FriendTaskType;
import com.thecguyyyy.staywithme.ai.TaskPlanner;
import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.crafting.RecipeCatalog;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.entity.ModEntities;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.llm.MiningExpeditionPlan;
import com.thecguyyyy.staywithme.llm.MiningExpeditionPlanner;
import com.thecguyyyy.staywithme.llm.OreDistributionAnalyzer;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class StayWithMeCommands {
    private static final int SEARCH_RADIUS = 64;

    private StayWithMeCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("staywithme")
                        .then(Commands.literal("spawn").executes(StayWithMeCommands::spawn))
                        .then(Commands.literal("follow").executes(StayWithMeCommands::follow))
                        .then(Commands.literal("stop").executes(StayWithMeCommands::stop))
                        .then(Commands.literal("crafttable").executes(StayWithMeCommands::craftTable))
                        .then(Commands.literal("craftingtable").executes(StayWithMeCommands::craftTable))
                        .then(Commands.literal("workbench").executes(StayWithMeCommands::craftTable))
                        .then(Commands.literal("sticks").executes(StayWithMeCommands::sticks))
                        .then(Commands.literal("chest").executes(StayWithMeCommands::chest))
                        .then(Commands.literal("woodenaxe").executes(StayWithMeCommands::woodenAxe))
                        .then(Commands.literal("woodaxe").executes(StayWithMeCommands::woodenAxe))
                        .then(Commands.literal("woodenpickaxe").executes(StayWithMeCommands::woodenPickaxe))
                        .then(Commands.literal("woodpickaxe").executes(StayWithMeCommands::woodenPickaxe))
                        .then(Commands.literal("stonepickaxe").executes(StayWithMeCommands::stonePickaxe))
                        .then(Commands.literal("stonepick").executes(StayWithMeCommands::stonePickaxe))
                        .then(Commands.literal("furnace").executes(StayWithMeCommands::furnace))
                        .then(Commands.literal("ironingot").executes(StayWithMeCommands::ironIngot))
                        .then(Commands.literal("iron").executes(StayWithMeCommands::ironIngot))
                        .then(Commands.literal("ironpickaxe").executes(StayWithMeCommands::ironPickaxe))
                        .then(Commands.literal("ironpick").executes(StayWithMeCommands::ironPickaxe))
                        .then(Commands.literal("mine")
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(context -> mineResource(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> mineResource(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("mineplan")
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(context -> minePlan(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> minePlan(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("expedition")
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(context -> miningExpedition(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> miningExpedition(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("craft")
                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                        .executes(context -> craftItem(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> craftItem(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("status").executes(StayWithMeCommands::status))
                        .then(Commands.literal("expeditionstatus").executes(StayWithMeCommands::expeditionStatus))
                        .then(Commands.literal("observe").executes(StayWithMeCommands::observe))
                        .then(Commands.literal("integrations").executes(StayWithMeCommands::integrations))
                        .then(Commands.literal("recipes")
                                .executes(StayWithMeCommands::recipesSummary)
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .executes(StayWithMeCommands::recipesQuery)))
                        .then(Commands.literal("oreinfo")
                                .then(Commands.argument("resource", ResourceLocationArgument.id())
                                        .executes(StayWithMeCommands::oreInfo)))
                        .then(Commands.literal("memory")
                                .executes(StayWithMeCommands::memory)
                                .then(Commands.literal("export").executes(StayWithMeCommands::memoryExport))
                                .then(Commands.literal("import")
                                        .then(Commands.argument("file", StringArgumentType.word())
                                                .executes(StayWithMeCommands::memoryImport)))
                                .then(Commands.literal("learnresource")
                                        .then(Commands.argument("resource", ResourceLocationArgument.id())
                                                .then(Commands.argument("hint", StringArgumentType.greedyString())
                                                        .executes(StayWithMeCommands::memoryLearnResource)))))
                        .then(Commands.literal("ask")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(StayWithMeCommands::ask)))
        );
    }

    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();

        FriendEntity friend = ModEntities.FRIEND.get().create(level);
        if (friend == null) {
            context.getSource().sendFailure(Component.literal("Could not create companion entity."));
            return 0;
        }

        Vec3 spawnPos = player.position().add(player.getLookAngle().normalize().scale(2.0D));
        friend.moveTo(spawnPos.x, player.getY(), spawnPos.z, player.getYRot(), 0.0F);
        friend.setCustomName(Component.literal("Companion"));
        friend.setOwner(player);
        friend.setFriendState(FriendState.IDLE);
        level.addFreshEntity(friend);

        context.getSource().sendSuccess(() -> Component.translatable("commands.staywithme.spawned"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int follow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendEntity entity = friend.get();
        entity.setOwner(player);
        entity.startTask(FriendTask.follow(player.getUUID(), player.getGameProfile().getName(), "Command /staywithme follow"));
        context.getSource().sendSuccess(() -> Component.translatable("commands.staywithme.follow"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        friend.get().stopTask();
        context.getSource().sendSuccess(() -> Component.translatable("commands.staywithme.stop"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int craftTable(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_CRAFTING_TABLE, "crafting_table", "Command /staywithme crafttable", "commands.staywithme.crafttable");
    }

    private static int sticks(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_STICKS, "sticks", "Command /staywithme sticks", "commands.staywithme.sticks");
    }

    private static int chest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_CHEST, "chest", "Command /staywithme chest", "commands.staywithme.chest");
    }

    private static int woodenAxe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_WOODEN_AXE, "wooden_axe", "Command /staywithme woodenaxe", "commands.staywithme.woodenaxe");
    }

    private static int woodenPickaxe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_WOODEN_PICKAXE, "wooden_pickaxe", "Command /staywithme woodenpickaxe", "commands.staywithme.woodenpickaxe");
    }

    private static int stonePickaxe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_STONE_PICKAXE, "stone_pickaxe", "Command /staywithme stonepickaxe", "commands.staywithme.stonepickaxe");
    }

    private static int furnace(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_FURNACE, "furnace", "Command /staywithme furnace", "commands.staywithme.furnace");
    }

    private static int ironIngot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_IRON_INGOT, "iron_ingot", "Command /staywithme ironingot", "commands.staywithme.ironingot");
    }

    private static int ironPickaxe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startWorkflowTask(context, FriendTaskType.MAKE_IRON_PICKAXE, "iron_pickaxe", "Command /staywithme ironpickaxe", "commands.staywithme.ironpickaxe");
    }

    private static int craftItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawItem = commandResourceId(context, "item");
        ResourceLocation itemId = parseItemId(rawItem);
        if (itemId == null) {
            context.getSource().sendFailure(Component.literal("Invalid item id: " + rawItem));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                FriendTaskType.CRAFT_ITEM,
                player.getUUID(),
                player.getGameProfile().getName(),
                itemId.toString(),
                amount,
                null,
                "Command /staywithme craft " + itemId
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.translatable("commands.staywithme.craftitem", itemId.toString(), amount), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int mineResource(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawResource = commandResourceId(context, "resource");
        String normalized = MiningTargetRegistry.normalize(rawResource);
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(normalized);
        if (miningTarget.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No executable mining target for "
                    + normalized
                    + ". Supported now: "
                    + MiningTargetRegistry.supportedTargetsSummary()
                    + ". Use /staywithme oreinfo <resource> to learn a strategy for unsupported resources."));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                FriendTaskType.MINE_RESOURCE,
                player.getUUID(),
                player.getGameProfile().getName(),
                miningTarget.get().resourceId(),
                amount,
                null,
                "Command /staywithme mine " + miningTarget.get().resourceId()
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(
                () -> Component.translatable("commands.staywithme.mine", miningTarget.get().resourceId(), amount),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int minePlan(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawResource = commandResourceId(context, "resource");
        String normalized = MiningTargetRegistry.normalize(rawResource);
        Optional<FriendEntity> friend = findNearestFriend(player);
        MinecraftServer server = player.getServer();
        MiningExpeditionPlanner planner = new MiningExpeditionPlanner();
        planner.planAsync(player, friend, normalized, amount).thenAccept(plan -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                rememberMiningPlan(player, normalized, plan);
                context.getSource().sendSuccess(() -> Component.literal(plan.summary()), false);
            });
        });
        context.getSource().sendSuccess(() -> Component.literal("Planning mining expedition for " + normalized + " x" + amount + "..."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int miningExpedition(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawResource = commandResourceId(context, "resource");
        String normalized = MiningTargetRegistry.normalize(rawResource);
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(normalized);
        if (miningTarget.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No executable expedition target for "
                    + normalized
                    + ". Use /staywithme mineplan <resource> to generate memory, then add a plugin-backed mining target later."));
            return 0;
        }

        MinecraftServer server = player.getServer();
        MiningExpeditionPlanner planner = new MiningExpeditionPlanner();
        planner.planAsync(player, findNearestFriend(player), normalized, amount).thenAccept(plan -> {
            if (server == null) {
                return;
            }
            server.execute(() -> applyMiningExpeditionPlan(context.getSource(), player, miningTarget.get(), amount, plan));
        });
        context.getSource().sendSuccess(() -> Component.literal("Planning executable expedition for " + normalized + " x" + amount + "..."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void applyMiningExpeditionPlan(
            CommandSourceStack source,
            ServerPlayer player,
            MiningTargetRegistry.MiningTarget miningTarget,
            int amount,
            MiningExpeditionPlan plan
    ) {
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            source.sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return;
        }

        rememberMiningPlan(player, miningTarget.resourceId(), plan);
        FriendTask task = new FriendTask(
                FriendTaskType.MINING_EXPEDITION,
                player.getUUID(),
                player.getGameProfile().getName(),
                miningTarget.resourceId(),
                amount,
                JsonUtils.toJson(plan),
                "Mining expedition: " + plan.strategyMode + " y=" + plan.preferredYMin + ".." + plan.preferredYMax
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        source.sendSuccess(() -> Component.literal("Expedition planned and started. " + plan.summary()), false);
    }

    private static void rememberMiningPlan(ServerPlayer player, String resourceId, MiningExpeditionPlan plan) {
        JsonMemoryStore.rememberResource(
                player.getUUID(),
                player.getGameProfile().getName(),
                resourceId,
                plan.memoryHint(),
                plan.source()
        );
    }

    private static int startWorkflowTask(CommandContext<CommandSourceStack> context, FriendTaskType type, String target, String reason, String translationKey) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(type, player.getUUID(), player.getGameProfile().getName(), target, 1, null, reason);
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.translatable(translationKey), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendEntity entity = friend.get();
        context.getSource().sendSuccess(
                () -> Component.literal("State=" + entity.getFriendState().name()
                        + ", controller=" + entity.getFriendBrain().getControllerName()
                        + ", compat={" + entity.getFriendBrain().getControllerStatus() + "}"
                        + ", inventory=" + entity.getInventorySummary()
                        + ", hunger={" + entity.getHungerProvider().summary() + "}"
                        + ", perception={" + entity.getPerception().refreshNow().summary() + "}"
                        + ", task=" + entity.getTaskSummary()),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int expeditionStatus(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendEntity entity = friend.get();
        context.getSource().sendSuccess(
                () -> Component.literal("Expedition={" + entity.getFriendBrain().getExpeditionStatus() + "}"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int observe(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Observation: " + friend.get().getPerception().refreshNow().summary()),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int integrations(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(IntegrationStatus.describe()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int recipesSummary(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        context.getSource().sendSuccess(() -> Component.literal(RecipeCatalog.summary(level)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int recipesQuery(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String query = StringArgumentType.getString(context, "query");
        List<String> matches = RecipeCatalog.describeMatching(level, query, 8);
        if (matches.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No loaded recipes matched: " + query));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Recipe matches for \"" + query + "\": " + matches.size()), false);
        for (String match : matches) {
            context.getSource().sendSuccess(() -> Component.literal(match), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int oreInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawResource = commandResourceId(context, "resource");
        ResourceLocation resourceId = parseItemId(rawResource);
        if (resourceId == null) {
            context.getSource().sendFailure(Component.literal("Invalid resource id: " + rawResource));
            return 0;
        }

        MinecraftServer server = player.getServer();
        OreDistributionAnalyzer analyzer = new OreDistributionAnalyzer();
        analyzer.analyzeAsync(player, resourceId.toString()).thenAccept(result -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                JsonMemoryStore.rememberResource(
                        player.getUUID(),
                        player.getGameProfile().getName(),
                        resourceId.toString(),
                        result.memoryHint(),
                        result.source()
                );
                context.getSource().sendSuccess(() -> Component.literal(result.summary()), false);
            });
        });
        context.getSource().sendSuccess(() -> Component.literal("Analyzing ore distribution for " + resourceId + "..."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int ask(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        String message = StringArgumentType.getString(context, "message");
        submitAsk(context.getSource(), player, friend.get(), message);
        return Command.SINGLE_SUCCESS;
    }

    private static int memory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        FriendMemory memory = JsonMemoryStore.load(player.getUUID(), player.getGameProfile().getName());
        context.getSource().sendSuccess(() -> Component.literal(memory.describe()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int memoryExport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        try {
            Path path = JsonMemoryStore.exportPortable(player.getUUID(), player.getGameProfile().getName());
            context.getSource().sendSuccess(() -> Component.literal("Companion memory exported to " + path.toAbsolutePath()), false);
            return Command.SINGLE_SUCCESS;
        } catch (IOException error) {
            context.getSource().sendFailure(Component.literal("Failed to export companion memory: " + error.getMessage()));
            return 0;
        }
    }

    private static int memoryImport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String fileName = StringArgumentType.getString(context, "file");
        try {
            FriendMemory memory = JsonMemoryStore.importPortable(player.getUUID(), player.getGameProfile().getName(), fileName);
            context.getSource().sendSuccess(
                    () -> Component.literal("Companion memory imported. Companion="
                            + memory.companionName
                            + ", learnedResources="
                            + memory.learnedResources.size()),
                    false
            );
            return Command.SINGLE_SUCCESS;
        } catch (IOException error) {
            context.getSource().sendFailure(Component.literal("Failed to import companion memory from "
                    + JsonMemoryStore.importDirectory().toAbsolutePath()
                    + ": "
                    + error.getMessage()));
            return 0;
        }
    }

    private static int memoryLearnResource(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String rawResource = commandResourceId(context, "resource");
        String hint = StringArgumentType.getString(context, "hint");
        ResourceLocation resourceId = parseItemId(rawResource);
        if (resourceId == null) {
            context.getSource().sendFailure(Component.literal("Invalid resource id: " + rawResource));
            return 0;
        }
        JsonMemoryStore.rememberResource(player.getUUID(), player.getGameProfile().getName(), resourceId.toString(), hint, "player");
        context.getSource().sendSuccess(() -> Component.literal("Learned resource note for " + resourceId + ": " + hint), false);
        return Command.SINGLE_SUCCESS;
    }

    public static void submitAsk(CommandSourceStack source, ServerPlayer player, FriendEntity friend, String message) {
        friend.setOwner(player);
        friend.setFriendState(FriendState.THINKING);
        JsonMemoryStore.appendConversation(player.getUUID(), player.getGameProfile().getName(), "Player: " + message);

        MinecraftServer server = player.getServer();
        TaskPlanner planner = new TaskPlanner();
        planner.planAsync(player, friend, message).thenAccept(task -> {
            if (server == null) {
                return;
            }
            server.execute(() -> applyPlannedTask(source, player, friend, task));
        });

        source.sendSuccess(() -> Component.literal("Thinking..."), false);
    }

    private static void applyPlannedTask(CommandSourceStack source, ServerPlayer player, FriendEntity friend, FriendTask task) {
        if (!friend.isAlive()) {
            source.sendFailure(Component.literal("The companion is no longer available."));
            return;
        }

        friend.setOwner(player);
        friend.startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        source.sendSuccess(() -> Component.translatable("commands.staywithme.ask", task.summary()), false);
    }

    public static Optional<FriendEntity> findNearestFriend(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<FriendEntity> friends = level.getEntitiesOfClass(
                FriendEntity.class,
                player.getBoundingBox().inflate(SEARCH_RADIUS),
                FriendEntity::isAlive
        );
        Optional<FriendEntity> owned = friends.stream()
                .filter(friend -> friend.isOwnedBy(player))
                .min(Comparator.comparingDouble(friend -> friend.distanceToSqr(player)));
        if (owned.isPresent()) {
            return owned;
        }
        return friends.stream().min(Comparator.comparingDouble(friend -> friend.distanceToSqr(player)));
    }

    private static ResourceLocation parseItemId(String rawItem) {
        if (rawItem == null || rawItem.isBlank()) {
            return null;
        }
        String normalized = rawItem.contains(":") ? rawItem.trim() : "minecraft:" + rawItem.trim();
        return ResourceLocation.tryParse(normalized);
    }

    private static String commandResourceId(CommandContext<CommandSourceStack> context, String argumentName) {
        return ResourceLocationArgument.getId(context, argumentName).toString();
    }
}
