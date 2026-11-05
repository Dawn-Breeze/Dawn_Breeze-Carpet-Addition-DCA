package dca.client;

import dca.DcaSettings;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

/**
 * Client half of the {@code pauseSoundOnFocusLost} rule: silences the configured sound categories
 * while the game window is unfocused, and gives them back when it is focused again.
 *
 * <p><b>How it silences.</b> A gain of zero through
 * {@code SoundEngine.updateCategoryVolume(source, 0)}, the same per-source multiplier vanilla
 * applies the volume sliders with ({@code calculateVolume} = base volume x slider x gain). That is
 * instant, also affects sounds that are already playing, and never writes to the player's own volume
 * settings - the options screen refreshes these gains from the sliders
 * ({@code refreshCategoryVolume}), which is exactly what should happen if the player moves a slider
 * while we are muted. It is deliberately <b>not</b> a pause: playback carries on silently, so a
 * record does not jump back to its start.
 *
 * <p><b>Why it is idempotent and checked every tick.</b> The vanilla engine drops gain writes while
 * it is not loaded yet ({@code updateCategoryVolume} stores the value but {@code refreshCategoryVolume}
 * returns early). A one-shot "mute on the focus transition" would therefore be lost silently if the
 * window lost focus during a resource reload, or before the engine had finished loading. So the tick
 * handler re-reads {@link #isSilenced()} every tick and only calls into the sound engine when the
 * result actually differs from what we want - cheap, and it self-heals.
 *
 * <p>Two details worth knowing: {@code SoundSource.MUSIC} is the ambient background music while a
 * jukebox record is {@code SoundSource.RECORDS}, a <b>separate</b> vanilla category (hence the
 * music-plus-records rule value); and the gain we overwrite is a per-session multiplier that starts
 * at {@code 1.0f}, which we still read before overwriting rather than hard-coding, so another mod
 * using the same multiplier is not clobbered.
 *
 * <p>All state lives here and is only touched from the client thread (the tick mixin). Nothing is
 * logged, and while the rule is off this class never even reads the window state.
 */
public final class FocusSound {

    /** The sources we have silenced right now. */
    private static final Set<SoundSource> MUTED = EnumSet.noneOf(SoundSource.class);

    /** The gain each silenced source had before we overwrote it, so it can be put back exactly. */
    private static final Object2FloatMap<SoundSource> PREVIOUS_GAIN = new Object2FloatOpenHashMap<>();

    private FocusSound() {
    }

    /** Called from {@code dca.mixin.MinecraftClientTickMixin} once per client tick. */
    public static void clientTick(Minecraft mc) {
        if (mc == null) {
            return;
        }
        applyFor(mc, mc.isWindowActive());
    }

    /**
     * The whole rule body, with the window state passed in. {@link #clientTick} calls this with the
     * real {@code isWindowActive()}; taking the focus state as a parameter gives the self test a seam
     * to drive the real apply/restore path without physically taking the window's focus away.
     *
     * @param focused whether the window currently has the focus
     */
    static void applyFor(Minecraft mc, boolean focused) {
        if (mc == null) {
            return;
        }
        DcaSettings.FocusSoundMode mode = DcaSettings.pauseSoundOnFocusLost;
        // Rule off, or window focused -> nothing may stay silenced.
        if (mode == DcaSettings.FocusSoundMode.OFF || focused) {
            silence(Set.of());
            return;
        }
        silence(sourcesFor(mode));
    }

    /** The sources a given rule value silences. */
    private static Set<SoundSource> sourcesFor(DcaSettings.FocusSoundMode mode) {
        return switch (mode) {
            case OFF -> Set.of();
            case MUSIC -> Set.of(SoundSource.MUSIC);
            case MUSIC_AND_RECORDS -> Set.of(SoundSource.MUSIC, SoundSource.RECORDS);
            case ALL -> Set.of(SoundSource.values());
        };
    }

    /**
     * Makes exactly {@code wanted} silent and everything else audible. The engine's real state is
     * checked rather than trusted, because it can throw our zero away behind our back: see
     * {@link #gainOf}.
     */
    private static void silence(Set<SoundSource> wanted) {
        for (SoundSource source : MUTED.toArray(SoundSource[]::new)) {
            if (!wanted.contains(source)) {
                unmute(source);
            }
        }
        for (SoundSource source : wanted) {
            if (!MUTED.contains(source)) {
                PREVIOUS_GAIN.put(source, gainOf(source));
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.getSoundManager().updateCategoryVolume(source, 0.0F);
                }
                MUTED.add(source);
            } else if (gainOf(source) != 0.0F) {
                // The engine reset this category's gain. SoundEngine.loadLibrary() runs
                // asynchronously during startup (and on a sound-device change) and calls
                // gainBySource.clear(), and a resource reload goes through reload(); both wipe our
                // zero while our bookkeeping still says "muted". Without this check only the first
                // mute after a load would ever work.
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.getSoundManager().updateCategoryVolume(source, 0.0F);
                }
            }
        }
    }

    private static void unmute(SoundSource source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            // A missing entry must never be restored as 0.0f, which would leave the source silent.
            // SoundSource's default gain is 1.0f, so that is the safe fallback.
            float gain = PREVIOUS_GAIN.getOrDefault(source, 1.0F);
            mc.getSoundManager().updateCategoryVolume(source, gain);
        }
        PREVIOUS_GAIN.removeFloat(source);
        MUTED.remove(source);
    }

    /**
     * The gain currently in effect for a source, read out of {@code SoundEngine}'s private
     * {@code gainBySource} map. Falls back to 1.0f - the value the engine itself uses for a source it
     * has not stored anything for - when the field cannot be read.
     */
    @SuppressWarnings("unchecked")
    private static float gainOf(SoundSource source) {
        Minecraft mc = Minecraft.getInstance();
        SoundEngine engine = mc == null ? null : engineOf(mc);
        if (engine == null) {
            return 1.0F;
        }
        try {
            Field field = SoundEngine.class.getDeclaredField("gainBySource");
            field.setAccessible(true);
            Object value = field.get(engine);
            if (value instanceof Object2FloatMap) {
                float gain = ((Object2FloatMap<SoundSource>) value).getFloat(source);
                // A source that was never stored returns the map's default (0.0f), which is not a
                // gain we could have read before our own write, so treat it as "not set yet".
                return gain > 0.0F ? gain : 1.0F;
            }
        } catch (Throwable ignored) {
            // A future Minecraft may rename the field; 1.0f is the engine's own default.
        }
        return 1.0F;
    }

    /** {@code SoundManager.soundEngine} is private and has no getter. */
    private static SoundEngine engineOf(Minecraft mc) {
        try {
            Field field = mc.getSoundManager().getClass().getDeclaredField("soundEngine");
            field.setAccessible(true);
            Object value = field.get(mc.getSoundManager());
            return value instanceof SoundEngine engine ? engine : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}