package com.viameowts.viastyle;

import net.minecraft.text.Text;

/**
 * Duck interface injected into {@link net.minecraft.server.network.ServerPlayerEntity}
 * by the mixin.  Allows setting a custom player-list display name and list order.
 */
public interface PlayerListNameAccess {

    /** Sets the custom tab-list display name ({@code null} = use vanilla name). */
    void viaStyle$setCustomListName(Text name);

    /** Returns the custom tab-list display name, or {@code null}. */
    Text viaStyle$getCustomListName();

    /** Sets the custom tab-list order (lower = higher in the list). */
    void viaStyle$setListOrder(int order);

    /** Returns the custom list order, or {@code 0}. */
    int viaStyle$getListOrder();
}
