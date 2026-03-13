package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.viaStyle;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the vanilla "X left the game" broadcast so viaStyle can
 * send its own formatted leave message instead.
 *
 * <p>The leave message is broadcast from {@code cleanUp()} in
 * {@code ServerPlayNetworkHandler}.</p>
 */
@Mixin(net.minecraft.server.network.ServerPlayNetworkHandler.class)
public abstract class ServerPlayLeaveMixin {

    @Redirect(
            method = "cleanUp",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V")
    )
    private void viaStyle$suppressLeaveBroadcast(PlayerManager instance, Text message, boolean overlay) {
        // Suppress — viaStyle sends its own leave message from the DISCONNECT event.
        viaStyle.LOGGER.debug("[viaStyle] Suppressed vanilla leave broadcast: {}", message.getString());
    }
}
