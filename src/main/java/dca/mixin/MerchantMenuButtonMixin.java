package dca.mixin;

import carpet.CarpetSettings;
import dca.DcaSettings;
import dca.VillagerBulkTrade;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side trigger for the {@code villagerBulkTrade} rule.
 *
 * <p><b>Why {@code clickMenuButton}?</b> The client has to press a key inside the trade screen, so
 * something must travel from the client to the server. We deliberately do <i>not</i> register a
 * custom payload: the vanilla button-click packet
 * ({@code ServerboundContainerButtonClickPacket(containerId, buttonId)}) already is a "the client
 * pressed a GUI button" channel, its {@code buttonId} is a full VarInt, and
 * {@code ServerGamePacketListenerImpl.handleContainerButtonClick} validates the container id and
 * {@code stillValid(player)} for us before calling {@link AbstractContainerMenu#clickMenuButton}.
 * Two more properties make it a good fit:
 *
 * <ul>
 *   <li>{@code MerchantMenu} does not override {@code clickMenuButton}, so vanilla answers
 *       {@code false} for every id - a server without DCA ignores our packet completely;</li>
 *   <li>vanilla calls {@code broadcastChanges()} when {@code clickMenuButton} returns {@code true},
 *       which is exactly the container resync we need after trading behind the client's back.</li>
 * </ul>
 *
 * <p>The injection lives on {@link AbstractContainerMenu} rather than on {@code MerchantMenu},
 * because {@code MerchantMenu} does not declare {@code clickMenuButton} itself: targeting an
 * inherited method from a subclass mixin is ambiguous, so we inject into the declaring class and
 * guard with {@code instanceof}, the same pattern {@code BlockBehaviourMixin} uses.
 *
 * <p>Everything here is silent: with the rule off (or on a spectator) we simply do nothing, without
 * telling the player anything. The client does not even check its keys while the rule is off, see
 * {@code dca.client.VillagerTradeKeys}.
 */
@Mixin(AbstractContainerMenu.class)
public abstract class MerchantMenuButtonMixin {

    /** Full descriptor on purpose: it cannot silently match a different overload later. */
    private static final String CLICK_MENU_BUTTON =
            "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z";

    @Inject(method = CLICK_MENU_BUTTON, at = @At("HEAD"), cancellable = true)
    private void dca$onClickMenuButton(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        // Not our button id: leave vanilla behaviour (and every other menu) untouched.
        if (!VillagerBulkTrade.isTrigger(buttonId)) {
            return;
        }
        // Our id, but this is not the villager trade menu.
        if (!((Object) this instanceof MerchantMenu menu)) {
            return;
        }
        // ...or not the menu the player actually has open.
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.containerMenu != (Object) this) {
            return;
        }
        // Rule off (or spectator): consume the packet and do nothing.
        if (!DcaSettings.villagerBulkTrade || player.isSpectator()) {
            cir.setReturnValue(false);
            return;
        }

        // Claim the button *before* doing the work: vanilla broadcasts the container changes
        // afterwards, which is how the client learns about the trades we just performed.
        cir.setReturnValue(true);

        try {
            VillagerBulkTrade.run(menu, serverPlayer, VillagerBulkTrade.indexOf(buttonId));
        } catch (Throwable t) {
            // A rule must not be able to take the server down; report and carry on.
            CarpetSettings.LOG.error("[DCA] bulk trade failed", t);
        }
    }
}
