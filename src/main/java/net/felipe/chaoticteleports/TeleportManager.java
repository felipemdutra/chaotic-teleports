package net.felipe.chaoticteleports;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;

import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings({"FieldCanBeLocal", "LoggingSimilarMessage"})
public class TeleportManager {
    private static final Random random = new Random();

    private static final double TICK = 0.05;

    // 150 blocks in any direction.
    private static final int MAX_TP_RADIUS = 150;

    private static final int MAX_TP_ATTEMPTS = 20;

    // When someone gets teleported, this is the min amount of ticks until they can be teleported again.
    // This is equal to 30 seconds.
    private final double MIN_TICKS_UNTIL_TP = TICK * 600.0;

    // This is equal to 5 minutes.
    private final double MAX_TICKS_UNTIL_TP = TICK * 6000.0;

    // Key: Player UUID, Value: Player's current tick count.
    // This is used to keep track of which player can be teleported.
    private final Map<UUID, Double> playerTickMap = new HashMap<>(5);

    public void initialize() {
        ChaoticTeleports.LOGGER.info("TeleportManager initialized!");

        ServerTickEvents.START_WORLD_TICK.register(serverWorld -> {
            List<ServerPlayerEntity> players = serverWorld.getPlayers();
            for (ServerPlayerEntity player : players) {
                handlePlayerTick(serverWorld, player);
            }
        });
    }

    private void handlePlayerTick(ServerWorld world, @NotNull ServerPlayerEntity player) {
        boolean teleported = false;
        UUID playerUuid = player.getUuid();

        if (!playerTickMap.containsKey(playerUuid)) {
            // If player is new, start their tick count.
            playerTickMap.put(playerUuid, 0.0);
            return;
        }

        double playerTickCount = playerTickMap.get(playerUuid) + TICK;

        if (playerTickCount < MIN_TICKS_UNTIL_TP) {
            // Player shouldn't be teleported yet.
            playerTickMap.replace(playerUuid, playerTickCount);
            return;
        }

        if (playerTickCount > MAX_TICKS_UNTIL_TP) {
            // Force-teleport the player.
            if (!tryRandomTp(world, player)) {
                ChaoticTeleports.LOGGER.info("Failed to teleport player!");
            } else {
                ChaoticTeleports.LOGGER.info("Teleported player successfully!");
                teleported = true;
            }
        }

        // Adds a 0.3% chance of the player being teleported every tick.
        if (random.nextFloat() < 0.003f) {
            if (!tryRandomTp(world, player)) {
                ChaoticTeleports.LOGGER.info("Failed to teleport player!");
            } else {
                ChaoticTeleports.LOGGER.info("Teleported player successfully!");
                teleported = true;
            }
        }

        // If the player has been teleported, reset their counter.
        if (teleported) {
            playerTickMap.put(playerUuid, 0.0);
        } else {
            playerTickMap.put(playerUuid, playerTickCount);
        }
    }

    private static boolean tryRandomTp(ServerWorld world, ServerPlayerEntity player) {
        BlockPos playerPos = player.getBlockPos();

        for (int i = 0; i < MAX_TP_ATTEMPTS; ++i) {
            int bound = MAX_TP_RADIUS * 2 - 1;

            int localX = random.nextInt(bound) - MAX_TP_RADIUS;
            int localZ = random.nextInt(bound) - MAX_TP_RADIUS;

            int targetX = localX + playerPos.getX();
            int targetZ = localZ + playerPos.getZ();

            int targetY;

            if (world.getRegistryKey() == World.NETHER) {
                Optional<Integer> optional = findSafeNetherYPos(world, targetX, targetZ);
                if (optional.isEmpty()) {
                    continue;
                } else {
                    targetY = optional.get();
                }
            } else {
                targetY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, targetX, targetZ);
            }

            BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);

            if (isSafeTpPos(world, targetPos)) {
                player.requestTeleport(targetX + 0.5, targetY + 1, targetZ + 0.5);
                return true;
            }
        }

        return false;
    }

    private static boolean isSafeTpPos(ServerWorld world, BlockPos pos) {
        BlockState blockAtFeet = world.getBlockState(pos);
        BlockState blockAtHead = world.getBlockState(pos.up());

        boolean feetSafe = !blockAtFeet.isSolidBlock(world, pos);
        boolean headSafe = !blockAtHead.isSolidBlock(world, pos.up());

        return feetSafe && headSafe;
    }

    private static Optional<Integer> findSafeNetherYPos(ServerWorld world, int x, int z) {
        // Store all safe Y coordinates.
        List<Integer> safeYs = new ArrayList<>(3);

        for (int y = 120; y > 10; --y) {
            BlockPos currPos = new BlockPos(x, y, z);

            BlockState below = world.getBlockState(currPos.down());
            BlockState feet = world.getBlockState(currPos);
            BlockState head = world.getBlockState(currPos.up());

            if (below.isSolidBlock(world, currPos.down()) && feet.isAir() && head.isAir()) {
                safeYs.add(y);
            }
        }

        // Randomly choose one of the safe Y coordinates.
        if (safeYs.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(safeYs.get(random.nextInt(safeYs.size())));
        }
    }
}
