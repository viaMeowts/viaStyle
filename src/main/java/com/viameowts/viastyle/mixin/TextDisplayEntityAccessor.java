package com.viameowts.viastyle.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor mixin for {@link Display.TextDisplay} private setters.
 * Used by the "display" nametag mode to set text, opacity and background.
 */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayEntityAccessor {

    @Invoker("setText")
    void invokeSetText(Component text);

    @Invoker("setTextOpacity")
    void invokeSetTextOpacity(byte opacity);

    @Invoker("setBackgroundColor")
    void invokeSetBackground(int color);
}
