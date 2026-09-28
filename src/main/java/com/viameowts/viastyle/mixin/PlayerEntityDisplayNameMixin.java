package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.NickColorManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides {@code PlayerEntity.getDisplayName()} so that death messages,
 * server announcements, and any other code using display names show the
 * player's nick-coloured name.
 *
 * <p>Must target {@link Player} because {@code getDisplayName()} is
 * defined there, not in {@link ServerPlayer}.</p>
 */
@Mixin(Player.class)
public abstract class PlayerEntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectDisplayName(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof ServerPlayer spe) {
            MutableComponent colored = NickColorManager.getColoredName(spe);
            if (colored != null) {
                cir.setReturnValue(colored);
            }
        }
    }
}
