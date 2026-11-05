package dca.mixin;

import dca.client.VillagerTradeKeys;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.InputWithModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Arms the Ctrl+Alt+C action only after the player has actually clicked a trade row.
 *
 * <p>Vanilla always has a trade selected: {@code MerchantScreen.shopItem} is a plain {@code int}
 * field that defaults to 0, {@code init()} fires {@code postButtonClick()} for it, and the only
 * place it is ever written is the click handler of a trade row button. So without this, pressing the
 * key with nothing clicked would silently sell the first trade.
 *
 * <p>A trade row is a {@code MerchantScreen$TradeOfferButton}, which extends
 * {@code Button$Plain} and does <b>not</b> override {@code onPress} - so injecting into
 * {@link Button#onPress} at HEAD catches the click. {@code AbstractButton} calls that method both
 * for mouse clicks and for keyboard activation of a focused button.
 *
 * <p>The guard uses {@code getClass().getEnclosingClass()} instead of an {@code instanceof} because
 * {@code MerchantScreen$TradeOfferButton} is package-private and cannot be named from here. Every
 * button the trade screen adds is one of those nested classes, so this is exactly "a trade row was
 * pressed" and not, say, a button another mod injected into the screen.
 *
 * <p>Client-only: listed under {@code client} in {@code dca.mixins.json}.
 */
@Mixin(Button.class)
public abstract class TradeRowClickMixin {

    /** Full descriptor on purpose: 26.2 passes the input event to {@code onPress}. */
    private static final String ON_PRESS = "onPress(Lnet/minecraft/client/input/InputWithModifiers;)V";

    @Inject(method = ON_PRESS, at = @At("HEAD"))
    private void dca$onButtonPress(InputWithModifiers input, CallbackInfo ci) {
        if (((Object) this).getClass().getEnclosingClass() != MerchantScreen.class) {
            return;
        }
        VillagerTradeKeys.markTradeRowClicked();
    }
}
