package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.viaStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses the vanilla "X joined the game" broadcast so viaStyle can
 * send its own formatted join message instead.
 */
@Mixin(PlayerList.class)
public abstract class PlayerManagerJoinMixin {

    @Redirect(
            method = "placeNewPlayer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void viaStyle$suppressJoinBroadcast(PlayerList instance, Component message, boolean overlay) {
        // Suppress — viaStyle sends its own join message from the JOIN event.
        viaStyle.LOGGER.debug("[viaStyle] Suppressed vanilla join broadcast: {}", message.getString());
    }
}
