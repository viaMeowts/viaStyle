package com.viameowts.viastyle.mixin;

import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels {@code PlayerManager.broadcast(Text, boolean)} when the message string
 * is empty or blank.
 *
 * <p>Provides a fallback safety net for cases where an empty text would be
 * broadcast to all players (e.g. when a Vanish event handler returns
 * {@code Text.empty()} but the config has no format defined).
 *
 * <p>Note: In Yarn mappings, {@code broadcast(Text, boolean)} (method_43514)
 * is the correct target — it corresponds to Mojang's {@code broadcastSystemMessage}
 * and is used for join/leave/death messages.</p>
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerBroadcastMixin {

    @Inject(
            method = "broadcast(Lnet/minecraft/text/Text;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void viaStyle$suppressEmptyBroadcast(Text message, boolean overlay,
                                                   CallbackInfo ci) {
        if (message == null || message.getString().isBlank()) {
            ci.cancel();
        }
    }
}
