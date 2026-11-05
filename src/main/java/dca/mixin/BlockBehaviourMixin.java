package dca.mixin;

import dca.DcaSettings;
import dca.OreBlink;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rule needs two hooks that {@code RedStoneOreBlock} itself does not declare, so they are taken
 * from its parent {@link BlockBehaviour}:
 *
 * <ul>
 *   <li>{@code tick(...)} - vanilla's scheduled block tick. {@code RedStoneOreBlock} does not
 *       override it, which is exactly why the blink timer has to live here. Every block's scheduled
 *       tick passes through, so the handler starts with an {@code instanceof} guard.</li>
 *   <li>{@code neighborChanged(...)} - fired when a block next to the ore changes, e.g. when the
 *       player puts the note block on top. That is what starts the very first blink instantly
 *       instead of waiting for a random tick.</li>
 * </ul>
 *
 * <p>Note the {@code (Object) this} cast: a mixin class is not related to the target class as far as
 * the Java compiler knows, so {@code this instanceof RedStoneOreBlock} would not compile.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {

    /** Full descriptors are used on purpose: they document the exact target and cannot silently
     *  match a different overload if Mojang adds one later. */
    private static final String TICK =
            "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V";
    private static final String NEIGHBOR_CHANGED =
            "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V";

    /**
     * The blink itself: flip {@code lit} and schedule the next flip. Cancelling is not strictly
     * needed ({@code BlockBehaviour.tick} is empty), but it keeps us from depending on that.
     */
    @Inject(method = TICK, at = @At("HEAD"), cancellable = true)
    private void dca$blinkOnScheduledTick(BlockState state, ServerLevel level, BlockPos pos,
                                         RandomSource random, CallbackInfo ci) {
        if (!((Object) this instanceof RedStoneOreBlock ore)) {
            return;                                  // scheduled tick of some other block
        }
        int note = OreBlink.noteAbove(level, pos);
        if (note <= 0 || !DcaSettings.redstoneOreNoteBlockBlink) {
            return;                                  // rule off, or no note block above: vanilla
        }
        boolean lit = state.getValue(RedStoneOreBlock.LIT);
        level.setBlock(pos, state.setValue(RedStoneOreBlock.LIT, !lit), Block.UPDATE_ALL);
        OreBlink.scheduleNext(level, pos, ore, random, note);
        ci.cancel();
    }

    /**
     * Kick-off: a neighbour change (for example the note block being placed above the ore) starts
     * the timer immediately, unless one is already pending.
     */
    @Inject(method = NEIGHBOR_CHANGED, at = @At("HEAD"))
    private void dca$startBlinkOnNeighborChange(BlockState state, Level level, BlockPos pos,
                                                Block neighborBlock, Orientation orientation,
                                                boolean movedByPiston, CallbackInfo ci) {
        if (!((Object) this instanceof RedStoneOreBlock ore)) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int note = OreBlink.noteAbove(level, pos);
        if (note <= 0 || !DcaSettings.redstoneOreNoteBlockBlink) {
            return;
        }
        if (OreBlink.blinkPending(serverLevel, pos, ore)) {
            return;                                  // a blink is already scheduled - don't restart it
        }
        OreBlink.scheduleNext(serverLevel, pos, ore, serverLevel.getRandom(), note);
    }
}
