package com.viameowts.viastyle.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the {@code EntityType.isSaveable()} check inside
 * {@link Entity#startRiding(Entity, boolean, boolean)} so that
 * entities tagged {@code viastyle_nametag} can mount players.
 *
 * <p>In vanilla, {@code EntityType.PLAYER} has {@code disableSaving()},
 * which makes {@code isSaveable()} return {@code false}. The
 * {@code startRiding} method unconditionally rejects riding an entity
 * whose type is not saveable, even when {@code force=true}. This
 * redirect pretends the type IS saveable when our display entity is
 * the one trying to ride.</p>
 */
@Mixin(Entity.class)
public abstract class EntityStartRidingMixin {

    @Redirect(
            method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/entity/EntityType;isSaveable()Z"))
    private boolean viaStyle$bypassSaveableForNametag(EntityType<?> type) {
        Entity self = (Entity) (Object) this;
        if (self.getCommandTags().contains("viastyle_nametag")) {
            return true; // pretend vehicle type is saveable
        }
        return type.isSaveable();
    }
}
