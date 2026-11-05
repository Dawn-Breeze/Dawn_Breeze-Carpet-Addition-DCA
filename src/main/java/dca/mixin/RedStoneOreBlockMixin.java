package dca.mixin;

import dca.DcaSettings;
import dca.OreBlink;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drives the {@code redstoneOreNoteBlockBlink} rule from the ore block's own tick hooks.
 *
 * <p>Vanilla 26.2 redstone ore works like this:
 * <ul>
 *   <li>{@code isRandomlyTicking(state)} returns the {@code LIT} property, i.e. only lit ore is
 *       randomly ticked;</li>
 *   <li>{@code randomTick(...)} switches {@code LIT} back off - that is the whole "glow then fade"
 *       cycle, which normally starts when a player walks on / mines the ore.</li>
 * </ul>
 *
 * <p>With the rule enabled we take that cycle over: we keep the ore randomly tickable (so an ore
 * that was never touched also gets picked up), and when a note block sits above it we schedule our
 * own ticks instead of letting vanilla switch it off immediately.
 */
@Mixin(RedStoneOreBlock.class)
public abstract class RedStoneOreBlockMixin {

    /** Full descriptors are used on purpose: they document the exact target and cannot silently
     *  match a different overload if Mojang adds one later. */
    private static final String IS_RANDOMLY_TICKING =
            "isRandomlyTicking(Lnet/minecraft/world/level/block/state/BlockState;)Z";
    private static final String RANDOM_TICK =
            "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V";

    /**
     * Vanilla only random-ticks lit ore. While the rule is on we claim to be randomly tickable
     * either way; the {@link #dca$onRandomTick} handler below does the actual note block check
     * (this method only receives the block state, so it cannot look at the world).
     */
    @Inject(method = IS_RANDOMLY_TICKING, at = @At("HEAD"), cancellable = true)
    private void dca$alwaysRandomlyTickable(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (DcaSettings.redstoneOreNoteBlockBlink) {
            cir.setReturnValue(true);
        }
    }

    /**
     * When the rule applies: start the blink timer (once) and cancel vanilla's "turn the ore off"
     * behaviour. When it does not apply, return without touching anything so vanilla keeps working.
     */
    @Inject(method = RANDOM_TICK, at = @At("HEAD"), cancellable = true)
    private void dca$onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!OreBlink.applies(level, pos)) {
            return;
        }
        if (!OreBlink.blinkPending(level, pos, state.getBlock())) {
            OreBlink.scheduleNext(level, pos, state.getBlock(), random, OreBlink.noteAbove(level, pos));
        }
        ci.cancel();
    }
}
