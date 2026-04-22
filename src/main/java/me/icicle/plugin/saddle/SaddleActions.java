package me.icicle.plugin.saddle;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemStackItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import me.icicle.plugin.HorseOverhaul;
import me.icicle.plugin.component.EquippedSaddleComponent;
import me.icicle.plugin.ui.HorseInventoryWindow;
import me.icicle.plugin.ui.SaddleBagWindow;

import java.util.Objects;

public final class SaddleActions {

    public static final String SADDLE_ITEM_ID = "Horse_Saddle";
    private static final String LOCKED_SLOT_ITEM_ID = "Horse_Locked_Slot";

    private static final short SADDLE_SLOT_COUNT = 9;
    private static final short HORSE_SADDLE_SLOT_CAPACITY = 1;
    private static final short HORSE_INVENTORY_SLOT_COUNT = 18;
    private static final short HORSE_GEAR_SLOT_INDEX = 0;
    private static final short HORSE_BAG_SLOT_START = 9;
    private static final String HORSE_ROLE_NAME = "Horse";
    private static final String TAMED_HORSE_ROLE_NAME = "Tamed_Horse";
    private static final String SADDLED_HORSE_ROLE_NAME = "Horse_Overhaul_Saddled";

    private SaddleActions() {
    }

    public static boolean openSaddleInventory(Store<EntityStore> store, Ref<EntityStore> playerRef, short hotbarSlot) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }

        ItemContainer hotbar = player.getInventory().getSectionById(InventoryComponent.HOTBAR_SECTION_ID);
        if (hotbar == null || hotbarSlot < 0 || hotbarSlot >= hotbar.getCapacity()) {
            return false;
        }

        ItemStack saddleStack = hotbar.getItemStack(hotbarSlot);
        if (!isSaddleItem(saddleStack)) {
            return false;
        }

        ItemStackItemContainer saddleContainer = ItemStackItemContainer.ensureContainer(
                hotbar,
                hotbarSlot,
                SADDLE_SLOT_COUNT
        );
        if (saddleContainer == null) {
            return false;
        }

        SaddleBagWindow saddleWindow = new SaddleBagWindow(saddleContainer);
        boolean opened = player.getPageManager().setPageWithWindows(
                playerRef,
                store,
                Page.Bench,
                true,
                saddleWindow
        );
        if (opened) {
            registerRestrictedHotbarWindow(store, playerRef, saddleWindow, hotbarSlot);
        }
        return opened;
    }

    public static boolean openEquippedSaddleInventory(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> targetEntity,
            short restrictedHotbarSlot
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || targetEntity == null || !targetEntity.isValid()) {
            return false;
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return false;
        }

        SimpleItemContainer saddleSlotContainer = createHorseSaddleSlotContainer(store, playerRef, targetEntity);
        if (saddleSlotContainer == null) {
            return false;
        }

        ItemStackItemContainer persistedContainer = ItemStackItemContainer.ensureContainer(
                saddleSlotContainer,
                (short) 0,
                SADDLE_SLOT_COUNT
        );
        if (persistedContainer == null) {
            return false;
        }

        SaddleBagWindow saddleWindow = new SaddleBagWindow(persistedContainer);
        boolean opened = player.getPageManager().setPageWithWindows(
                playerRef,
                store,
                Page.Bench,
                true,
                saddleWindow
        );
        if (opened) {
            registerRestrictedHotbarWindow(
                    store,
                    playerRef,
                    saddleWindow,
                    resolveRestrictedHotbarSlot(store, playerRef, restrictedHotbarSlot)
            );
        }
        return opened;
    }

    public static boolean openHorseInventory(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> targetEntity,
            short restrictedHotbarSlot
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || targetEntity == null || !targetEntity.isValid()) {
            return false;
        }

        SimpleItemContainer horseInventoryContainer = createHorseInventoryContainer(store, playerRef, targetEntity);
        if (horseInventoryContainer == null) {
            return false;
        }

        HorseInventoryWindow horseInventoryWindow = new HorseInventoryWindow(horseInventoryContainer, targetEntity);
        boolean opened = player.getPageManager().setPageWithWindows(
                playerRef,
                store,
                Page.Bench,
                true,
                horseInventoryWindow
        );
        if (opened) {
            registerRestrictedHotbarWindow(
                    store,
                    playerRef,
                    horseInventoryWindow,
                    resolveRestrictedHotbarSlot(store, playerRef, restrictedHotbarSlot)
            );
        }
        return opened;
    }

    public static boolean openMountedSaddleInventory(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        Ref<EntityStore> mountedHorseRef = getMountedSaddledHorse(store, playerRef);
        return mountedHorseRef != null
                && openEquippedSaddleInventory(
                store,
                playerRef,
                mountedHorseRef,
                getActiveHotbarSlot(store, playerRef)
        );
    }

    public static boolean openMountedHorseInventory(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        Ref<EntityStore> mountedHorseRef = getMountedSaddledHorse(store, playerRef);
        return mountedHorseRef != null
                && openHorseInventory(
                store,
                playerRef,
                mountedHorseRef,
                getActiveHotbarSlot(store, playerRef)
        );
    }

    public static boolean equipSaddleOnHorse(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            short hotbarSlot,
            Ref<EntityStore> targetEntity
    ) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || targetEntity == null || !targetEntity.isValid()) {
            return false;
        }

        ItemContainer hotbar = player.getInventory().getSectionById(InventoryComponent.HOTBAR_SECTION_ID);
        if (hotbar == null || hotbarSlot < 0 || hotbarSlot >= hotbar.getCapacity()) {
            return false;
        }

        ItemStack saddleStack = hotbar.getItemStack(hotbarSlot);
        if (!isSaddleItem(saddleStack)) {
            return false;
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return false;
        }

        String roleName = targetNpc.getRoleName();
        boolean isHorse = HORSE_ROLE_NAME.equals(roleName);
        boolean isTamedHorse = TAMED_HORSE_ROLE_NAME.equals(roleName);
        if (!isHorse && !isTamedHorse) {
            return false;
        }

        EquippedSaddleComponent equippedSaddle = store.getComponent(targetEntity, EquippedSaddleComponent.getComponentType());
        if (equippedSaddle != null && !ItemStack.isEmpty(equippedSaddle.getSaddleStack())) {
            return false;
        }

        Role currentRole = targetNpc.getRole();
        if (currentRole == null || currentRole.isRoleChangeRequested()) {
            return false;
        }

        int saddledHorseRoleIndex = NPCPlugin.get().getIndex(SADDLED_HORSE_ROLE_NAME);
        if (saddledHorseRoleIndex < 0) {
            return false;
        }

        if (isHorse || isTamedHorse) {
            if (currentRole == null || currentRole.isRoleChangeRequested()) {
                return false;
            }
        }

        if (!hotbar.removeItemStackFromSlot(hotbarSlot, 1).succeeded()) {
            return false;
        }

        store.putComponent(
                targetEntity,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(saddleStack.withQuantity(1))
        );
        refreshSaddledHorseTracking(store, targetEntity);

        RoleChangeSystem.requestRoleChange(
                targetEntity,
                currentRole,
                saddledHorseRoleIndex,
                true,
                store
        );
        return true;
    }

    public static boolean isSaddleItem(ItemStack itemStack) {
        return itemStack != null
                && !itemStack.isEmpty()
                && SADDLE_ITEM_ID.equals(itemStack.getItemId());
    }

    public static boolean hasEquippedSaddle(Store<EntityStore> store, Ref<EntityStore> targetEntity) {
        if (targetEntity == null || !targetEntity.isValid()) {
            return false;
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return false;
        }

        EquippedSaddleComponent equippedSaddle = store.getComponent(targetEntity, EquippedSaddleComponent.getComponentType());
        return equippedSaddle != null && !ItemStack.isEmpty(equippedSaddle.getSaddleStack());
    }

    public static boolean isSaddledHorse(Store<EntityStore> store, Ref<EntityStore> targetEntity) {
        if (targetEntity == null || !targetEntity.isValid()) {
            return false;
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return false;
        }

        return SADDLED_HORSE_ROLE_NAME.equals(targetNpc.getRoleName()) || hasEquippedSaddle(store, targetEntity);
    }

    private static SimpleItemContainer createHorseSaddleSlotContainer(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        ItemStack saddleStack = getOrCreateEquippedSaddleStack(store, horseRef);
        if (ItemStack.isEmpty(saddleStack)) {
            return null;
        }

        SimpleItemContainer saddleSlotContainer = new SimpleItemContainer(HORSE_SADDLE_SLOT_CAPACITY);
        saddleSlotContainer.setItemStackForSlot((short) 0, saddleStack);
        saddleSlotContainer.registerChangeEvent(event -> syncHorseSaddleSlot(store, playerRef, horseRef, saddleSlotContainer));
        return saddleSlotContainer;
    }

    private static SimpleItemContainer createHorseInventoryContainer(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        ItemStack saddleStack = getOrCreateEquippedSaddleStack(store, horseRef);
        if (ItemStack.isEmpty(saddleStack)) {
            return null;
        }

        SimpleItemContainer horseInventoryContainer = new SimpleItemContainer(HORSE_INVENTORY_SLOT_COUNT);
        configureHorseInventoryFilters(horseInventoryContainer);
        horseInventoryContainer.setItemStackForSlot(HORSE_GEAR_SLOT_INDEX, saddleStack);
        loadLockedHorseGearSlots(horseInventoryContainer);
        loadHorseBagSlotsFromSaddle(horseInventoryContainer);

        ItemStack[] trackedSaddleStack = new ItemStack[]{normalizeItemStack(horseInventoryContainer.getItemStack(HORSE_GEAR_SLOT_INDEX))};
        boolean[] isSyncing = new boolean[]{false};
        horseInventoryContainer.registerChangeEvent(
                event -> syncHorseInventoryContainer(
                        store,
                        playerRef,
                        horseRef,
                        horseInventoryContainer,
                        trackedSaddleStack,
                        isSyncing
                )
        );
        return horseInventoryContainer;
    }

    private static ItemStack getOrCreateEquippedSaddleStack(Store<EntityStore> store, Ref<EntityStore> targetEntity) {
        if (targetEntity == null || !targetEntity.isValid()) {
            return ItemStack.EMPTY;
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return ItemStack.EMPTY;
        }

        EquippedSaddleComponent equippedSaddle = store.getComponent(targetEntity, EquippedSaddleComponent.getComponentType());
        if (equippedSaddle != null && !ItemStack.isEmpty(equippedSaddle.getSaddleStack())) {
            return equippedSaddle.getSaddleStack();
        }

        if (!SADDLED_HORSE_ROLE_NAME.equals(targetNpc.getRoleName())) {
            return ItemStack.EMPTY;
        }

        ItemStack defaultSaddleStack = new ItemStack(SADDLE_ITEM_ID, 1);
        store.putComponent(
                targetEntity,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(defaultSaddleStack)
        );
        refreshSaddledHorseTracking(store, targetEntity);
        return defaultSaddleStack;
    }

    private static void syncHorseSaddleSlot(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            SimpleItemContainer saddleSlotContainer
    ) {
        if (store == null || horseRef == null || !horseRef.isValid() || saddleSlotContainer == null) {
            return;
        }

        ItemStack updatedSaddleStack = saddleSlotContainer.getItemStack((short) 0);
        store.putComponent(
                horseRef,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(updatedSaddleStack)
        );
        refreshSaddledHorseTracking(store, horseRef);

        if (!ItemStack.isEmpty(updatedSaddleStack)) {
            return;
        }

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse == null) {
            return;
        }

        Role currentRole = horse.getRole();
        if (currentRole != null && !currentRole.isRoleChangeRequested()) {
            int tamedHorseRoleIndex = NPCPlugin.get().getIndex(TAMED_HORSE_ROLE_NAME);
            if (tamedHorseRoleIndex >= 0) {
                RoleChangeSystem.requestRoleChange(
                        horseRef,
                        currentRole,
                        tamedHorseRoleIndex,
                        true,
                        store
                );
            }
        }

        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        closePlayerInventoryView(store, playerRef);
    }

    private static void syncHorseInventoryContainer(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef,
            SimpleItemContainer horseInventoryContainer,
            ItemStack[] trackedSaddleStack,
            boolean[] isSyncing
    ) {
        if (store == null
                || horseRef == null
                || !horseRef.isValid()
                || horseInventoryContainer == null
                || trackedSaddleStack == null
                || trackedSaddleStack.length == 0
                || isSyncing == null
                || isSyncing.length == 0
                || isSyncing[0]) {
            return;
        }

        isSyncing[0] = true;
        try {
            ItemStack currentSaddleStack = normalizeItemStack(horseInventoryContainer.getItemStack(HORSE_GEAR_SLOT_INDEX));
            boolean saddleChanged = !Objects.equals(trackedSaddleStack[0], currentSaddleStack);

            if (ItemStack.isEmpty(currentSaddleStack)) {
                trackedSaddleStack[0] = ItemStack.EMPTY;
                clearHorseBagSlots(horseInventoryContainer);
                clearHorseSaddle(store, playerRef, horseRef);
                return;
            }

            if (!isSaddleItem(currentSaddleStack)) {
                horseInventoryContainer.setItemStackForSlot(HORSE_GEAR_SLOT_INDEX, ItemStack.EMPTY);
                returnUnexpectedHorseSlotItem(store, playerRef, currentSaddleStack);
                trackedSaddleStack[0] = ItemStack.EMPTY;
                clearHorseBagSlots(horseInventoryContainer);
                clearHorseSaddle(store, playerRef, horseRef);
                return;
            }

            if (saddleChanged) {
                loadHorseBagSlotsFromSaddle(horseInventoryContainer);
            } else {
                persistHorseBagSlotsToSaddle(horseInventoryContainer);
            }

            ItemStack persistedSaddleStack = normalizeItemStack(horseInventoryContainer.getItemStack(HORSE_GEAR_SLOT_INDEX));
            trackedSaddleStack[0] = persistedSaddleStack;
            store.putComponent(
                    horseRef,
                    EquippedSaddleComponent.getComponentType(),
                    new EquippedSaddleComponent(persistedSaddleStack)
            );
            refreshSaddledHorseTracking(store, horseRef);
        } finally {
            isSyncing[0] = false;
        }
    }

    private static void configureHorseInventoryFilters(SimpleItemContainer horseInventoryContainer) {
        horseInventoryContainer.setSlotFilter(
                FilterActionType.ADD,
                HORSE_GEAR_SLOT_INDEX,
                (actionType, container, slot, itemStack) -> isSaddleItem(itemStack)
        );

        for (short slot = 1; slot < HORSE_BAG_SLOT_START; slot++) {
            horseInventoryContainer.setSlotFilter(FilterActionType.ADD, slot, SlotFilter.DENY);
            horseInventoryContainer.setSlotFilter(FilterActionType.REMOVE, slot, SlotFilter.DENY);
            horseInventoryContainer.setSlotFilter(FilterActionType.DROP, slot, SlotFilter.DENY);
        }
    }

    private static void loadLockedHorseGearSlots(SimpleItemContainer horseInventoryContainer) {
        for (short slot = 1; slot < HORSE_BAG_SLOT_START; slot++) {
            horseInventoryContainer.setItemStackForSlot(slot, new ItemStack(LOCKED_SLOT_ITEM_ID, 1), false);
        }
    }

    private static void loadHorseBagSlotsFromSaddle(SimpleItemContainer horseInventoryContainer) {
        ItemStack saddleStack = normalizeItemStack(horseInventoryContainer.getItemStack(HORSE_GEAR_SLOT_INDEX));
        if (ItemStack.isEmpty(saddleStack) || !isSaddleItem(saddleStack)) {
            clearHorseBagSlots(horseInventoryContainer);
            return;
        }

        ItemStackItemContainer saddleBagContainer = ItemStackItemContainer.ensureContainer(
                horseInventoryContainer,
                HORSE_GEAR_SLOT_INDEX,
                SADDLE_SLOT_COUNT
        );
        if (saddleBagContainer == null) {
            clearHorseBagSlots(horseInventoryContainer);
            return;
        }

        for (short slot = 0; slot < SADDLE_SLOT_COUNT; slot++) {
            horseInventoryContainer.setItemStackForSlot(
                    (short) (HORSE_BAG_SLOT_START + slot),
                    saddleBagContainer.getItemStack(slot)
            );
        }
    }

    private static void persistHorseBagSlotsToSaddle(SimpleItemContainer horseInventoryContainer) {
        ItemStack saddleStack = normalizeItemStack(horseInventoryContainer.getItemStack(HORSE_GEAR_SLOT_INDEX));
        if (ItemStack.isEmpty(saddleStack) || !isSaddleItem(saddleStack)) {
            return;
        }

        ItemStackItemContainer saddleBagContainer = ItemStackItemContainer.ensureContainer(
                horseInventoryContainer,
                HORSE_GEAR_SLOT_INDEX,
                SADDLE_SLOT_COUNT
        );
        if (saddleBagContainer == null) {
            return;
        }

        for (short slot = 0; slot < SADDLE_SLOT_COUNT; slot++) {
            saddleBagContainer.setItemStackForSlot(
                    slot,
                    horseInventoryContainer.getItemStack((short) (HORSE_BAG_SLOT_START + slot))
            );
        }
    }

    private static void clearHorseBagSlots(SimpleItemContainer horseInventoryContainer) {
        for (short slot = 0; slot < SADDLE_SLOT_COUNT; slot++) {
            horseInventoryContainer.setItemStackForSlot((short) (HORSE_BAG_SLOT_START + slot), ItemStack.EMPTY);
        }
    }

    private static void clearHorseSaddle(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        store.putComponent(
                horseRef,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(ItemStack.EMPTY)
        );
        refreshSaddledHorseTracking(store, horseRef);

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse != null) {
            Role currentRole = horse.getRole();
            if (currentRole != null && !currentRole.isRoleChangeRequested()) {
                int tamedHorseRoleIndex = NPCPlugin.get().getIndex(TAMED_HORSE_ROLE_NAME);
                if (tamedHorseRoleIndex >= 0) {
                    RoleChangeSystem.requestRoleChange(
                            horseRef,
                            currentRole,
                            tamedHorseRoleIndex,
                            true,
                            store
                    );
                }
            }
        }

        if (playerRef == null || !playerRef.isValid()) {
            return;
        }

        closePlayerInventoryView(store, playerRef);
    }

    private static ItemStack normalizeItemStack(ItemStack itemStack) {
        return itemStack == null ? ItemStack.EMPTY : itemStack;
    }

    private static void closePlayerInventoryView(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef
    ) {
        if (store == null || playerRef == null || !playerRef.isValid()) {
            return;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        clearRestrictedHotbarWindow(store, playerRef);
        player.getWindowManager().closeAllWindows(playerRef, store);
        player.getPageManager().setPage(playerRef, store, Page.None, true);
    }

    private static void returnUnexpectedHorseSlotItem(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            ItemStack itemStack
    ) {
        if (store == null
                || playerRef == null
                || !playerRef.isValid()
                || ItemStack.isEmpty(itemStack)) {
            return;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.giveItem(itemStack, playerRef, store);
    }

    public static Ref<EntityStore> getMountedSaddledHorse(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (playerRef == null || !playerRef.isValid()) {
            return null;
        }

        MountedComponent mountedComponent = store.getComponent(playerRef, MountedComponent.getComponentType());
        if (mountedComponent == null) {
            return null;
        }

        Ref<EntityStore> mountedHorseRef = mountedComponent.getMountedToEntity();
        if (mountedHorseRef == null || !mountedHorseRef.isValid()) {
            return null;
        }

        NPCEntity mountedHorse = store.getComponent(mountedHorseRef, NPCEntity.getComponentType());
        if (mountedHorse == null) {
            return null;
        }

        return isSaddledHorse(store, mountedHorseRef) ? mountedHorseRef : null;
    }

    public static void anchorHorseWanderToCurrentLocation(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (horse == null || transform == null || transform.getPosition() == null) {
            return;
        }

        if (transform.getRotation() != null) {
            horse.saveLeashInformation(transform.getPosition(), transform.getRotation());
            return;
        }

        horse.getLeashPoint().assign(transform.getPosition());
    }

    public static void anchorHorseWanderToLocation(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef,
            double x,
            double y,
            double z
    ) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse == null) {
            return;
        }

        horse.getLeashPoint().assign(x, y, z);
    }

    public static void anchorHorseWanderToLocation(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef,
            double x,
            double y,
            double z,
            float pitch,
            float yaw
    ) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse == null) {
            return;
        }

        horse.saveLeashInformation(
                new Vector3d(x, y, z),
                new Vector3f(pitch, yaw, 0.0F)
        );
    }

    public static String describeHorseTarget(Store<EntityStore> store, Ref<EntityStore> targetEntity) {
        if (targetEntity == null || !targetEntity.isValid()) {
            return "invalid";
        }

        NPCEntity targetNpc = store.getComponent(targetEntity, NPCEntity.getComponentType());
        if (targetNpc == null) {
            return "not_npc";
        }

        EquippedSaddleComponent equippedSaddle = store.getComponent(targetEntity, EquippedSaddleComponent.getComponentType());
        boolean hasEquippedSaddle = equippedSaddle != null && !ItemStack.isEmpty(equippedSaddle.getSaddleStack());
        return targetNpc.getRoleName() + ",equipped=" + hasEquippedSaddle;
    }

    private static void refreshSaddledHorseTracking(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (plugin == null || plugin.getSaddleInputInterceptor() == null) {
            return;
        }

        plugin.getSaddleInputInterceptor().refreshSaddledHorseTarget(store, horseRef);
    }

    private static short resolveRestrictedHotbarSlot(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            short preferredHotbarSlot
    ) {
        if (preferredHotbarSlot >= 0 && preferredHotbarSlot < InventoryComponent.DEFAULT_HOTBAR_CAPACITY) {
            return preferredHotbarSlot;
        }

        return getActiveHotbarSlot(store, playerRef);
    }

    private static short getActiveHotbarSlot(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        if (store == null || playerRef == null || !playerRef.isValid()) {
            return InventoryComponent.INACTIVE_SLOT_INDEX;
        }

        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return InventoryComponent.INACTIVE_SLOT_INDEX;
        }

        return hotbar.getActiveSlot();
    }

    private static void registerRestrictedHotbarWindow(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Window window,
            short restrictedHotbarSlot
    ) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (store == null
                || playerRef == null
                || !playerRef.isValid()
                || window == null
                || plugin == null
                || plugin.getSaddleInputInterceptor() == null) {
            return;
        }

        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }

        plugin.getSaddleInputInterceptor().registerRestrictedHotbarWindow(
                player.getUuid(),
                window,
                restrictedHotbarSlot
        );
    }

    private static void clearRestrictedHotbarWindow(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (store == null
                || playerRef == null
                || !playerRef.isValid()
                || plugin == null
                || plugin.getSaddleInputInterceptor() == null) {
            return;
        }

        PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }

        plugin.getSaddleInputInterceptor().clearRestrictedHotbarWindow(player.getUuid());
    }
}
