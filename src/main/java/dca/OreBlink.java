package dca;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared logic of the {@code redstoneOreNoteBlockBlink} rule. Kept separate from the mixins so the
 * behaviour can be read (and unit-tested later) without mixin noise.
 *
 * <p>All Minecraft names here are the official 26.2 names - there is no Yarn/Intermediary mapping
 * in 26.2, so anything you write has to match the real class/method names exactly.
 */
public final class OreBlink {
    private OreBlink() {}

    /** Upper end of the blink interval, as a multiple of the note value. */
    public static final int TICKS_PER_NOTE = 5;

    /**
     * @return the note property of the note block directly above {@code pos}, or {@code 0} when
     *         there is no note block there (0 is also the "do nothing" value for the rule).
     */
    public static int noteAbove(Level level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof NoteBlock) {
            return above.getValue(NoteBlock.NOTE);
        }
        return 0;
    }

    /**
     * Random wait in {@code [note, TICKS_PER_NOTE * note]} ticks, both ends inclusive.
     * {@code note} must be &gt;= 1.
     */
    public static int randomWait(RandomSource random, int note) {
        int max = TICKS_PER_NOTE * note;
        return note + random.nextInt(max - note + 1);
    }

    /** Schedules the next blink of that ore block. */
    public static void scheduleNext(ServerLevel level, BlockPos pos, Block block, RandomSource random, int note) {
        level.scheduleTick(pos, block, randomWait(random, note));
    }

    /** @return true when this position already has a blink pending, so we must not restart the timer. */
    public static boolean blinkPending(ServerLevel level, BlockPos pos, Block block) {
        return level.getBlockTicks().hasScheduledTick(pos, block);
    }

    /** @return true when the rule is on and this ore is actually controlled by a note block. */
    public static boolean applies(ServerLevel level, BlockPos pos) {
        return DcaSettings.redstoneOreNoteBlockBlink && noteAbove(level, pos) > 0;
    }
}
