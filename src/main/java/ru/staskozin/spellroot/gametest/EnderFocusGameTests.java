package ru.staskozin.spellroot.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import ru.staskozin.spellroot.component.EnderFocusState;
import ru.staskozin.spellroot.config.SpellrootConfig;
import ru.staskozin.spellroot.gameplay.EnderFocusTargetResolver;
import ru.staskozin.spellroot.item.EnderFocusItem;
import ru.staskozin.spellroot.recipe.EnderFocusRechargeRecipe;
import ru.staskozin.spellroot.registry.ModDataComponents;
import ru.staskozin.spellroot.registry.ModItems;

final class EnderFocusGameTests {
    private static final double MAX_DISTANCE = 10.0;
    private static final int ASSIST = 2;
    private static final double TOLERANCE = 0.08;

    private EnderFocusGameTests() {
    }

    static void run(GameTestHelper helper) {
        ServerPlayer survival = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        testOpenAir(helper, survival);
        testDownwardBlink(helper, survival);
        testCeilingBlink(helper, survival);
        testSolidAndLowObstacles(helper, survival);
        testLedges(helper, survival);
        testPoseAndCollisionShapes(helper, survival);
        testDeepCornerCorrection(helper, survival);
        testFluids(helper, survival);
        testEntitiesAreIgnored(helper, survival);
        testStateAndRecharge(helper, survival);
        testVanillaCooldown(helper);
        helper.succeed();
    }

    private static void testOpenAir(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        EnderFocusTargetResolver.Result result = resolve(helper, player);
        helper.assertTrue(result.valid(), "Open-air blink must be valid");
        helper.assertTrue(close(result.feetPosition().x, 11.5), "Open-air blink must use the full range");
        helper.assertTrue(close(result.feetPosition().y, 3.0), "Open-air endpoint must be derived from eye height");
    }

    private static void testDownwardBlink(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 8.0, 1.5, 30.0F);
        EnderFocusTargetResolver.Result openAir = resolve(helper, player);
        helper.assertTrue(openAir.valid(), "A downward blink through open air must be valid");
        helper.assertTrue(close(openAir.feetPosition().y, 3.0),
                "A downward open-air blink must preserve the ray endpoint as the new eye position");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 8.0, 1.5, 45.0F);
        for (int x = 7; x <= 9; x++) {
            helper.setBlock(x, 2, 1, Blocks.STONE);
        }
        EnderFocusTargetResolver.Result surface = resolve(helper, player);
        helper.assertTrue(surface.valid(), "A downward blink onto a solid surface must be valid");
        helper.assertTrue(close(surface.feetPosition().y, 3.0), "Feet must snap to the hit surface when blinking down");

        clearCourse(helper);
        helper.setBlock(1, 2, 1, Blocks.STONE);
        for (int x = 7; x <= 10; x++) {
            helper.setBlock(x, 1, 1, Blocks.STONE);
        }
        placePlayer(helper, player, 1.5, 3.0, 1.5, 20.0F);
        EnderFocusTargetResolver.Result oneBlockDown = resolve(helper, player);
        helper.assertTrue(oneBlockDown.valid(), "A player standing on a block must be able to blink one block down");
        helper.assertTrue(close(oneBlockDown.feetPosition().y, 2.0),
                "A one-block downward blink must land on the lower surface");

        clearCourse(helper);
        helper.setBlock(1, 2, 1, Blocks.STONE);
        for (int x = 9; x <= 12; x++) {
            helper.setBlock(x, 1, 1, Blocks.STONE);
        }
        placePlayer(helper, player, 1.5, 3.0, 1.5, 10.0F);
        EnderFocusTargetResolver.Result hullHitsFloor = resolve(helper, player);
        helper.assertTrue(hullHitsFloor.valid(),
                "A target whose feet intersect a floor below the eye ray must be lifted onto that floor");
        helper.assertTrue(close(hullHitsFloor.feetPosition().y, 2.0),
                "The target outline must sit on the floor instead of penetrating it");
    }

    private static void testCeilingBlink(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        for (int x = 4; x <= 8; x++) {
            helper.setBlock(x, 7, 1, Blocks.STONE);
        }
        placePlayer(helper, player, 1.5, 3.0, 1.5, -30.0F);
        EnderFocusTargetResolver.Result ceiling = resolve(helper, player);
        helper.assertTrue(ceiling.valid(), "The underside of a ceiling must be a legal blink target");
        double expectedFeetY = 7.0 - player.getDimensions(player.getPose()).height();
        helper.assertTrue(close(ceiling.feetPosition().y, expectedFeetY),
                "The complete player box must fit immediately below the ceiling, got " + ceiling.feetPosition());
    }

    private static void testSolidAndLowObstacles(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        placeWall(helper, 8, Blocks.STONE);
        EnderFocusTargetResolver.Result wall = resolve(helper, player);
        helper.assertTrue(wall.valid(), "A solid wall must allow stopping in front of it");
        helper.assertTrue(wall.feetPosition().x < 6.0, "A solid wall must never be crossed");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        helper.setBlock(4, 3, 1, Blocks.STONE);
        EnderFocusTargetResolver.Result lowObstacle = resolve(helper, player);
        helper.assertTrue(lowObstacle.valid() && close(lowObstacle.feetPosition().x, 11.5),
                "An intermediate obstacle below the eye ray must not block a legal endpoint");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        for (int x = 3; x <= 11; x++) {
            helper.setBlock(x, 4, 1, Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
            helper.setBlock(x, 5, 1, Blocks.STONE);
            helper.setBlock(x, 6, 1, Blocks.STONE);
            helper.setBlock(x, 7, 1, Blocks.STONE);
        }
        EnderFocusTargetResolver.Result ceiling = resolve(helper, player);
        helper.assertTrue(ceiling.valid() && ceiling.feetPosition().x < 3.0,
                "A low ceiling may be approached but must never be crossed");
    }

    private static void testLedges(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 20.0F);
        for (int x = 5; x <= 8; x++) {
            helper.setBlock(x, 2, 1, Blocks.STONE);
        }
        EnderFocusTargetResolver.Result levelSurface = resolve(helper, player);
        helper.assertTrue(levelSurface.valid(), "A level top surface must be a valid target");
        helper.assertTrue(close(levelSurface.feetPosition().y, 3.0), "A zero-height ledge must preserve feet height");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 10.0F);
        for (int x = 5; x <= 8; x++) {
            helper.setBlock(x, 3, 1, Blocks.STONE);
        }
        EnderFocusTargetResolver.Result oneBlock = resolve(helper, player);
        helper.assertTrue(oneBlock.valid(), "A one-block ledge must be reachable");
        helper.assertTrue(close(oneBlock.feetPosition().y, 4.0), "A one-block ledge must snap feet to its top");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        placeWall(helper, 4, Blocks.STONE);
        EnderFocusTargetResolver.Result twoBlocks = resolve(helper, player);
        helper.assertTrue(twoBlocks.valid(), "A two-block ledge must be reachable with assist=2");
        helper.assertTrue(close(twoBlocks.feetPosition().y, 5.0),
                "Two-block assist must select the ledge top, got " + twoBlocks.feetPosition());

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        placeWall(helper, 7, Blocks.STONE);
        EnderFocusTargetResolver.Result tooHigh = resolve(helper, player);
        helper.assertTrue(tooHigh.valid(), "A tall wall must still allow stopping before it");
        helper.assertTrue(tooHigh.feetPosition().x < 6.0 && tooHigh.feetPosition().y < 5.0,
                "Ledge assist must not search more than two blocks above the hit block face");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, -30.0F);
        placeWall(helper, 8, Blocks.OAK_LOG);
        EnderFocusTargetResolver.Result highTargetedLedge = resolve(helper, player);
        helper.assertTrue(highTargetedLedge.valid(),
                "A ledge directly targeted high above the player must remain reachable");
        helper.assertTrue(close(highTargetedLedge.feetPosition().y, 9.0),
                "Targeting the upper side of a tall column must select its top, got "
                        + highTargetedLedge.feetPosition());
    }

    private static void testPoseAndCollisionShapes(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        for (int x = 3; x <= 11; x++) {
            helper.setBlock(x, 4, 1, Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
        }
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        player.setPose(Pose.CROUCHING);
        EnderFocusTargetResolver.Result crouching = resolve(helper, player);
        player.setPose(Pose.STANDING);
        EnderFocusTargetResolver.Result standing = resolve(helper, player);
        helper.assertTrue(crouching.valid(), "Crouching pose must fit below a 1.5-block ceiling");
        helper.assertTrue(crouching.feetPosition().x > standing.feetPosition().x + 4.0,
                "Resolver must use dimensions of the current pose");

        List<BlockState> shapes = List.of(
                Blocks.STONE_SLAB.defaultBlockState(),
                Blocks.OAK_STAIRS.defaultBlockState(),
                Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.OAK_TRAPDOOR.defaultBlockState(),
                Blocks.GLASS_PANE.defaultBlockState()
        );
        for (BlockState state : shapes) {
            assertShapeProducesLegalEndpoint(helper, player, state);
        }

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        helper.setBlock(4, 3, 1, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(4, 4, 1, Blocks.OAK_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        EnderFocusTargetResolver.Result door = resolve(helper, player);
        helper.assertTrue(!door.valid() || door.feetPosition().x < 5.0, "A closed door must not be crossed");

        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        helper.setBlock(4, 3, 1, Blocks.SCAFFOLDING);
        EnderFocusTargetResolver.Result scaffolding = resolve(helper, player);
        helper.assertTrue(scaffolding.valid() && close(scaffolding.feetPosition().x, 11.5),
                "Scaffolding must remain traversable from the side according to its vanilla collision shape");
    }

    private static void testDeepCornerCorrection(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        for (int y = 3; y <= 7; y++) {
            helper.setBlock(6, y, 1, Blocks.STONE);
            helper.setBlock(6, y, 2, Blocks.STONE);
            helper.setBlock(5, y, 2, Blocks.STONE);
        }
        placePlayer(helper, player, 1.5, 3.0, 1.9, 0.0F);
        EnderFocusTargetResolver.Result corner = resolve(helper, player);
        helper.assertTrue(corner.valid(), "A target at a deep three-block corner must be corrected to a legal box");
        helper.assertTrue(corner.feetPosition().x < 6.0
                        && corner.feetPosition().z > 1.6
                        && corner.feetPosition().z < 1.75,
                "The target must stand against both faces of the corner instead of penetrating either one, got "
                        + corner.feetPosition());

        clearCourse(helper);
        for (int x = 9; x <= 10; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(x, 3, z, Blocks.STONE);
            }
        }
        helper.setBlock(10, 4, 1, Blocks.STONE);
        helper.setBlock(9, 4, 2, Blocks.STONE);
        helper.setBlock(10, 4, 2, Blocks.STONE);
        placePlayer(helper, player, 1.3, 8.0, 1.7, 32.7F);
        EnderFocusTargetResolver.Result steppedCorner = resolve(helper, player);
        helper.assertTrue(steppedCorner.valid(),
                "An air-ray endpoint overlapping a stepped three-block corner must be corrected upward");
        helper.assertTrue(close(steppedCorner.feetPosition().y, 5.0),
                "The target must stand on the highest intersecting corner surface, got "
                        + steppedCorner.feetPosition());
    }

    private static void testFluids(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 5.0, 1.5, 35.0F);
        helper.setBlock(4, 4, 1, Blocks.WATER);
        EnderFocusTargetResolver.Result waterSurface = resolve(helper, player);
        helper.assertTrue(waterSurface.valid(), "A fluid surface seen from air must be targetable");
        helper.assertTrue(waterSurface.feetPosition().y > 4.8 && waterSurface.feetPosition().y <= 5.01,
                "Feet must be placed above the actual fluid surface, got " + waterSurface.feetPosition());

        clearCourse(helper);
        for (int x = 1; x <= 12; x++) {
            helper.setBlock(x, 3, 1, Blocks.WATER);
            helper.setBlock(x, 4, 1, Blocks.WATER);
        }
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        EnderFocusTargetResolver.Result fromInside = resolve(helper, player);
        helper.assertTrue(fromInside.valid(), "Fluid must be ignored when the camera starts inside it");
        helper.assertTrue(close(fromInside.feetPosition().x, 11.5), "Blink from inside fluid must retain full range");
        helper.assertTrue(close(fromInside.feetPosition().y, 3.0), "Endpoint may remain inside fluid");
    }

    private static void testEntitiesAreIgnored(GameTestHelper helper, ServerPlayer player) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        var cow = helper.spawn(EntityTypes.COW, new Vec3(11.5, 3.0, 1.5));
        EnderFocusTargetResolver.Result result = resolve(helper, player);
        helper.assertTrue(result.valid() && close(result.feetPosition().x, 11.5),
                "Entities must not affect either the path or endpoint");
        cow.discard();
    }

    private static void testStateAndRecharge(GameTestHelper helper, ServerPlayer survival) {
        EnderFocusState expanded = new EnderFocusState(5, 8).normalize(12);
        helper.assertTrue(expanded.charges() == 5 && expanded.maxCharges() == 12,
                "Increasing capacity must preserve the absolute charge count");
        EnderFocusState reduced = expanded.normalize(4);
        helper.assertTrue(reduced.charges() == 4 && reduced.maxCharges() == 4,
                "Reducing capacity must clamp charges from above");

        ItemStack legacyFocus = new ItemStack(ModItems.ENDER_FOCUS.get());
        legacyFocus.remove(ModDataComponents.ENDER_FOCUS_STATE.get());
        EnderFocusState legacyState = EnderFocusItem.normalizeState(legacyFocus);
        helper.assertTrue(legacyState.charges() == SpellrootConfig.maxCharges()
                        && legacyState.maxCharges() == SpellrootConfig.maxCharges(),
                "A legacy focus without state must initialize with the current full capacity");

        EnderFocusState initial = new EnderFocusState(3, 8);
        EnderFocusState survivalResult = EnderFocusItem.stateAfterSuccessfulBlink(initial, survival);
        helper.assertTrue(survivalResult.charges() == 2, "A survival blink must consume exactly one charge");
        EnderFocusState immediateSecondResult = EnderFocusItem.stateAfterSuccessfulBlink(survivalResult, survival);
        helper.assertTrue(immediateSecondResult.charges() == 1,
                "A second blink must be available immediately and consume exactly one more charge");

        ServerPlayer creative = (ServerPlayer) helper.makeMockServerPlayer(GameType.CREATIVE);
        EnderFocusState creativeResult = EnderFocusItem.stateAfterSuccessfulBlink(initial, creative);
        int expectedCreativeCharges = SpellrootConfig.creativeConsumesCharges() ? 2 : 3;
        helper.assertTrue(creativeResult.charges() == expectedCreativeCharges,
                "Creative charge consumption must follow the synchronized server config");

        ItemStack first = new ItemStack(ModItems.ENDER_FOCUS.get());
        first.set(ModDataComponents.ENDER_FOCUS_STATE.get(), survivalResult);

        Component customName = Component.literal("Test focus");
        first.set(DataComponents.CUSTOM_NAME, customName);
        first.set(ModDataComponents.ENDER_FOCUS_STATE.get(), new EnderFocusState(2, 8));
        EnderFocusRechargeRecipe recipe = new EnderFocusRechargeRecipe();
        CraftingInput input = CraftingInput.of(2, 1, List.of(first, new ItemStack(Items.ENDER_PEARL)));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "One incomplete focus plus one pearl must match");
        ItemStack output = recipe.assemble(input);
        EnderFocusState outputState = EnderFocusItem.getState(output);
        helper.assertTrue(outputState.charges() == SpellrootConfig.maxCharges()
                        && outputState.maxCharges() == SpellrootConfig.maxCharges(),
                "Recharge recipe must normalize capacity and fully restore charges");
        helper.assertTrue(customName.equals(output.get(DataComponents.CUSTOM_NAME)),
                "Recharge must preserve custom components from the original stack");
        output.getItem().onCraftedPostProcess(output, helper.getLevel());

        ItemStack full = first.copy();
        full.set(ModDataComponents.ENDER_FOCUS_STATE.get(), EnderFocusState.full(SpellrootConfig.maxCharges()));
        CraftingInput fullInput = CraftingInput.of(2, 1, List.of(full, new ItemStack(Items.ENDER_PEARL)));
        helper.assertFalse(recipe.matches(fullInput, helper.getLevel()), "A full focus must not match the recharge recipe");
        CraftingInput extraInput = CraftingInput.of(3, 1,
                List.of(first, new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.AMETHYST_SHARD)));
        helper.assertFalse(recipe.matches(extraInput, helper.getLevel()), "Recharge recipe must reject extra ingredients");
    }

    private static void testVanillaCooldown(GameTestHelper helper) {
        clearCourse(helper);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);

        ItemStack focus = new ItemStack(ModItems.ENDER_FOCUS.get());
        focus.set(ModDataComponents.ENDER_FOCUS_STATE.get(), new EnderFocusState(3, 8));
        player.setItemInHand(InteractionHand.MAIN_HAND, focus);
        var startResult = player.gameMode.useItem(player, helper.getLevel(), focus, InteractionHand.MAIN_HAND);
        helper.assertTrue(startResult.consumesAction() && player.isUsingItem(),
                "A ready focus must enter aiming through the server game mode");
        helper.assertFalse(player.getCooldowns().isOnCooldown(focus),
                "Starting or cancelling aim must not start an Ender Focus cooldown");
        player.stopUsingItem();

        int cooldownTicks = SpellrootConfig.cooldownTicks();
        ItemCooldowns playerCooldowns = new ItemCooldowns();
        EnderFocusItem.applyCooldownAfterSuccessfulBlink(playerCooldowns, focus);
        helper.assertTrue(cooldownTicks == 0 || playerCooldowns.isOnCooldown(focus),
                "A successful blink must start the configured vanilla cooldown");

        ItemStack secondFocus = new ItemStack(ModItems.ENDER_FOCUS.get());
        helper.assertTrue(cooldownTicks == 0 || playerCooldowns.isOnCooldown(secondFocus),
                "All Ender Focus stacks owned by one player must share the vanilla cooldown group");
        helper.assertFalse(playerCooldowns.isOnCooldown(new ItemStack(Items.ENDER_PEARL)),
                "The Ender Focus cooldown must not affect unrelated items");

        ItemCooldowns otherPlayerCooldowns = new ItemCooldowns();
        helper.assertFalse(otherPlayerCooldowns.isOnCooldown(secondFocus),
                "Ender Focus cooldowns must be isolated between players");

        if (cooldownTicks > 0) {
            for (int tick = 1; tick < cooldownTicks; tick++) {
                playerCooldowns.tick();
                helper.assertTrue(playerCooldowns.isOnCooldown(focus),
                        "The vanilla cooldown must remain active until its configured final tick");
            }
            playerCooldowns.tick();
            helper.assertFalse(playerCooldowns.isOnCooldown(focus),
                    "The vanilla cooldown must end after exactly the configured number of ticks");
        }
    }

    private static void assertShapeProducesLegalEndpoint(GameTestHelper helper, ServerPlayer player, BlockState state) {
        clearCourse(helper);
        placePlayer(helper, player, 1.5, 3.0, 1.5, 0.0F);
        helper.setBlock(new BlockPos(4, 3, 1), state);
        EnderFocusTargetResolver.Result result = resolve(helper, player);
        helper.assertTrue(result.valid(), "Collision shape must produce a legal endpoint: " + state.getBlock());
    }

    private static EnderFocusTargetResolver.Result resolve(GameTestHelper helper, ServerPlayer player) {
        EnderFocusTargetResolver.Result result =
                EnderFocusTargetResolver.resolve(helper.getLevel(), player, MAX_DISTANCE, ASSIST);
        return new EnderFocusTargetResolver.Result(helper.relativeVec(result.feetPosition()), result.valid());
    }

    private static void placePlayer(
            GameTestHelper helper,
            ServerPlayer player,
            double x,
            double y,
            double z,
            float pitch
    ) {
        Vec3 position = helper.absoluteVec(new Vec3(x, y, z));
        player.setPose(Pose.STANDING);
        player.snapTo(position.x, position.y, position.z, -90.0F, pitch);
    }

    private static void placeWall(GameTestHelper helper, int maxY, Block block) {
        for (int y = 3; y <= maxY; y++) {
            helper.setBlock(6, y, 1, block);
        }
    }

    private static void clearCourse(GameTestHelper helper) {
        for (int x = 0; x <= 13; x++) {
            for (int y = 2; y <= 10; y++) {
                for (int z = 0; z <= 3; z++) {
                    helper.setBlock(x, y, z, Blocks.AIR);
                }
            }
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= TOLERANCE;
    }
}
