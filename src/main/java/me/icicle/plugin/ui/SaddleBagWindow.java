package me.icicle.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public class SaddleBagWindow extends ContainerWindow {

    private static final String SADDLE_BAG_TITLE = "Saddle Bag";

    public SaddleBagWindow(ItemContainer itemContainer, int slotCount, int rowCount) {
        super(itemContainer);
        getData().addProperty("title", SADDLE_BAG_TITLE);
        getData().addProperty("name", SADDLE_BAG_TITLE);
        getData().addProperty("capacity", slotCount);
        getData().addProperty("rows", rowCount);
    }
}
