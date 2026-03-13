package com.viameowts.viastyle.mixin;

import com.viameowts.viastyle.PlayerListNameAccess;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects a custom player-list display name into {@link ServerPlayerEntity}.
 * When {@code viaStyle$customListName} is non-null the custom text is returned
 * instead of the vanilla one.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin implements PlayerListNameAccess {

    @Unique
    private Text viaStyle$customListName = null;

    @Unique
    private int viaStyle$listOrder = 0;

    @Override
    public void viaStyle$setCustomListName(Text name) {
        this.viaStyle$customListName = name;
    }

    @Override
    public Text viaStyle$getCustomListName() {
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

    @Inject(method = "getPlayerListName", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectPlayerListName(CallbackInfoReturnable<Text> cir) {
        if (this.viaStyle$customListName != null) {
            cir.setReturnValue(this.viaStyle$customListName);
        }
    }

    @Inject(method = "getPlayerListOrder", at = @At("HEAD"), cancellable = true)
    private void viaStyle$injectPlayerListOrder(CallbackInfoReturnable<Integer> cir) {
        if (this.viaStyle$listOrder != 0) {
            cir.setReturnValue(this.viaStyle$listOrder);
        }
    }

}
