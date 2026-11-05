package dca.mixin;

import dca.DcaSettings;
import dca.EndPortalDestination;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server-side trigger for {@code endPortalDestinationInEnd}.
 *
 * <p>Replaces {@code EndPortalBlock.getPortalDestination} entirely for the one case the rule is about:
 * the portal is being used <b>inside the End</b> and the entity matches the configured category. In
 * that case the vanilla return value (the entity's respawn point) is discarded and the Overworld-style
 * End arrival is returned instead - see {@link EndPortalDestination}.
 *
 * <p>Everything else is untouched: outside the End the method behaves exactly like vanilla, and so does
 * it inside the End when the rule is {@code OFF} or the entity does not match.
 *
 * <p>The rule cannot be checked from the client side at all here, and does not need to be: the whole
 * decision happens on the server and reaches the client as an ordinary teleport.
 */
@Mixin(EndPortalBlock.class)
public abstract class EndPortalDestinationMixin {

    /** Full descriptor on purpose: it cannot silently match a different overload later. */
    private static final String GET_PORTAL_DESTINATION =
            "getPortalDestination(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/world/level/portal/TeleportTransition;";

    @Inject(method = GET_PORTAL_DESTINATION, at = @At("HEAD"), cancellable = true)
    private void dca$redirectEndPortalInsideTheEnd(ServerLevel level, Entity entity, BlockPos pos,
                                                   CallbackInfoReturnable<TeleportTransition> cir) {
        DcaSettings.EndPortalEntryMode mode = DcaSettings.endPortalDestinationInEnd;
        if (mode == DcaSettings.EndPortalEntryMode.OFF) {
            return;   // rule off: no dimension check, no entity check
        }
        if (level.dimension() != Level.END) {
            return;   // from the Overworld or the Nether the portal keeps its vanilla destination
        }
        if (!EndPortalDestination.applies(mode, entity)) {
            return;
        }
        TeleportTransition transition = EndPortalDestination.build(level, entity);
        if (transition != null) {
            cir.setReturnValue(transition);
        }
        // transition == null (no End level) -> fall through to vanilla, which also returns null
    }
}