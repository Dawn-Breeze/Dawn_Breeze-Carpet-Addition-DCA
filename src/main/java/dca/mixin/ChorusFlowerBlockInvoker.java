package dca.mixin;

import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code ChorusFlowerBlock.randomTick(...)}, which is protected.
 *
 * <p>That method <i>is</i> the natural growth of a chorus flower: one call grows it one block up,
 * spawns side branches, or withers it. The rule calls it directly instead of reimplementing the
 * logic, which keeps the behaviour byte-for-byte identical to a real random tick and also keeps
 * third-party hooks on {@code randomTick} working (e.g. Serilum's {@code collective} fires its
 * {@code PRE_CROP_GROW} event there).
 *
 * <p>Blocks are registry singletons, so {@code ((ChorusFlowerBlockInvoker) Blocks.CHORUS_FLOWER)}
 * is the same instance the mixin was merged into.
 */
@Mixin(ChorusFlowerBlock.class)
public interface ChorusFlowerBlockInvoker {

    @Invoker("randomTick")
    void dca$randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random);
}
