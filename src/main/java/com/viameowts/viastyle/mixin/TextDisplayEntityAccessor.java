package com.viameowts.viastyle.mixin;

import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for {@link DisplayEntity.TextDisplayEntity} private setters.
 * Used by the "display" nametag mode to set text, opacity and background.
 */
@Mixin(DisplayEntity.TextDisplayEntity.class)
public interface TextDisplayEntityAccessor {

    @Invoker("setText")
    void invokeSetText(Text text);

    @Invoker("setTextOpacity")
    void invokeSetTextOpacity(byte opacity);

    @Invoker("setBackground")
    void invokeSetBackground(int color);
}
