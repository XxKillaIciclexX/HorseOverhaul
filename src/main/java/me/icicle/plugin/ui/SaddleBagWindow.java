package me.icicle.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public class SaddleBagWindow extends ContainerWindow {

    private static final String SADDLE_BAG_TITLE = "Saddle Bag";
    private static final int SLOT_COUNT = 9;
    private static final int ROW_COUNT = 1;

    public SaddleBagWindow(ItemContainer itemContainer) {
        super(itemContainer);
        getData().addProperty("title", SADDLE_BAG_TITLE);
        getData().addProperty("name", SADDLE_BAG_TITLE);
        getData().addProperty("capacity", SLOT_COUNT);
        getData().addProperty("rows", ROW_COUNT);
    }
}
