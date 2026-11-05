package dca;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Optional interop with carpet-ams-addition.
 *
 * <p>AMS has a rule {@code endPortalChunkLoadDisabled} that removes the {@code PLACE_PORTAL_TICKET}
 * hook from the Overworld-to-End transition (it {@code @WrapOperation}s the
 * {@code PostTeleportTransition.then(PLACE_PORTAL_TICKET)} call site). The transition
 * {@link EndPortalDestination} builds is a new object, so AMS's wrap cannot reach it - but the two
 * rules would then disagree about whether arriving in the End adds a chunk ticket.
 *
 * <p>So we read AMS's rule directly and follow it. The lookup is done once, reflectively, and any
 * failure (AMS absent, class renamed, field gone) is remembered as "no interop" and never retried and
 * never logged: the caller then just keeps the ticket, which is the vanilla behaviour.
 */
final class AmsCompat {

    /** {@code null} = not looked up yet; otherwise whether AMS's rule is currently on. */
    private static Boolean chunkLoadDisabled;

    private AmsCompat() {
    }

    /**
     * @return true when carpet-ams-addition is present and its {@code endPortalChunkLoadDisabled} rule
     *         is enabled, meaning the End portal arrival should not add a chunk ticket.
     */
    static boolean endPortalChunkLoadDisabled() {
        Boolean cached = chunkLoadDisabled;
        if (cached != null) {
            return cached;
        }
        boolean value = false;
        try {
            Class<?> settings = Class.forName("carpetamsaddition.CarpetAMSAdditionSettings");
            Field field = settings.getField("endPortalChunkLoadDisabled");
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == boolean.class) {
                value = field.getBoolean(null);
            }
        } catch (Throwable ignored) {
            // AMS not installed, or its internals moved: interop silently off.
        }
        chunkLoadDisabled = value;
        return value;
    }
}