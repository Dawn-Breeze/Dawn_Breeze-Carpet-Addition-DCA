package dca.mixin;

import dca.ChorusBonemeal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes a chorus flower bonemealable for the {@code chorusFlowerBonemeal} rule.
 *
 * <p>This follows the same pattern carpet-extra uses for its {@code betterBonemeal} rule (bone meal
 * on sugar cane / cactus / lily pads): the vanilla block is given the {@link BonemealableBlock}
 * interface, and everything else - consuming one bone meal, the green particles
 * ({@code levelEvent 1505}), the vibration, the interaction result - is handled by vanilla's own
 * {@code BoneMealItem} pipeline.
 *
 * <p><b>With the rule off, nothing changes:</b> {@code isValidBonemealTarget} returns false, so
 * {@code BoneMealItem.growCrop} behaves exactly as it does in vanilla (bone meal neither changes the
 * block nor gets consumed).
 *
 * <p>The three methods are interface implementations and therefore must be public - they are added
 * to {@code ChorusFlowerBlock} as new methods, which is what mixins do for interface members.
 */
@Mixin(ChorusFlowerBlock.class)
public abstract class ChorusFlowerBlockMixin implements BonemealableBlock {

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return ChorusBonemeal.canRipen(level, pos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;   // one bone meal always ripens something, no dice roll
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        ChorusBonemeal.ripenOne(level, pos, random);
    }
}
