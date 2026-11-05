package dca;

import carpet.CarpetSettings;
import dca.mixin.MerchantMenuInvoker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * The server-side half of the {@code villagerBulkTrade} rule: "take everything in the player's
 * inventory that can pay for the selected trade, trade it away and drop the products".
 *
 * <p>This class is deliberately silent: it shows the player nothing at all (no action bar text, no
 * chat). The only output is one log line, which exists purely so that a failure can be diagnosed
 * and so that the self test can check what happened.
 *
 * <p><b>Why this is short.</b> Vanilla 26.2 already contains both halves of the job; we only have to
 * press them in the right order (all of this is verified against the 26.2 bytecode):
 *
 * <ol>
 *   <li>{@link MerchantMenu#setSelectionHint(int)} + {@link MerchantMenu#tryMoveItems(int)} is the
 *       vanilla "auto-refill" action: it hands leftovers in payment slots 0/1 back to the player
 *       inventory and then pulls the required {@code ItemCost}s (splitting stacks as needed) out of
 *       inventory slots 3..38 until the payment slot is full. Selecting a trade in the GUI does
 *       exactly this, client- and server-side.</li>
 *   <li>{@code AbstractContainerMenu.doClick}'s {@code THROW} branch <i>is a loop</i>:
 *       {@code while (button == 1 && !taken.isEmpty() && ItemStack.isSameItem(slot.getItem(), taken))}
 *       it takes the whole result stack, drops it with {@link net.minecraft.world.entity.player.Player#drop},
 *       and repeats as long as the slot is refilled with the same item. For the merchant result slot
 *       the refill happens because taking the result consumes the payment and
 *       {@code MerchantContainer.updateSellItem()} therefore re-assembles the offer.</li>
 * </ol>
 *
 * <p>So one {@code THROW} click with button 1 on the result slot performs
 * {@code paymentStack / cost} trades and drops every product, until the payment slots can no longer
 * pay or the offer runs out of uses. We only wrap that in a refill loop.
 *
 * <p>Everything the player sees (uses/stock, demand-based price changes, villager XP, the items on
 * the ground) is therefore produced by vanilla code, not by us. Leftover payment stays in the
 * payment slots, exactly like manual trading; vanilla returns it to the inventory when the screen
 * closes ({@code MerchantMenu.removed}).
 */
public final class VillagerBulkTrade {

    /**
     * Button id we use on the vanilla {@code ServerboundContainerButtonClickPacket} channel.
     *
     * <p>{@code MerchantMenu} does not override {@code clickMenuButton}, so vanilla always answers
     * {@code false} for this menu and any id is unused. A dedicated high base keeps us out of the way
     * of real button menus (enchanting table, stonecutter, loom, ...), and the low 12 bits carry the
     * offer index. The packet's {@code buttonId} is a full VarInt, so this value survives the trip.
     */
    public static final int BUTTON_BASE = 0x7DCA000;
    /** Low bits of {@link #BUTTON_BASE} that carry the offer index. */
    public static final int BUTTON_INDEX_MASK = 0xFFF;

    /** Payment slots are 0 and 1, the trade result is 2 ({@code MerchantMenu.RESULT_SLOT}). */
    private static final int PAYMENT1_SLOT = 0;
    private static final int PAYMENT2_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    /**
     * Hard guard against a runaway loop. In practice the loop always ends much earlier, because a
     * vanilla offer is out of stock after {@code maxUses} (usually 12) trades.
     */
    private static final int MAX_CYCLES = 64;

    private VillagerBulkTrade() {
    }

    /** Is this button id one of ours? */
    public static boolean isTrigger(int buttonId) {
        return (buttonId & ~BUTTON_INDEX_MASK) == BUTTON_BASE;
    }

    /** Offer index carried by one of our button ids. Only valid when {@link #isTrigger} is true. */
    public static int indexOf(int buttonId) {
        return buttonId & BUTTON_INDEX_MASK;
    }

    /** Encodes an offer index into the button id the client sends. Used by the client half. */
    public static int buttonIdFor(int offerIndex) {
        return BUTTON_BASE | (offerIndex & BUTTON_INDEX_MASK);
    }

    /**
     * Trades the selected offer for as long as the player's inventory can pay for it, dropping every
     * product on the ground. Silently does nothing when there is nothing to do.
     */
    public static void run(MerchantMenu menu, ServerPlayer player, int offerIndex) {
        MerchantOffers offers = menu.getOffers();
        if (offerIndex < 0 || offerIndex >= offers.size()) {
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            // The vanilla THROW branch refuses to act while something is on the cursor.
            return;
        }

        MerchantOffer offer = offers.get(offerIndex);
        int startUses = offer.getUses();
        int cycles = 0;

        while (cycles++ < MAX_CYCLES) {
            // 1) Vanilla auto-refill: move the payment items from the inventory into slots 0/1.
            menu.setSelectionHint(offerIndex);
            menu.tryMoveItems(offerIndex);
            if (menu.slots.get(RESULT_SLOT).getItem().isEmpty()) {
                // tryMoveItems gives up when the payment leftovers cannot be handed back to the
                // inventory, which is always the case with a completely full inventory - the normal
                // bulk-trading situation (36 stacks of the payment item). Top the slots up directly
                // instead; see MerchantMenuInvoker.
                topUpPayments(menu, offer);
            }

            // 2) Nothing to trade: no payment items left, or the offer is out of stock.
            if (menu.slots.get(RESULT_SLOT).getItem().isEmpty()) {
                break;
            }

            // 3) One whole-stack throw on the result slot = repeatedly trade + drop, see class doc.
            int usesBefore = offer.getUses();
            menu.clicked(RESULT_SLOT, 1, ContainerInput.THROW, player);
            if (offer.getUses() == usesBefore) {
                break;   // no progress at all - stop instead of spinning
            }
        }

        int traded = offer.getUses() - startUses;

        if (traded > 0) {
            refreshTradeList(menu, player);
        }

        // Only diagnostics - the player is never shown anything.
        CarpetSettings.LOG.info("[DCA] bulk trade: player={} traded={} uses={}/{}",
                                player.getName().getString(), traded,
                                offer.getUses(), offer.getMaxUses());
    }

    /**
     * Tops the payment slots up straight from the player inventory, ignoring the leftovers already
     * sitting there. Needed because {@link MerchantMenu#tryMoveItems(int)} bails out when the
     * inventory is too full to take those leftovers back - see {@code dca.mixin.MerchantMenuInvoker}.
     *
     * <p>Safe to call unconditionally: the vanilla method skips inventory stacks that do not match
     * the payment slot's contents, and returns on its own as soon as the payment slot is full.
     */
    private static void topUpPayments(MerchantMenu menu, MerchantOffer offer) {
        MerchantMenuInvoker invoker = (MerchantMenuInvoker) menu;
        invoker.dca$moveFromInventoryToPaymentSlot(PAYMENT1_SLOT, offer.getItemCostA());
        offer.getItemCostB().ifPresent(cost ->
                invoker.dca$moveFromInventoryToPaymentSlot(PAYMENT2_SLOT, cost));
    }

    /**
     * Sends the trade list again so the open trade screen reflects what just happened.
     *
     * <p>Normally nobody has to do this: the client predicts every trade itself
     * ({@code ClientSideMerchant.notifyTrade} is just {@code offer.increaseUses()}), so its own copy
     * of the offers already shows the red "out of stock" cross. We trade straight on the server, so
     * the client's copy would keep claiming the trade is available until the screen is closed and
     * reopened. Re-sending {@link net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket}
     * (what {@link net.minecraft.server.level.ServerPlayer#sendMerchantOffers} does) replaces the
     * client's list, and {@code MerchantScreen} reads {@code menu.getOffers()} plus
     * {@code MerchantOffer.isOutOfStock()} every frame - so the cross and the experience bar are
     * correct on the next frame. This is also the only packet we send proactively; the slot contents
     * come back through the vanilla {@code broadcastChanges()} that our returning {@code true} from
     * {@code clickMenuButton} triggers.
     */
    private static void refreshTradeList(MerchantMenu menu, ServerPlayer player) {
        player.sendMerchantOffers(menu.containerId, menu.getOffers(), menu.getTraderLevel(),
                                  menu.getTraderXp(), menu.showProgressBar(), menu.canRestock());
    }
}
