package dca.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the offer index the player has selected in the trade screen.
 *
 * <p>{@code MerchantScreen.shopItem} is private and has no getter, but it is exactly "the trade row
 * that is highlighted / that vanilla last sent a {@code ServerboundSelectTradePacket} for"
 * ({@code MerchantScreen.postButtonClick()} uses it for {@code setSelectionHint} +
 * {@code tryMoveItems} + that packet). An accessor is all we need - we do not call
 * {@code postButtonClick()} ourselves, because the server performs the refill.
 *
 * <p>Client-only: listed under {@code client} in {@code dca.mixins.json}, so a dedicated server
 * never loads it.
 */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
    @Accessor("shopItem")
    int dca$shopItem();
}
