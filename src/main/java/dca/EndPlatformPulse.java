package dca;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server half of the {@code endPlatformShapeUpdateOnEntry} rule: on every end-portal arrival, make the
 * 25 obsidian blocks of the arrival platform emit one round of shape updates.
 *
 * <h2>Where the platform is</h2>
 * The End platform is <b>fixed</b> - it does not depend on the entity. Its structure origin is the
 * placed-feature position {@code (100, 49, 0)} ({@code data/minecraft/worldgen/placed_feature/
 * end_platform.json}), and that origin sits <b>one block above</b> the middle of the obsidian part, so
 * the obsidian itself is the 5x1x5 slab
 *
 * <pre>x = 98..102, y = 48, z = -2..2</pre>
 *
 * with 4 blocks of cleared space above it ({@code y = 49..52}). That is the same geometry
 * {@code EndPortalBlock.getPortalDestination} hands to {@code EndPlatformFeature.createEndPlatform}
 * as {@code BlockPos.containing(Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT)).below()}, so both
 * paths agree. Because it is fixed, the coordinates below are hard-coded and the blocks are
 * <b>not</b> inspected first.
 *
 * <h2>Why not just call setBlock again</h2>
 * The obvious "replay vanilla's reset" - {@code level.setBlock(pos, OBSIDIAN, 3)} - <b>does nothing</b>
 * when the state is already obsidian: the shape update is emitted from the "block was placed" branch
 * of {@code Level.setBlock}, which only runs when the state actually changed. That was measured, not
 * assumed. So this class calls the shape update directly instead:
 *
 * <pre>state.updateNeighbourShapes(level, pos, flags);   // public; emits all 6 directions</pre>
 *
 * which is exactly the "PP"/post-placement update, and it fires whether or not the platform needed
 * resetting.
 *
 * <h2>What an observer sees</h2>
 * {@code updateNeighbourShapes} walks {@code BlockBehaviour.UPDATE_SHAPE_ORDER} and calls
 * {@code Level.neighborShapeChanged(dir, pos, neighbourPos, state, flags, recursion)} for each side,
 * which lands in {@code ObserverBlock.updateShape}: if the direction it was told about equals its
 * {@code FACING} and it is not already {@code POWERED}, it schedules its 2-tick pulse. So an observer
 * attached to the platform fires once per arrival.
 *
 * <h2>Flags</h2>
 * {@code 3 = UPDATE_NEIGHBORS | UPDATE_CLIENTS}, the same value vanilla's platform reset passes to
 * {@code setBlock}. It is deliberately <b>not</b> {@code 16} ({@code UPDATE_KNOWN_SHAPE}): that bit
 * suppresses shape propagation down the chain, but it must stay clear here so the neighbours really
 * receive their {@code updateShape} call - and the flag also travels on to the observer, where it only
 * limits further shape recursion.
 */
public final class EndPlatformPulse {

    /** Inclusive bounds of the obsidian slab, as listed in the class doc. */
    private static final int MIN_X = 98;
    private static final int MAX_X = 102;
    private static final int Y = 48;
    private static final int MIN_Z = -2;
    private static final int MAX_Z = 2;

    /** The same flags vanilla's platform reset uses for {@code setBlock}: neighbours + clients. */
    private static final int FLAGS = 3;

    private EndPlatformPulse() {
    }

    /**
     * Emits one round of shape updates on every obsidian block of the platform.
     *
     * <p>Called by {@code dca.mixin.EndPortalArrivalMixin} from
     * {@code Entity.placePortalTicket(BlockPos)} - vanilla's arrival hook, which runs for every entity
     * and after the platform has been rebuilt.
     */
    public static void emitOnArrival(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                emit(obsidian, level, pos.set(x, Y, z));
            }
        }
    }

    /** One block's shape update. */
    private static void emit(BlockState state, ServerLevel level, BlockPos pos) {
        state.updateNeighbourShapes(level, pos, FLAGS);
    }
}