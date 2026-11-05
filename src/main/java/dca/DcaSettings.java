package dca;

import carpet.api.settings.Rule;

import static carpet.api.settings.RuleCategory.FEATURE;

/**
 * All DCA rules live here as {@code public static} fields annotated with {@link Rule}.
 *
 * <p>Rules about this file:
 * <ul>
 *   <li>The field type may be {@code boolean}, {@code int}, {@code long}, {@code double},
 *       {@code float}, {@code String} or an {@code enum}. Booleans and enums infer their own
 *       allowed values, everything else needs {@code options = {...}}.</li>
 *   <li>The field's initial value IS the rule's default value. Defaults are what the game uses
 *       until a world's {@code dca.conf} overrides them, so <b>default off</b> simply means
 *       {@code = false}.</li>
 *   <li>{@code categories} must not be empty. Carpet's own categories
 *       ({@code FEATURE}, {@code CREATIVE}, {@code EXPERIMENTAL}, {@code BUGFIX},
 *       {@code OPTIMIZATION}, {@code SURVIVAL}, {@code COMMAND}, ...) are available through the
 *       static import; adding your own string (here {@link #DCA}) gives the rules their own tag.</li>
 *   <li>Read a rule from game code by reading the field, e.g.
 *       {@code if (DcaSettings.redstoneOreNoteBlockBlink) { ... }}</li>
 * </ul>
 *
 * <p>To add a new rule: copy one of the blocks below, give it a unique name, then add a matching
 * description to {@code assets/dca/lang/en_us.json} and {@code zh_cn.json} using the keys
 * {@code dca.rule.<name>.name} and {@code dca.rule.<name>.desc}. That is all - no registration
 * list to update.
 */
public class DcaSettings {
    /** Own category tag, shown as {@code dca.category.dca}. */
    public static final String DCA = "dca";

    /**
     * Redstone ore with a note block directly above it blinks on/off.
     *
     * <p>Semantics (identical to the scarpet/js script this rule was ported from):
     * the ore's {@code lit} state is toggled every {@code note .. 5*note} ticks, where
     * {@code note} is the note block's note property. {@code note = 0} means "leave this ore
     * alone", and ores without a note block above them are never touched.
     */
    @Rule(categories = {FEATURE, DCA})
    public static boolean redstoneOreNoteBlockBlink = false;

    /**
     * Bulk trading in the villager trade screen: select a trade, then hold Ctrl+Alt+C to spend every
     * inventory item that can pay for that trade and drop the products on the ground.
     *
     * <p>The work is done by the server (see {@link VillagerBulkTrade} and
     * {@code dca.mixin.MerchantMenuButtonMixin}); the client only sends the key press. Because of
     * that the mod has to be installed on the client <b>and</b> on the server (single player counts
     * as both at once).
     *
     * <p>Two vanilla limits the player will notice:
     * <ul>
     *   <li>an offer is sold out after {@code maxUses} trades (usually 12), so a burst normally ends
     *       with "the villager needs to restock" rather than "out of items";</li>
     *   <li>{@code MerchantMenu.tryMoveItems} only looks at inventory slots 3..38, i.e. the main
     *       inventory and the hotbar - items in the off hand or in armour slots are not used.</li>
     * </ul>
     */
    @Rule(categories = {FEATURE, DCA})
    public static boolean villagerBulkTrade = false;

    /**
     * Bone meal ripens chorus flowers: clicking a chorus plant (stem) or flower with bone meal gives
     * one randomly chosen connected flower exactly one natural random tick.
     *
     * <p>"Natural" is meant literally - the rule calls vanilla's own
     * {@code ChorusFlowerBlock.randomTick}, so one use can grow that flower one block upwards, make
     * it branch sideways, or (when nothing fits) wither it into a dead flower. One bone meal ripens
     * one flower; use it again for more.
     *
     * <p>Flowers that could not do anything anyway - withered ones ({@code AGE = 5}), or ones with a
     * block right above them - are never chosen, so bone meal is only consumed when something can
     * actually happen. Vanilla behaviour is completely untouched while the rule is off.
     */
    @Rule(categories = {FEATURE, DCA})
    public static boolean chorusFlowerBonemeal = false;

    /**
     * Which sound categories are silenced while the game window does not have the focus (the player
     * alt-tabbed to a browser and so on):
     *
     * <ul>
     *   <li>{@link #OFF} - vanilla behaviour, nothing is touched;</li>
     *   <li>{@link #MUSIC} - the ambient background music only ({@code SoundSource.MUSIC});</li>
     *   <li>{@link #MUSIC_AND_RECORDS} - background music plus jukebox records
     *       ({@code SoundSource.RECORDS} is a separate vanilla category, so "music" alone does not
     *       cover a record that is playing);</li>
     *   <li>{@link #ALL} - every sound category.</li>
     * </ul>
     */
    public enum FocusSoundMode {
        OFF,
        MUSIC,
        MUSIC_AND_RECORDS,
        ALL
    }

    /**
     * Silences the chosen sound categories while the game window is unfocused.
     *
     * <p>"Silence" is a gain of zero applied through
     * {@code SoundEngine.updateCategoryVolume(source, 0)} - the very per-source gain vanilla
     * multiplies the volume sliders with ({@code calculateVolume} = base volume x slider x gain).
     * That means it is instant, needs no channel bookkeeping, cannot desync from the sound engine,
     * and the player's own volume settings are never written to: the gain is a separate multiplier,
     * and it is put back to normal when the window is focused again. It is deliberately not a pause -
     * playback continues silently, so a record does not jump back to its start.
     *
     * <p>This is a client-side rule. Carpet syncs it to the client like every other rule and the
     * client half ({@code dca.client.FocusSound}) reads it every client tick. It reads whatever value
     * the client currently has, so on a server without DCA it simply stays off.
     */
    @Rule(categories = {FEATURE, DCA})
    public static FocusSoundMode pauseSoundOnFocusLost = FocusSoundMode.OFF;

    /**
     * On every arrival in the End through an end portal, makes the 25 obsidian blocks of the arrival
     * platform emit one round of shape updates (a "PP"/post-placement update) in all six directions -
     * which is exactly the update an observer watches for (see {@link EndPlatformPulse}).
     *
     * <p>Fires for every entity, on every arrival, and regardless of whether the platform was actually
     * changed by vanilla's own reset (which happens just before this). Everything else about the
     * arrival - the platform reset itself and the {@code PORTAL} chunk ticket - is untouched.
     *
     * <p>Only the placement/direction-update path of {@code Level.setBlock} runs: the obsidian state is
     * re-set with {@code UPDATE_NEIGHBORS | UPDATE_CLIENTS}, the same flags vanilla's own platform
     * reset uses, so no block is replaced and no item is dropped.
     */
    @Rule(categories = {FEATURE, DCA})
    public static boolean endPlatformShapeUpdateOnEntry = false;

    /**
     * Which entities the {@code endPortalDestinationInEnd} rule applies to:
     *
     * <ul>
     *   <li>{@link #OFF} - vanilla behaviour;</li>
     *   <li>{@link #ALL} - every entity, the player included;</li>
     *   <li>{@link #NON_PLAYER} - every entity except {@code ServerPlayer};</li>
     *   <li>{@link #ITEM} - dropped items only ({@code ItemEntity}, not experience orbs).</li>
     * </ul>
     */
    public enum EndPortalEntryMode {
        OFF,
        ALL,
        NON_PLAYER,
        ITEM
    }

    /**
     * Makes an end portal block used <b>inside the End</b> send entities to the End, exactly the way
     * arriving from the Overworld does: the End spawn point, the platform reset, the same rotation and
     * relative flags, the same post-teleport hooks.
     *
     * <p>Which entities this affects is chosen by {@link #endPortalEntryMode}. With {@link
     * EndPortalEntryMode#ALL} the player is redirected too, which means the End can no longer be left
     * through that portal - hence the rule's warning. Vanilla splits its own Overworld branch by entity
     * type (players go to their personal respawn, everything else goes to the End), and this rule
     * deliberately does <b>not</b> copy that split: it sends whatever the mode selects to the End.
     */
    @Rule(categories = {FEATURE, DCA})
    public static EndPortalEntryMode endPortalDestinationInEnd = EndPortalEntryMode.OFF;
}
