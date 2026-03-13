package com.viameowts.viastyle.mixin;

import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.decoration.DisplayEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for {@link DisplayEntity} private members.
 * Used by the "display" nametag mode to configure TextDisplayEntity properties.
 */
@Mixin(DisplayEntity.class)
public interface DisplayEntityAccessor {

    @Invoker("setBillboardMode")
    void invokeSetBillboardMode(DisplayEntity.BillboardMode mode);

    @Invoker("setTeleportDuration")
    void invokeSetTeleportDuration(int ticks);

    @Accessor("TRANSLATION")
    static TrackedData<Vector3f> getTranslationField() {
        throw new AssertionError();
    }
}
