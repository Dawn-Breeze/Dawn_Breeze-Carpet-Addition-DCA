package dca.mixin;

import dca.DcaSettings;
import dca.EndPlatformPulse;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side trigger for {@code endPlatformShapeUpdateOnEntry}.
 *
 * <p>Injects {@code Entity.placePortalTicket(BlockPos)}. That method is vanilla's own "an entity has
 * just arrived through a portal" step: it is called from {@code Entity.teleportCrossDimension} via
 * the {@code TeleportTransition.PLACE_PORTAL_TICKET} post-teleport hook, and it runs
 * <b>after</b> {@code EndPortalBlock.getPortalDestination} has already rebuilt the platform. It also
 * covers every entity (not just players) and every arrival, which is exactly the rule's trigger.
 *
 * <p>The position it receives is the arriving entity's position, but the End platform is fixed
 * (a placed feature at {@code (100,49,0)}), so the rule ignores the argument and uses the hard-coded
 * obsidian coordinates - see {@link EndPlatformPulse}.
 *
 * <p>Not client-only: the arriving entity can be any entity and the work happens on the server.
 */
@Mixin(Entity.class)
public abstract class EndPortalArrivalMixin {

    /** Full descriptor on purpose: it cannot silently match a different overload later. */
    private static final String PLACE_PORTAL_TICKET = "placePortalTicket(Lnet/minecraft/core/BlockPos;)V";

    /** {@code Entity.level()} - shadowed because a mixin has no inheritance relationship to its target. */
    @Shadow
    public abstract Level level();

    @Inject(method = PLACE_PORTAL_TICKET, at = @At("HEAD"))
    private void dca$onPortalArrival(BlockPos pos, CallbackInfo ci) {
        if (!DcaSettings.endPlatformShapeUpdateOnEntry) {
            return;   // rule off: do not even look at the level
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;   // client side has nothing to do here; the server syncs the result
        }
        EndPlatformPulse.emitOnArrival(serverLevel);
    }
}