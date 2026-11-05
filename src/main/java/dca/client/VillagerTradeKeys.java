package dca.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dca.DcaSettings;
import dca.VillagerBulkTrade;
import dca.mixin.MerchantScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.world.inventory.MerchantMenu;

/**
 * Client half of the {@code villagerBulkTrade} rule: watches for {@code Ctrl+Alt+C} while the
 * villager trade screen is open and asks the server to do the work.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>The rule value is checked first.</b> Carpet syncs extension rules to clients
 *       ({@code carpet.network.ClientNetworkHandler} receives the "Rules" payload and calls
 *       {@code rule.set(...)} on the client's own manager, which carpet also registers on the client
 *       via {@code CarpetServer.onGameStarted()}). So {@link DcaSettings#villagerBulkTrade} is
 *       correct in single player (one JVM, one static field) and on a dedicated server (synced).
 *       While it is {@code false} this class never touches the keyboard state at all.</li>
 *   <li><b>A row has to be clicked first.</b> Vanilla preselects offer 0 (see
 *       {@code dca.mixin.TradeRowClickMixin}), so without arming the key, opening the screen and
 *       pressing Ctrl+Alt+C would silently trade the first offer. {@link #isArmed} only becomes true
 *       once the player has pressed a trade row button in <i>this</i> screen, and opening the screen
 *       again requires a fresh click.</li>
 *   <li>The client does <b>not</b> click anything itself. It only sends the vanilla button-click
 *       packet, so there is no client-side prediction and nothing can desync - the server trades and
 *       answers with real container updates.</li>
 *   <li>Keys are <i>polled</i> instead of read from a key event: that gives "press once = one burst,
 *       hold = a burst every {@value #REPEAT_TICKS} ticks" for free. 26.2 also removed
 *       {@code Screen.hasControlDown()}; {@link InputConstants#isKeyDown} is the supported way to
 *       test a modifier.</li>
 *   <li>Ctrl+Alt+C is intentionally shared with ItemScroller's mass-craft hotkey: that one only
 *       works in screens with a crafting result slot, this one only in {@link MerchantScreen}, so
 *       the two never see the same screen.</li>
 *   <li>Nothing is ever shown to the player, here or on the server.</li>
 * </ul>
 */
public final class VillagerTradeKeys {

    /** Ticks between two bursts while the key combination is held down. */
    private static final int REPEAT_TICKS = 4;

    private static int cooldown;

    /**
     * The trade screen in which the player last clicked a trade row. Nothing is armed while this is
     * not the screen that is currently open, which also gives "reopening needs a new click" for free.
     */
    private static MerchantScreen armedScreen;

    private VillagerTradeKeys() {
    }

    /** Called by {@code dca.mixin.TradeRowClickMixin} when a trade row button is pressed. */
    public static void markTradeRowClicked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui.screen() instanceof MerchantScreen screen) {
            armedScreen = screen;
        }
    }

    /** Has the player clicked a trade row in the trade screen that is open right now? */
    public static boolean isArmed(Minecraft mc) {
        return mc != null && mc.gui.screen() instanceof MerchantScreen screen && screen == armedScreen;
    }

    /** Called every client tick while a container screen is open (see the tick mixin). */
    public static void onTradeScreenTick(Minecraft mc) {
        if (cooldown > 0) {
            cooldown--;
        }
        // Rule off -> do not look at the keyboard at all.
        if (!DcaSettings.villagerBulkTrade) {
            return;
        }
        if (mc == null || mc.player == null || mc.getConnection() == null) {
            return;
        }
        if (!(mc.gui.screen() instanceof MerchantScreen screen)) {
            armedScreen = null;   // screen gone: the next one needs its own click
            return;
        }
        // Vanilla preselects the first trade, so require an explicit click on a row first.
        if (screen != armedScreen) {
            return;
        }
        if (!isComboDown(mc)) {
            cooldown = 0;   // released: the next press acts immediately
            return;
        }
        if (cooldown > 0) {
            return;
        }
        cooldown = REPEAT_TICKS;

        MerchantMenu menu = screen.getMenu();
        int offerIndex = ((MerchantScreenAccessor) screen).dca$shopItem();
        if (offerIndex < 0 || offerIndex >= menu.getOffers().size()) {
            return;
        }
        mc.getConnection().send(new ServerboundContainerButtonClickPacket(
                menu.containerId, VillagerBulkTrade.buttonIdFor(offerIndex)));
    }

    private static boolean isComboDown(Minecraft mc) {
        Window window = mc.getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_C)
                && (InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL))
                && (InputConstants.isKeyDown(window, InputConstants.KEY_LALT)
                    || InputConstants.isKeyDown(window, InputConstants.KEY_RALT));
    }
}
