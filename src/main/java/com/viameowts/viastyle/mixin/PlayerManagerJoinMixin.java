package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.viaStyle;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the vanilla "X joined the game" broadcast so viaStyle can
 * send its own formatted join message instead.
 */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerJoinMixin {

    @Redirect(
            method = "onPlayerConnect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V")
    )
    private void viaStyle$suppressJoinBroadcast(PlayerManager instance, Text message, boolean overlay) {
        // Suppress — viaStyle sends its own join message from the JOIN event.
        viaStyle.LOGGER.debug("[viaStyle] Suppressed vanilla join broadcast: {}", message.getString());
    }
}
