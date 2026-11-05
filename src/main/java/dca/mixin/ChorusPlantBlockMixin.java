package dca.mixin;

import dca.ChorusBonemeal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Makes the chorus <i>stem</i> bonemealable too, so the player can aim at the plant instead of
 * having to hit the flower exactly.
 *
 * <p>Same three methods as {@link ChorusFlowerBlockMixin}, both forwarding to
 * {@link ChorusBonemeal}: the clicked block (stem or flower) is only used as the starting point for
 * walking the connected chorus structure.
 */
@Mixin(ChorusPlantBlock.class)
public abstract class ChorusPlantBlockMixin implements BonemealableBlock {

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return ChorusBonemeal.canRipen(level, pos);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        ChorusBonemeal.ripenOne(level, pos, random);
    }
}
