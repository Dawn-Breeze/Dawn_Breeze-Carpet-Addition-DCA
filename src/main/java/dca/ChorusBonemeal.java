package dca;

import dca.mixin.ChorusFlowerBlockInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The logic behind the {@code chorusFlowerBonemeal} rule: bone meal on a chorus plant or chorus
 * flower ripens one random connected flower by giving it exactly one natural random tick.
 *
 * <p>Vanilla facts this builds on (all verified against the 26.2 bytecode):
 * <ul>
 *   <li>{@link ChorusFlowerBlock} does <b>not</b> implement {@code BonemealableBlock}, so bone meal
 *       is a no-op on chorus flowers;<br>
 *       {@code BoneMealItem.growCrop} only needs the block to implement that interface and to say
 *       yes in {@code isValidBonemealTarget} - the item consumption, the green particles
 *       ({@code levelEvent 1505}), the vibration and the success sound then all come for free.</li>
 *   <li>"Natural growth" is {@code ChorusFlowerBlock.randomTick}: one call either grows the flower
 *       one block upwards, spawns side branches, or turns it into a withered flower
 *       ({@code AGE = 5}). That is exactly what this rule hands out - once per bone meal.</li>
 * </ul>
 *
 * <p>Which flower gets the tick: the clicked block must be part of a chorus structure (plant or
 * flower); we walk the connected structure (all six directions, through both chorus blocks) and
 * collect the flowers that could actually do something - alive ({@code AGE < 5}) with room above,
 * which is precisely randomTick's own first guard. One of those is picked at random. If there is
 * none, bone meal is refused instead of being wasted.
 */
public final class ChorusBonemeal {

    /** Hard cap on how much of a connected chorus structure a single bone meal will look at. */
    private static final int MAX_VISITED = 512;

    private ChorusBonemeal() {
    }

    /** Can this block be ripened right now? Called for the clicked block, plant or flower alike. */
    public static boolean canRipen(LevelReader level, BlockPos clicked) {
        return DcaSettings.chorusFlowerBonemeal && !findTargets(level, clicked).isEmpty();
    }

    /** Gives one randomly chosen connected flower one natural random tick. */
    public static void ripenOne(ServerLevel level, BlockPos clicked, RandomSource random) {
        List<BlockPos> targets = findTargets(level, clicked);
        if (targets.isEmpty()) {
            return;
        }
        BlockPos flowerPos = targets.get(random.nextInt(targets.size()));
        BlockState flowerState = level.getBlockState(flowerPos);
        if (!(flowerState.getBlock() instanceof ChorusFlowerBlock)) {
            return;
        }
        // The vanilla natural growth step, on that very flower. It can grow upwards, branch
        // sideways, or wither the flower - exactly as a random tick would.
        ((ChorusFlowerBlockInvoker) flowerState.getBlock()).dca$randomTick(flowerState, level, flowerPos, random);
    }

    /**
     * The flowers of the connected chorus structure around {@code clicked} that a natural tick could
     * actually act on.
     */
    private static List<BlockPos> findTargets(LevelReader level, BlockPos clicked) {
        List<BlockPos> targets = new ArrayList<>();
        if (!isChorus(level.getBlockState(clicked))) {
            return targets;   // not a chorus block at all
        }

        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(clicked);
        queue.add(clicked);

        while (!queue.isEmpty() && seen.size() <= MAX_VISITED) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CHORUS_FLOWER) && canGrowNow(level, pos, state)) {
                targets.add(pos);
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.contains(next) || !isChorus(level.getBlockState(next))) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return targets;
    }

    private static boolean isChorus(BlockState state) {
        return state.is(Blocks.CHORUS_PLANT) || state.is(Blocks.CHORUS_FLOWER);
    }

    /** Mirrors the guard {@code ChorusFlowerBlock.randomTick} starts with, so no bone meal is wasted. */
    private static boolean canGrowNow(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(ChorusFlowerBlock.AGE) >= ChorusFlowerBlock.DEAD_AGE) {
            return false;   // withered flower: isRandomlyTicking is false for it as well
        }
        BlockPos above = pos.above();
        return above.getY() <= level.getMaxY() && level.isEmptyBlock(above);
    }
}
