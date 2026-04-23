package me.icicle.plugin.ui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ValidatedWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.icicle.plugin.component.EquippedSaddleComponent;

public class HorseInventoryWindow extends ContainerWindow implements ValidatedWindow {

    private static final String HORSE_INVENTORY_TITLE = "Horse Inventory";
    private final Ref<EntityStore> horseRef;

    public HorseInventoryWindow(ItemContainer itemContainer, Ref<EntityStore> horseRef, int slotCount, int rowCount) {
        super(itemContainer);
        this.horseRef = horseRef;
        getData().addProperty("title", HORSE_INVENTORY_TITLE);
        getData().addProperty("name", HORSE_INVENTORY_TITLE);
        getData().addProperty("capacity", slotCount);
        getData().addProperty("rows", rowCount);
    }

    @Override
    public boolean validate(Ref<EntityStore> playerRef, ComponentAccessor<EntityStore> store) {
        if (horseRef == null || !horseRef.isValid()) {
            return false;
        }

        EquippedSaddleComponent equippedSaddle = store.getComponent(
                horseRef,
                EquippedSaddleComponent.getComponentType()
        );
        return equippedSaddle != null && !equippedSaddle.getSaddleStack().isEmpty();
    }
}
