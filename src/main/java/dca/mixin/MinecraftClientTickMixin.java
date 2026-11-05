package dca.mixin;

import dca.client.FocusSound;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client tick heartbeat for the {@code pauseSoundOnFocusLost} rule.
 *
 * <p>Unlike the trade key handler this has nothing to do with container screens: the window can lose
 * focus from anywhere, including with no screen open at all, so the hook sits on
 * {@code Minecraft.tick()} - one call per client tick, which is the cheapest place to sample
 * {@code Minecraft.isWindowActive()}.
 *
 * <p>The callback itself is trivial: the real work (and all of the state) is in
 * {@link FocusSound}, which returns immediately while the rule is off.
 *
 * <p>Client-only: listed under {@code client} in {@code dca.mixins.json}. A dedicated server must
 * never load this, because it references {@code net.minecraft.client.*}.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientTickMixin {

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void dca$onClientTick(CallbackInfo ci) {
        FocusSound.clientTick((Minecraft) (Object) this);
    }
}