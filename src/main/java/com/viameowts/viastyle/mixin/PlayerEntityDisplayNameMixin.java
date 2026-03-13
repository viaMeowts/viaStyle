package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.NickColorManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides {@code PlayerEntity.getDisplayName()} so that death messages,
 * server announcements, and any other code using display names show the
 * player's nick-coloured name.
 *
 * <p>Must target {@link PlayerEntity} because {@code getDisplayName()} is
 * defined there, not in {@link ServerPlayerEntity}.</p>
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectDisplayName(CallbackInfoReturnable<Text> cir) {
        if ((Object) this instanceof ServerPlayerEntity spe) {
            MutableText colored = NickColorManager.getColoredName(spe);
            if (colored != null) {
                cir.setReturnValue(colored);
            }
        }
    }
}
