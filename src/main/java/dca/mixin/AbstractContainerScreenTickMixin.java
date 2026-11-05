package dca.mixin;

import dca.client.VillagerTradeKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the client half of {@code villagerBulkTrade} a per-tick heartbeat.
 *
 * <p>{@code AbstractContainerScreen.containerTick()} runs once per client tick for every container
 * screen, which is exactly "while a GUI with slots is open" - cheaper and more precise than hooking
 * {@code Minecraft.tick()}, and it needs no Fabric API events. {@code MerchantScreen} does not
 * override it, so the guard below decides whether it is a trade screen.
 *
 * <p>Client-only: listed under {@code client} in {@code dca.mixins.json}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenTickMixin {

    private static final String CONTAINER_TICK = "containerTick()V";

    @Inject(method = CONTAINER_TICK, at = @At("HEAD"))
    private void dca$onContainerTick(CallbackInfo ci) {
        if ((Object) this instanceof MerchantScreen) {
            VillagerTradeKeys.onTradeScreenTick(Minecraft.getInstance());
        }
    }
}
