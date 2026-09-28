package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.PlayerListNameAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects a custom player-list display name into {@link ServerPlayer}.
 * When {@code viaStyle$customListName} is non-null the custom text is returned
 * instead of the vanilla one.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin implements PlayerListNameAccess {

    @Unique
    private Component viaStyle$customListName = null;

    @Unique
    private int viaStyle$listOrder = 0;

    @Override
    public void viaStyle$setCustomListName(Component name) {
        this.viaStyle$customListName = name;
    }

    @Override
    public Component viaStyle$getCustomListName() {
        return this.viaStyle$customListName;
    }

    @Override
    public void viaStyle$setListOrder(int order) {
        this.viaStyle$listOrder = order;
    }

    @Override
    public int viaStyle$getListOrder() {
        return this.viaStyle$listOrder;
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectPlayerListName(CallbackInfoReturnable<Component> cir) {
        if (this.viaStyle$customListName != null) {
            cir.setReturnValue(this.viaStyle$customListName);
        }
    }

    @Inject(method = "getTabListOrder", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectPlayerListOrder(CallbackInfoReturnable<Integer> cir) {
        if (this.viaStyle$listOrder != 0) {
            cir.setReturnValue(this.viaStyle$listOrder);
        }
    }

}
