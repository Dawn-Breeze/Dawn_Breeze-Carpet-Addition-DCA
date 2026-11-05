package dca.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@code MerchantMenu.moveFromInventoryToPaymentSlot(int, ItemCost)}, which is private.
 *
 * <p>We need it because {@code MerchantMenu.tryMoveItems(int)} (the vanilla "auto refill" used when a
 * trade is selected) starts by handing the leftovers in payment slots 0/1 back to the player
 * inventory and <b>returns immediately if that fails</b>:
 *
 * <pre>
 * ItemStack leftover = tradeContainer.getItem(0);
 * if (!leftover.isEmpty()) {
 *     if (!moveItemStackTo(leftover, 3, 39, true)) return;   // &lt;- gives up here
 *     tradeContainer.setItem(0, leftover);
 * }
 * </pre>
 *
 * <p>With a completely full inventory of the payment item - the normal bulk-trading situation - that
 * move can never succeed, so the payment slots are never topped up again and trading stops after the
 * first payment stack is spent. {@code moveFromInventoryToPaymentSlot} has no such problem: it only
 * ever moves items <i>into</i> the payment slot (merging into the existing same-item stack, and
 * returning as soon as that slot is full), and writing a payment slot runs
 * {@code MerchantContainer.updateSellItem()}, which re-fills the result slot immediately.
 *
 * <p>Server side (the class is common), so this lives in the {@code mixins} list.
 */
@Mixin(MerchantMenu.class)
public interface MerchantMenuInvoker {

    @Invoker("moveFromInventoryToPaymentSlot")
    void dca$moveFromInventoryToPaymentSlot(int paymentSlot, ItemCost cost);
}
