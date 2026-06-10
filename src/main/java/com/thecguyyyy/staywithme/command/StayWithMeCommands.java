package com.thecguyyyy.staywithme.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thecguyyyy.staywithme.ai.FriendState;
import com.thecguyyyy.staywithme.ai.FriendTask;
import com.thecguyyyy.staywithme.ai.FriendTaskType;
import com.thecguyyyy.staywithme.ai.HighLevelTaskSurface;
import com.thecguyyyy.staywithme.ai.TaskPlanner;
import com.thecguyyyy.staywithme.ai.mining.MiningTargetRegistry;
import com.thecguyyyy.staywithme.config.StayWithMeConfig;
import com.thecguyyyy.staywithme.crafting.RecipeCatalog;
import com.thecguyyyy.staywithme.entity.FriendEntity;
import com.thecguyyyy.staywithme.entity.ModEntities;
import com.thecguyyyy.staywithme.integration.IntegrationStatus;
import com.thecguyyyy.staywithme.llm.MiningExpeditionPlan;
import com.thecguyyyy.staywithme.llm.MiningExpeditionPlanner;
import com.thecguyyyy.staywithme.llm.OreDistributionAnalyzer;
import com.thecguyyyy.staywithme.memory.FriendMemory;
import com.thecguyyyy.staywithme.memory.JsonMemoryStore;
import com.thecguyyyy.staywithme.playerengine.PlayerEngineCatalogueDiagnostics;
import com.thecguyyyy.staywithme.util.JsonUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
                        .then(Commands.literal("goto")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .executes(StayWithMeCommands::goToPosition)))))
                        .then(Commands.literal("place")
                                .then(Commands.argument("block", StringArgumentType.word())
                                        .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                                .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                                .executes(StayWithMeCommands::placeBlock))))))
                        .then(Commands.literal("placeblock")
                                .then(Commands.argument("block", StringArgumentType.word())
                                        .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                                .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                        .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                                .executes(StayWithMeCommands::placeBlock))))))
                        .then(Commands.literal("placethrowaway")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .executes(StayWithMeCommands::placeThrowawayBlock)))))
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
                        .then(Commands.literal("buildingmaterials")
                                .executes(context -> buildingMaterials(context, 32))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                        .executes(context -> buildingMaterials(context, IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("routeblocks")
                                .executes(context -> buildingMaterials(context, 32))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                        .executes(context -> buildingMaterials(context, IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("bridgeblocks")
                                .executes(context -> buildingMaterials(context, 32))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 256))
                                        .executes(context -> buildingMaterials(context, IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("food")
                                .executes(context -> food(context, 10))
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> food(context, IntegerArgumentType.getInteger(context, "units")))))
                        .then(Commands.literal("meat")
                                .executes(context -> meat(context, 10))
                                .then(Commands.argument("units", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> meat(context, IntegerArgumentType.getInteger(context, "units")))))
                        .then(Commands.literal("fuel")
                                .executes(context -> fuel(context, 4))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> fuel(context, IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("smelt")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(context -> smeltItem(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> smeltItem(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("fish").executes(StayWithMeCommands::fish))
                        .then(Commands.literal("farm")
                                .executes(context -> farm(context, 10))
                                .then(Commands.argument("range", IntegerArgumentType.integer(1, 128))
                                        .executes(context -> farm(context, IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("explore")
                                .executes(context -> explore(context, 48))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(8, 256))
                                        .executes(context -> explore(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("sleep").executes(StayWithMeCommands::sleepThroughNight))
                        .then(Commands.literal("night").executes(StayWithMeCommands::sleepThroughNight))
                        .then(Commands.literal("outofwater").executes(StayWithMeCommands::getOutOfWater))
                        .then(Commands.literal("dryland").executes(StayWithMeCommands::getOutOfWater))
                        .then(Commands.literal("escapelava").executes(StayWithMeCommands::escapeLava))
                        .then(Commands.literal("clearliquid")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .executes(StayWithMeCommands::clearLiquid)))))
                        .then(Commands.literal("clearwater")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .executes(StayWithMeCommands::clearLiquid)))))
                        .then(Commands.literal("clearlava")
                                .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-2048, 2048))
                                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                                        .executes(StayWithMeCommands::clearLiquid)))))
                        .then(Commands.literal("putoutfire")
                                .executes(context -> putOutFire(context, 8))
                                .then(Commands.argument("range", IntegerArgumentType.integer(1, 32))
                                        .executes(context -> putOutFire(context, IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("extinguish")
                                .executes(context -> putOutFire(context, 8))
                                .then(Commands.argument("range", IntegerArgumentType.integer(1, 32))
                                        .executes(context -> putOutFire(context, IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("equiparmor")
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .executes(StayWithMeCommands::equipArmor)))
                        .then(Commands.literal("protect").executes(StayWithMeCommands::protectPlayer))
                        .then(Commands.literal("hero").executes(StayWithMeCommands::protectPlayer))
                        .then(Commands.literal("retreat")
                                .executes(context -> retreatFromHostiles(context, 16))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> retreatFromHostiles(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("flee")
                                .executes(context -> retreatFromHostiles(context, 16))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> retreatFromHostiles(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("creeperretreat")
                                .executes(context -> retreatFromCreepers(context, 10))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> retreatFromCreepers(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("fleecreeper")
                                .executes(context -> retreatFromCreepers(context, 10))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> retreatFromCreepers(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("dodge")
                                .executes(context -> dodgeProjectiles(context, 4))
                                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 16))
                                        .executes(context -> dodgeProjectiles(context, IntegerArgumentType.getInteger(context, "distance")))))
                        .then(Commands.literal("projectilewall")
                                .executes(context -> projectileProtectionWall(context, 16))
                                .then(Commands.argument("range", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> projectileProtectionWall(context, IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("arrowwall")
                                .executes(context -> projectileProtectionWall(context, 16))
                                .then(Commands.argument("range", IntegerArgumentType.integer(4, 64))
                                        .executes(context -> projectileProtectionWall(context, IntegerArgumentType.getInteger(context, "range")))))
                        .then(Commands.literal("attack").executes(StayWithMeCommands::attackNearbyHostile))
                        .then(Commands.literal("fight").executes(StayWithMeCommands::attackNearbyHostile))
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
                        .then(Commands.literal("get")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> getItem(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> getItem(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("pickup")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> pickupDroppedItem(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> pickupDroppedItem(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("give")
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> giveItem(context, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> giveItem(context, IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(Commands.literal("deposit").executes(StayWithMeCommands::depositInventory))
                        .then(Commands.literal("stash").executes(StayWithMeCommands::depositInventory))
                        .then(Commands.literal("status").executes(StayWithMeCommands::status))
                        .then(Commands.literal("expeditionstatus").executes(StayWithMeCommands::expeditionStatus))
                        .then(Commands.literal("observe").executes(StayWithMeCommands::observe))
                        .then(Commands.literal("integrations").executes(StayWithMeCommands::integrations))
                        .then(Commands.literal("capabilities").executes(StayWithMeCommands::capabilities))
                        .then(Commands.literal("catalogue")
                                .executes(context -> playerEngineCatalogue(context, ""))
                                .then(Commands.argument("query", StringArgumentType.greedyString())
                                        .executes(context -> playerEngineCatalogue(
                                                context,
                                                StringArgumentType.getString(context, "query")
                                        ))))
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

    private static int goToPosition(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String target = x + "," + y + "," + z;
        return startHighLevelTask(
                context,
                FriendTaskType.GO_TO_POSITION,
                target,
                0,
                "Command /staywithme goto " + target,
                "Going to " + target + "."
        );
    }

    private static int placeBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String rawBlock = StringArgumentType.getString(context, "block");
        Optional<String> block = normalizePlaceBlockCommandTarget(rawBlock);
        if (block.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Invalid placeable block target: " + rawBlock));
            return 0;
        }
        return placeBlock(context, block.get(), "Command /staywithme place", "Placing " + block.get());
    }

    private static int placeThrowawayBlock(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return placeBlock(context, "throwaway", "Command /staywithme placethrowaway", "Placing a throwaway route block");
    }

    private static int placeBlock(
            CommandContext<CommandSourceStack> context,
            String blockTarget,
            String reason,
            String feedbackPrefix
    ) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String encodedTarget = blockTarget + "@" + x + "," + y + "," + z;
        return startHighLevelTask(
                context,
                FriendTaskType.PLACE_BLOCK,
                encodedTarget,
                1,
                reason + " " + encodedTarget,
                feedbackPrefix + " at " + x + "," + y + "," + z + "."
        );
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

    private static int buildingMaterials(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.COLLECT_BUILDING_MATERIALS,
                "building_materials",
                count,
                "Command /staywithme buildingmaterials",
                "Collecting route building materials x" + count + " with PlayerEngine."
        );
    }

    private static int food(CommandContext<CommandSourceStack> context, int units) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                FriendTaskType.COLLECT_FOOD,
                player.getUUID(),
                player.getGameProfile().getName(),
                "food",
                units,
                null,
                "Command /staywithme food"
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.literal("Collecting food units x" + units + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int fish(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.FISH,
                "fish",
                1,
                "Command /staywithme fish",
                "Fishing with PlayerEngine until stopped."
        );
    }

    private static int meat(CommandContext<CommandSourceStack> context, int units) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.COLLECT_MEAT,
                "meat",
                units,
                "Command /staywithme meat",
                "Collecting meat food units x" + units + " with PlayerEngine."
        );
    }

    private static int fuel(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.COLLECT_FUEL,
                "fuel",
                count,
                "Command /staywithme fuel",
                "Collecting fuel items x" + count + " with PlayerEngine."
        );
    }

    private static int smeltItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawTarget = StringArgumentType.getString(context, "target");
        Optional<String> target = normalizeHighLevelWordTarget(rawTarget)
                .flatMap(StayWithMeCommands::normalizeSmeltCommandTarget);
        if (target.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Smelt target must be iron_ingot, gold_ingot, copper_ingot, charcoal, or a raw ore alias."));
            return 0;
        }
        return startHighLevelTask(
                context,
                FriendTaskType.SMELT_ITEM,
                target.get(),
                amount,
                "Command /staywithme smelt",
                "Smelting " + target.get() + " x" + amount + " with PlayerEngine furnace automation."
        );
    }

    private static int farm(CommandContext<CommandSourceStack> context, int range) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.FARM,
                "crops",
                range,
                "Command /staywithme farm",
                "Farming nearby crops within range " + range + " until stopped."
        );
    }

    private static int explore(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.EXPLORE,
                "area",
                distance,
                "Command /staywithme explore",
                "Exploring about " + distance + " blocks with PlayerEngine-first movement."
        );
    }

    private static int sleepThroughNight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.SLEEP_THROUGH_NIGHT,
                "night",
                1,
                "Command /staywithme sleep",
                "Sleeping through the night with PlayerEngine."
        );
    }

    private static int getOutOfWater(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.GET_OUT_OF_WATER,
                "dry_land",
                0,
                "Command /staywithme outofwater",
                "Getting out of water with PlayerEngine."
        );
    }

    private static int escapeLava(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.ESCAPE_LAVA,
                "lava_escape",
                0,
                "Command /staywithme escapelava",
                "Escaping lava with PlayerEngine."
        );
    }

    private static int clearLiquid(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        String target = x + "," + y + "," + z;
        return startHighLevelTask(
                context,
                FriendTaskType.CLEAR_LIQUID,
                target,
                1,
                "Command /staywithme clearliquid",
                "Clearing liquid at " + target + " with PlayerEngine-first execution."
        );
    }

    private static int putOutFire(CommandContext<CommandSourceStack> context, int range) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.PUT_OUT_FIRE,
                "fire",
                range,
                "Command /staywithme putoutfire",
                "Putting out nearby fire with PlayerEngine-first execution."
        );
    }

    private static int equipArmor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String rawTarget = StringArgumentType.getString(context, "target");
        Optional<String> target = normalizeHighLevelWordTarget(rawTarget);
        if (target.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Invalid armor target: " + rawTarget));
            return 0;
        }
        return startHighLevelTask(
                context,
                FriendTaskType.EQUIP_ARMOR,
                target.get(),
                1,
                "Command /staywithme equiparmor",
                "Equipping armor target " + target.get() + "."
        );
    }

    private static int protectPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.PROTECT_PLAYER,
                "player",
                0,
                "Command /staywithme protect",
                "Protecting the nearby area with PlayerEngine until stopped."
        );
    }

    private static int retreatFromHostiles(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.RETREAT_FROM_HOSTILES,
                "hostiles",
                distance,
                "Command /staywithme retreat",
                "Retreating from nearby hostile mobs with PlayerEngine."
        );
    }

    private static int retreatFromCreepers(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.RETREAT_FROM_CREEPERS,
                "creepers",
                distance,
                "Command /staywithme creeperretreat",
                "Retreating from nearby creepers with PlayerEngine-first execution."
        );
    }

    private static int dodgeProjectiles(CommandContext<CommandSourceStack> context, int distance) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.DODGE_PROJECTILES,
                "projectiles",
                distance,
                "Command /staywithme dodge",
                "Dodging incoming projectiles with PlayerEngine."
        );
    }

    private static int projectileProtectionWall(CommandContext<CommandSourceStack> context, int range) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.PROJECTILE_PROTECTION_WALL,
                "skeleton_projectiles",
                range,
                "Command /staywithme projectilewall",
                "Building a projectile protection wall with PlayerEngine."
        );
    }

    private static int attackNearbyHostile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.ATTACK_NEARBY_HOSTILE,
                "nearby_hostile",
                1,
                "Command /staywithme attack",
                "Attacking one nearby hostile mob."
        );
    }

    private static int craftItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        return startGenericItemTask(
                context,
                "item",
                amount,
                FriendTaskType.CRAFT_ITEM,
                "Command /staywithme craft ",
                "Crafting "
        );
    }

    private static int getItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        return startGenericCatalogueTask(context, "item", amount, "Command /staywithme get ", "Getting ");
    }

    private static int pickupDroppedItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawItem = StringArgumentType.getString(context, "item");
        Optional<String> target = normalizeCatalogueCommandTarget(rawItem);
        if (target.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Invalid item or PlayerEngine catalogue name: " + rawItem));
            return 0;
        }
        if (isFoodCommandTarget(target.get()) || isMeatCommandTarget(target.get()) || isFuelCommandTarget(target.get())) {
            context.getSource().sendFailure(Component.literal("Pickup needs a concrete dropped item such as bread, cooked_beef, coal, cobblestone, or torch."));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                FriendTaskType.PICKUP_DROPPED_ITEM,
                player.getUUID(),
                player.getGameProfile().getName(),
                target.get(),
                amount,
                null,
                "Command /staywithme pickup " + target.get()
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(
                () -> Component.literal("Picking up dropped " + target.get() + " x" + amount + " with PlayerEngine."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int giveItem(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawItem = StringArgumentType.getString(context, "item");
        Optional<String> target = normalizeCatalogueCommandTarget(rawItem);
        if (target.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Invalid item or PlayerEngine catalogue name: " + rawItem));
            return 0;
        }
        if (isFoodCommandTarget(target.get()) || isMeatCommandTarget(target.get()) || isFuelCommandTarget(target.get())) {
            context.getSource().sendFailure(Component.literal("Give needs a concrete item such as bread, cooked_beef, coal, or torch. Use /staywithme food, /staywithme meat, or /staywithme fuel for broad collection."));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                FriendTaskType.GIVE_ITEM,
                player.getUUID(),
                player.getGameProfile().getName(),
                target.get(),
                amount,
                null,
                "Command /staywithme give " + target.get()
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(
                () -> Component.literal("Giving " + target.get() + " x" + amount + " to " + player.getGameProfile().getName() + " with PlayerEngine."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int depositInventory(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return startHighLevelTask(
                context,
                FriendTaskType.DEPOSIT_INVENTORY,
                "inventory",
                0,
                "Command /staywithme deposit",
                "Depositing non-tool inventory into a nearby or newly placed container with PlayerEngine."
        );
    }

    private static int startHighLevelTask(
            CommandContext<CommandSourceStack> context,
            FriendTaskType type,
            String target,
            int amount,
            String reason,
            String feedback
    ) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTask task = new FriendTask(
                type,
                player.getUUID(),
                player.getGameProfile().getName(),
                target,
                amount,
                null,
                reason
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.literal(feedback), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int startGenericItemTask(
            CommandContext<CommandSourceStack> context,
            String argumentName,
            int amount,
            FriendTaskType type,
            String reasonPrefix,
            String feedbackPrefix
    ) throws CommandSyntaxException {
        String rawItem = commandResourceId(context, argumentName);
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
                type,
                player.getUUID(),
                player.getGameProfile().getName(),
                itemId.toString(),
                amount,
                null,
                reasonPrefix + itemId
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.literal(feedbackPrefix + itemId + " x" + amount + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int startGenericCatalogueTask(
            CommandContext<CommandSourceStack> context,
            String argumentName,
            int amount,
            String reasonPrefix,
            String feedbackPrefix
    ) throws CommandSyntaxException {
        String rawItem = StringArgumentType.getString(context, argumentName);
        Optional<String> target = normalizeCatalogueCommandTarget(rawItem);
        if (target.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Invalid item or PlayerEngine catalogue name: " + rawItem));
            return 0;
        }

        ServerPlayer player = context.getSource().getPlayerOrException();
        Optional<FriendEntity> friend = findNearestFriend(player);
        if (friend.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("commands.staywithme.no_friend"));
            return 0;
        }

        FriendTaskType type;
        String taskTarget;
        if (isFoodCommandTarget(target.get())) {
            type = FriendTaskType.COLLECT_FOOD;
            taskTarget = "food";
        } else if (isMeatCommandTarget(target.get())) {
            type = FriendTaskType.COLLECT_MEAT;
            taskTarget = "meat";
        } else if (isFuelCommandTarget(target.get())) {
            type = FriendTaskType.COLLECT_FUEL;
            taskTarget = "fuel";
        } else {
            type = FriendTaskType.GET_ITEM;
            taskTarget = target.get();
        }
        FriendTask task = new FriendTask(
                type,
                player.getUUID(),
                player.getGameProfile().getName(),
                taskTarget,
                amount,
                null,
                reasonPrefix + taskTarget
        );
        friend.get().setOwner(player);
        friend.get().startTask(task);
        JsonMemoryStore.appendTask(player.getUUID(), player.getGameProfile().getName(), task.summary());
        context.getSource().sendSuccess(() -> Component.literal(feedbackPrefix + taskTarget + " x" + amount + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int mineResource(CommandContext<CommandSourceStack> context, int amount) throws CommandSyntaxException {
        String rawResource = commandResourceId(context, "resource");
        String normalized = MiningTargetRegistry.normalize(rawResource);
        Optional<MiningTargetRegistry.MiningTarget> miningTarget = MiningTargetRegistry.find(normalized);
        if (miningTarget.isEmpty()) {
            if (canPlayerEngineAcquire(rawResource)) {
                return startGenericItemTask(
                        context,
                        "resource",
                        amount,
                        FriendTaskType.GET_ITEM,
                        "Command /staywithme mine via PlayerEngine get ",
                        "Getting "
                );
            }
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

    private static boolean canPlayerEngineAcquire(String rawResource) {
        if (!IntegrationStatus.isPlayerEngineLoaded()) {
            return false;
        }
        try {
            return PlayerEngineCatalogueDiagnostics.canResolve(rawResource);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
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
                        + ", entity=" + entity.getClass().getSimpleName()
                        + ", controller=" + entity.getFriendBrain().getControllerName()
                        + ", compat={" + entity.getFriendBrain().getControllerStatus() + "}"
                        + ", inventory=" + entity.getInventorySummary()
                        + ", hunger={" + entity.getHungerProvider().summary() + "}"
                        + ", perception={" + entity.getPerception().refreshNow().summary() + "}"
                        + lastFailureSuffix(entity)
                        + ", task=" + entity.getTaskSummary()),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static String lastFailureSuffix(FriendEntity entity) {
        String message = entity.getFriendBrain().getLastFailureMessage();
        if (message == null || message.isBlank()) {
            return "";
        }
        String trimmed = message.length() > 180 ? message.substring(0, 180) + "..." : message;
        return ", lastFailure=\"" + trimmed.replace('"', '\'') + "\"";
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

    private static int capabilities(CommandContext<CommandSourceStack> context) {
        boolean playerEngineActive = IntegrationStatus.isPlayerEngineLoaded() && StayWithMeConfig.USE_PLAYERENGINE_CONTROLLER.get();
        context.getSource().sendSuccess(
                () -> Component.literal("High-level tasks: " + HighLevelTaskSurface.ENTRY_POINTS + "."),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal("PlayerEngine-first: "
                        + (playerEngineActive ? "enabled" : "disabled")
                        + " ("
                        + HighLevelTaskSurface.PLAYERENGINE_FIRST_SUMMARY
                        + ")."),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal("Forge fallback: vanilla recipes, mining registry, visible wood/resource collection, construction-route recovery, expedition memory/safety."),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int playerEngineCatalogue(CommandContext<CommandSourceStack> context, String query) {
        if (!IntegrationStatus.isPlayerEngineLoaded()) {
            context.getSource().sendFailure(Component.literal("PlayerEngine is not loaded."));
            return 0;
        }
        try {
            List<String> matches = PlayerEngineCatalogueDiagnostics.search(query, 20);
            String label = query == null || query.isBlank() ? "<all>" : query;
            context.getSource().sendSuccess(
                    () -> Component.literal("PlayerEngine catalogue query " + label + ": " + matches.size() + " shown"),
                    false
            );
            if (query != null && !query.isBlank()) {
                context.getSource().sendSuccess(
                        () -> Component.literal("Input resolution: " + PlayerEngineCatalogueDiagnostics.resolveSummary(query)),
                        false
                );
            }
            if (matches.isEmpty()) {
                if (query != null && !query.isBlank()) {
                    context.getSource().sendSuccess(
                            () -> Component.literal("Closest catalogue names: "
                                    + String.join(", ", PlayerEngineCatalogueDiagnostics.closestMatches(query, 8))),
                            false
                    );
                }
                return Command.SINGLE_SUCCESS;
            }
            context.getSource().sendSuccess(() -> Component.literal(String.join(", ", matches)), false);
            return Command.SINGLE_SUCCESS;
        } catch (RuntimeException | LinkageError error) {
            context.getSource().sendFailure(Component.literal("PlayerEngine catalogue unavailable: "
                    + error.getClass().getSimpleName()
                    + ": "
                    + error.getMessage()));
            return 0;
        }
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

    private static Optional<String> normalizeCatalogueCommandTarget(String rawItem) {
        if (rawItem == null || rawItem.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawItem.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("/") || normalized.contains("\\")) {
            return Optional.empty();
        }
        if (normalized.contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(normalized);
            return id == null ? Optional.empty() : Optional.of(id.toString());
        }
        normalized = normalized.replace('-', '_');
        ResourceLocation id = ResourceLocation.tryParse("minecraft:" + normalized);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static Optional<String> normalizeHighLevelWordTarget(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawTarget.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return Optional.empty();
        }
        if (normalized.contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(normalized);
            return id == null ? Optional.empty() : Optional.of(id.toString());
        }
        ResourceLocation id = ResourceLocation.tryParse("minecraft:" + normalized);
        return id == null ? Optional.empty() : Optional.of(normalized);
    }

    private static boolean isFoodCommandTarget(String target) {
        return "food".equals(target) || "foods".equals(target) || "minecraft:food".equals(target) || "minecraft:foods".equals(target);
    }

    private static boolean isMeatCommandTarget(String target) {
        return "meat".equals(target) || "meats".equals(target) || "minecraft:meat".equals(target) || "minecraft:meats".equals(target);
    }

    private static boolean isFuelCommandTarget(String target) {
        return "fuel".equals(target) || "fuels".equals(target) || "minecraft:fuel".equals(target) || "minecraft:fuels".equals(target);
    }

    private static Optional<String> normalizeSmeltCommandTarget(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        return switch (normalized) {
            case "iron", "raw_iron", "iron_ingot" -> Optional.of("iron_ingot");
            case "gold", "raw_gold", "gold_ingot" -> Optional.of("gold_ingot");
            case "copper", "raw_copper", "copper_ingot" -> Optional.of("copper_ingot");
            case "charcoal" -> Optional.of("charcoal");
            default -> Optional.empty();
        };
    }

    private static Optional<String> normalizePlaceBlockCommandTarget(String target) {
        if (target == null || target.isBlank()) {
            return Optional.empty();
        }
        String normalized = target.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalized.contains("/") || normalized.contains("\\")) {
            return Optional.empty();
        }
        if (normalized.startsWith("minecraft:")) {
            normalized = normalized.substring("minecraft:".length());
        }
        if (isThrowawayPlaceBlockTarget(normalized)) {
            return Optional.of("throwaway");
        }
        ResourceLocation id = ResourceLocation.tryParse(normalized.contains(":")
                ? normalized
                : "minecraft:" + normalized);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR || block.asItem() == Items.AIR) {
            return Optional.empty();
        }
        return Optional.of(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString());
    }

    private static boolean isThrowawayPlaceBlockTarget(String normalizedTarget) {
        return "throwaway".equals(normalizedTarget)
                || "throwaway_block".equals(normalizedTarget)
                || "route_block".equals(normalizedTarget)
                || "route_blocks".equals(normalizedTarget)
                || "bridge_block".equals(normalizedTarget)
                || "bridge_blocks".equals(normalizedTarget)
                || "scaffold".equals(normalizedTarget)
                || "scaffold_block".equals(normalizedTarget)
                || "building_materials".equals(normalizedTarget);
    }

    private static String commandResourceId(CommandContext<CommandSourceStack> context, String argumentName) {
        return ResourceLocationArgument.getId(context, argumentName).toString();
    }
}
