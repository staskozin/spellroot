package ru.staskozin.spellroot.gameplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EnderFocusTargetResolver {
    private static final double EPSILON = 1.0E-4;

    private EnderFocusTargetResolver() {
    }

    public static Result resolve(Level level, Player player, double maxDistance, int ledgeAssistBlocks) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 rayEnd = eyePosition.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(maxDistance));
        boolean startsInsideFluid = !level.getFluidState(BlockPos.containing(eyePosition)).isEmpty();
        ClipContext.Fluid fluidMode = startsInsideFluid ? ClipContext.Fluid.NONE : ClipContext.Fluid.ANY;
        BlockHitResult hit = level.clip(new ClipContext(
                eyePosition,
                rayEnd,
                ClipContext.Block.COLLIDER,
                fluidMode,
                player
        ));

        EntityDimensions dimensions = player.getDimensions(player.getPose());
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 aimPoint = hit.getType() == HitResult.Type.MISS ? rayEnd : hit.getLocation();
        Vec3 directTarget = directTarget(level, player, hit, aimPoint, dimensions, eyeHeight, startsInsideFluid);
        directTarget = correctEndpoint(level, player, hit, dimensions, directTarget);

        List<Vec3> validTargets = new ArrayList<>();
        if (isBoxUsable(level, player, dimensions.makeBoundingBox(directTarget))) {
            validTargets.add(directTarget);
        }

        if (hit.getType() != HitResult.Type.MISS && hit.getDirection().getAxis().isHorizontal()) {
            List<Vec3> ledgeTargets = ledgeTargets(level, player, hit, dimensions, ledgeAssistBlocks);
            List<Vec3> validLedgeTargets = new ArrayList<>();
            for (Vec3 ledgeTarget : ledgeTargets) {
                if (isBoxUsable(level, player, dimensions.makeBoundingBox(ledgeTarget))) {
                    validLedgeTargets.add(ledgeTarget);
                }
            }
            if (!validLedgeTargets.isEmpty()) {
                Vec3 bestLedgeTarget = validLedgeTargets.stream()
                        .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(aimPoint)))
                        .orElseThrow();
                return new Result(bestLedgeTarget, true);
            }
        }

        if (!validTargets.isEmpty()) {
            Vec3 bestTarget = validTargets.stream()
                    .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(aimPoint)))
                    .orElseThrow();
            return new Result(bestTarget, true);
        }

        return new Result(directTarget, false);
    }

    private static Vec3 correctEndpoint(
            Level level,
            Player player,
            BlockHitResult hit,
            EntityDimensions dimensions,
            Vec3 target
    ) {
        Vec3 corrected = target;
        if (hit.getType() != HitResult.Type.MISS && hit.getDirection().getAxis().isHorizontal()) {
            corrected = moveEndpointOutOfCorner(level, player, dimensions, corrected);
        }
        corrected = liftEndpointFromFloor(level, player, dimensions, corrected);
        return moveEndpointOutOfCorner(level, player, dimensions, corrected);
    }

    private static Vec3 directTarget(
            Level level,
            Player player,
            BlockHitResult hit,
            Vec3 aimPoint,
            EntityDimensions dimensions,
            double eyeHeight,
            boolean startsInsideFluid
    ) {
        if (hit.getType() == HitResult.Type.MISS) {
            return aimPoint.subtract(0.0, eyeHeight, 0.0);
        }

        BlockPos hitPos = hit.getBlockPos();
        FluidState fluid = level.getFluidState(hitPos);
        if (!startsInsideFluid && !fluid.isEmpty()) {
            double surfaceY = hitPos.getY() + fluid.getHeight(level, hitPos) + EPSILON;
            return new Vec3(aimPoint.x, surfaceY, aimPoint.z);
        }

        if (hit.getDirection() == Direction.UP) {
            BlockState state = level.getBlockState(hitPos);
            VoxelShape shape = state.getCollisionShape(level, hitPos, CollisionContext.of(player));
            if (!shape.isEmpty()) {
                return new Vec3(aimPoint.x, hitPos.getY() + shape.max(Direction.Axis.Y) + EPSILON, aimPoint.z);
            }
        }

        if (hit.getDirection() == Direction.DOWN) {
            return new Vec3(aimPoint.x, aimPoint.y - dimensions.height() - EPSILON, aimPoint.z);
        }

        Direction face = hit.getDirection();
        double horizontalOffset = dimensions.width() / 2.0 + EPSILON;
        double offset = face.getAxis().isHorizontal() ? horizontalOffset : EPSILON;
        return aimPoint.add(
                face.getStepX() * offset,
                face.getStepY() * offset - eyeHeight,
                face.getStepZ() * offset
        );
    }

    private static List<Vec3> ledgeTargets(
            Level level,
            Player player,
            BlockHitResult hit,
            EntityDimensions dimensions,
            int ledgeAssistBlocks
    ) {
        List<Vec3> targets = new ArrayList<>();
        Direction face = hit.getDirection();
        double inward = dimensions.width() / 2.0 + 0.05;
        double x = hit.getLocation().x - face.getStepX() * inward;
        double z = hit.getLocation().z - face.getStepZ() * inward;

        for (int offset = 0; offset <= ledgeAssistBlocks; offset++) {
            BlockPos pos = hit.getBlockPos().above(offset);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos, CollisionContext.of(player));
            if (!shape.isEmpty()) {
                double y = pos.getY() + shape.max(Direction.Axis.Y) + EPSILON;
                targets.add(new Vec3(x, y, z));
            }
        }

        return targets;
    }

    private static Vec3 liftEndpointFromFloor(
            Level level,
            Player player,
            EntityDimensions dimensions,
            Vec3 target
    ) {
        AABB targetBox = dimensions.makeBoundingBox(target);
        if (!areChunksLoaded(level, targetBox) || level.noBlockCollision(player, targetBox)) {
            return target;
        }

        double highestSurface = target.y;
        for (VoxelShape shape : level.getBlockCollisions(player, targetBox)) {
            for (AABB part : shape.toAabbs()) {
                highestSurface = Math.max(highestSurface, part.maxY);
            }
        }

        double lift = highestSurface - target.y;
        if (lift <= EPSILON) {
            return target;
        }

        Vec3 liftedTarget = new Vec3(target.x, highestSurface + EPSILON, target.z);
        return isBoxUsable(level, player, dimensions.makeBoundingBox(liftedTarget)) ? liftedTarget : target;
    }

    private static Vec3 moveEndpointOutOfCorner(
            Level level,
            Player player,
            EntityDimensions dimensions,
            Vec3 target
    ) {
        AABB targetBox = dimensions.makeBoundingBox(target);
        if (!areChunksLoaded(level, targetBox) || level.noBlockCollision(player, targetBox)) {
            return target;
        }

        double halfWidth = dimensions.width() / 2.0;
        double maxCorrection = dimensions.width() + 0.1;
        List<Double> xCandidates = new ArrayList<>();
        List<Double> zCandidates = new ArrayList<>();
        xCandidates.add(target.x);
        zCandidates.add(target.z);

        AABB searchBox = targetBox.inflate(maxCorrection, EPSILON, maxCorrection);
        for (VoxelShape shape : level.getBlockCollisions(player, searchBox)) {
            for (AABB part : shape.toAabbs()) {
                if (part.maxY <= targetBox.minY + EPSILON || part.minY >= targetBox.maxY - EPSILON) {
                    continue;
                }
                xCandidates.add(part.minX - halfWidth - EPSILON);
                xCandidates.add(part.maxX + halfWidth + EPSILON);
                zCandidates.add(part.minZ - halfWidth - EPSILON);
                zCandidates.add(part.maxZ + halfWidth + EPSILON);
            }
        }

        Vec3 best = target;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (double x : xCandidates) {
            for (double z : zCandidates) {
                double distance = Mth.square(x - target.x) + Mth.square(z - target.z);
                if (distance > Mth.square(maxCorrection) || distance >= bestDistance) {
                    continue;
                }
                Vec3 candidate = new Vec3(x, target.y, z);
                if (isBoxUsable(level, player, dimensions.makeBoundingBox(candidate))) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static boolean isBoxUsable(Level level, Player player, AABB box) {
        BlockPos min = BlockPos.containing(box.minX + EPSILON, box.minY + EPSILON, box.minZ + EPSILON);
        BlockPos max = BlockPos.containing(box.maxX - EPSILON, box.maxY - EPSILON, box.maxZ - EPSILON);
        return level.isInWorldBounds(min)
                && level.isInWorldBounds(max)
                && level.getWorldBorder().isWithinBounds(box)
                && areChunksLoaded(level, box)
                && level.noBlockCollision(player, box);
    }

    private static boolean areChunksLoaded(Level level, AABB box) {
        int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(box.minX));
        int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(box.maxX - EPSILON));
        int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(box.minZ));
        int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(box.maxZ - EPSILON));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    public record Result(Vec3 feetPosition, boolean valid) {
    }
}
