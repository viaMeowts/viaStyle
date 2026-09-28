package com.viameowts.viastyle.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for {@link Display} private members.
 * Used by the "display" nametag mode to configure TextDisplayEntity properties.
 */
@Mixin(Display.class)
public interface DisplayEntityAccessor {

    @Invoker("setBillboardConstraints")
    void invokeSetBillboardMode(Display.BillboardConstraints mode);

    @Invoker("setPosRotInterpolationDuration")
    void invokeSetTeleportDuration(int ticks);

    @Accessor("DATA_TRANSLATION_ID")
    static EntityDataAccessor<Vector3f> getTranslationField() {
        throw new AssertionError();
    }
}
