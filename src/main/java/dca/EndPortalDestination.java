package dca;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Server half of the {@code endPortalDestinationInEnd} rule: while inside the End, an end portal block
 * sends entities to the End instead of to their respawn point - i.e. the destination is built exactly
 * the way the Overworld-to-End branch of {@code EndPortalBlock.getPortalDestination} builds it.
 *
 * <h2>What "exactly the way" means</h2>
 * The vanilla Overworld branch produces:
 *
 * <pre>spawnPos  = Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT)   // (100.5, 50.0, 0.5)
 * platform  = EndPlatformFeature.createEndPlatform(endLevel, BlockPos.containing(spawnPos).below(), true)
 * if (entity instanceof ServerPlayer) spawnPos = spawnPos.subtract(0.0, 1.0, 0.0)                 // y = 49
 * else spawnPos = Vec3.atBottomCenterOf(entity.adjustSpawnLocation(endLevel, ServerLevel.END_SPAWN_POINT))
 * yRot      = Direction.WEST.toYRot()          // 270
 * xRot      = 0.0F
 * relatives = Relative.union(Relative.DELTA, Set.of(Relative.X_ROT))
 * post      = PLAY_PORTAL_SOUND.then(PLACE_PORTAL_TICKET)</pre>
 *
 * The platform origin is always fixed: {@code END_SPAWN_POINT.below()} = (100, 49, 0), obsidian at y=48
 * and the air above it cleared at y=49..52. Only the entity's landing spot differs between players and
 * non-players in vanilla.
 *
 * <h2>One deliberate deviation: non-player landing spot</h2>
 * Vanilla lets {@code Entity.adjustSpawnLocation} pick the landing spot for non-player entities, but in
 * 26.2 that method ignores both of its arguments: it reads the <b>global (Overworld) respawn data</b>,
 * loads that column's chunk in the destination level and returns the destination height at that column.
 * The result has nothing to do with the End spawn point and the chunk generation is an unwanted side
 * effect, so this rule puts every entity (player or not) on the fixed platform top instead. Momentum,
 * tags, rotations, relatives and post-transition actions still match the vanilla branch.
 *
 * <h2>Vanilla deliberately differs for players</h2>
 * Vanilla's Overworld branch hands a {@code ServerPlayer} to
 * {@code findRespawnPositionAndUseSpawnBlock(false, DO_NOTHING)} - which ignores the End spawn point
 * entirely and returns the player's <b>personal respawn</b>. This rule does not copy that split: the
 * configured entity category is sent to the End regardless, which is the whole point of the rule's
 * {@link DcaSettings.EndPortalEntryMode#ALL} mode (and why the rule warns about getting stuck).
 */
public final class EndPortalDestination {

    private EndPortalDestination() {
    }

    /** Should this entity be redirected, given the rule value? */
    public static boolean applies(DcaSettings.EndPortalEntryMode mode, Entity entity) {
        return switch (mode) {
            case OFF -> false;
            case ALL -> true;
            case NON_PLAYER -> !(entity instanceof Player);
            case ITEM -> entity instanceof ItemEntity;
        };
    }

    /**
     * Builds the Overworld-style End arrival transition for this entity, rebuilding the platform as a
     * side effect exactly like the vanilla branch does.
     *
     * @return the transition, or null when the End level is unavailable (vanilla returns null there too)
     */
    public static TeleportTransition build(ServerLevel fromLevel, Entity entity) {
        ServerLevel end = fromLevel.getServer().getLevel(Level.END);
        if (end == null) {
            return null;   // the End is disabled: behave like vanilla and do not teleport at all
        }

        // Vanilla's fixed End arrival point: platform origin END_SPAWN_POINT.below() = (100, 49, 0).
        // Never derive this from the entity: Entity.adjustSpawnLocation in 26.2 ignores its arguments
        // and returns the global Overworld respawn column, which used to move the platform at random.
        Vec3 pos = Vec3.atBottomCenterOf(ServerLevel.END_SPAWN_POINT);

        // Rebuild the platform first (with drops), exactly like the vanilla Overworld branch.
        EndPlatformFeature.createEndPlatform(end, BlockPos.containing(pos).below(), true);

        // Landing spot: platform top (y=49, standing on the obsidian at y=48). Vanilla only subtracts the
        // extra block for players; this rule uses the same fixed spot for every entity on purpose, see
        // the class javadoc ("One deliberate deviation").
        pos = pos.subtract(0.0, 1.0, 0.0);

        float yRot = Direction.WEST.toYRot();
        float xRot = 0.0F;
        Set<Relative> relatives = Relative.union(Relative.DELTA, Set.<Relative>of(Relative.X_ROT));

        TeleportTransition.PostTeleportTransition post = TeleportTransition.PLAY_PORTAL_SOUND;
        if (!AmsCompat.endPortalChunkLoadDisabled()) {
            // Follows carpet-ams-addition when it is installed and its rule is on (see AmsCompat).
            post = post.then(TeleportTransition.PLACE_PORTAL_TICKET);
        }
        return new TeleportTransition(end, pos, Vec3.ZERO, yRot, xRot, relatives, post);
    }
}