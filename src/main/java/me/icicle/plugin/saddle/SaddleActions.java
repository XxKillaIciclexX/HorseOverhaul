package me.icicle.plugin.saddle;

import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.EmptyExtraInfo;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
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
import org.bson.BsonDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class SaddleActions {

    public static final String SADDLE_ITEM_ID = "Horse_Saddle";
    private static final String LOCKED_SLOT_ITEM_ID = "Horse_Locked_Slot";

    private static final short HORSE_SADDLE_SLOT_CAPACITY = 1;
    private static final short HORSE_GEAR_SLOT_INDEX = 0;
    private static final short HORSE_BAG_SLOT_START = 9;
    private static final String HORSE_ROLE_NAME = "Horse";
    private static final String TAMED_HORSE_ROLE_NAME = "Tamed_Horse";
    private static final String SADDLED_HORSE_ROLE_NAME = "Horse_Overhaul_Saddled";
    private static final String PETTABLE_SADDLED_HORSE_ROLE_NAME = "Horse_Overhaul_Saddled_Pettable";
    private static final long MOUNTED_UNSADDLE_FINALIZE_DELAY_MS = 75L;
    private static final int MAX_MOUNTED_UNSADDLE_FINALIZE_ATTEMPTS = 8;
    private static final ScheduledExecutorService mountedUnsaddleExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "HorseOverhaul-MountedUnsaddle");
            thread.setDaemon(true);
            return thread;
        }
    });

    private SaddleActions() {
    }

    public static void shutdown() {
        mountedUnsaddleExecutor.shutdownNow();
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

        ItemStackItemContainer saddleContainer = ensureSaddleContainerCapacity(
                hotbar,
                hotbarSlot,
                getConfiguredSaddleSlotCount()
        );
        if (saddleContainer == null) {
            return false;
        }

        SaddleBagWindow saddleWindow = new SaddleBagWindow(
                saddleContainer,
                getConfiguredSaddleSlotCount(),
                getConfiguredSaddleRowCount()
        );
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

        ItemStackItemContainer persistedContainer = ensureSaddleContainerCapacity(
                saddleSlotContainer,
                (short) 0,
                getConfiguredSaddleSlotCount()
        );
        if (persistedContainer == null) {
            return false;
        }

        SaddleBagWindow saddleWindow = new SaddleBagWindow(
                persistedContainer,
                getConfiguredSaddleSlotCount(),
                getConfiguredSaddleRowCount()
        );
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

        HorseInventoryWindow horseInventoryWindow = new HorseInventoryWindow(
                horseInventoryContainer,
                targetEntity,
                getConfiguredHorseInventorySlotCount(),
                getConfiguredHorseInventoryRowCount()
        );
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
        return openMountedHorseInventory(store, playerRef, mountedHorseRef);
    }

    public static boolean openMountedHorseInventory(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> mountedHorseRef
    ) {
        if (playerRef == null || !playerRef.isValid() || mountedHorseRef == null || !mountedHorseRef.isValid()) {
            return false;
        }

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || !isSaddledHorse(store, mountedHorseRef)) {
            return false;
        }

        // Replace the local PocketCrafting page with the mounted horse inventory.
        player.getWindowManager().closeAllWindows(playerRef, store);
        player.getPageManager().setPage(playerRef, store, Page.None, true);
        return openHorseInventory(
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

        int saddledHorseRoleIndex = NPCPlugin.get().getIndex(getConfiguredSaddledHorseRoleName());
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

        return isSaddledHorseRoleName(targetNpc.getRoleName()) || hasEquippedSaddle(store, targetEntity);
    }

    public static boolean isSaddledHorseRoleName(String roleName) {
        return SADDLED_HORSE_ROLE_NAME.equals(roleName)
                || PETTABLE_SADDLED_HORSE_ROLE_NAME.equals(roleName);
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

        SimpleItemContainer horseInventoryContainer = new SimpleItemContainer(getConfiguredHorseInventorySlotCount());
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

        if (!isSaddledHorseRoleName(targetNpc.getRoleName())) {
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
        if (ItemStack.isEmpty(updatedSaddleStack)) {
            clearHorseSaddle(store, playerRef, horseRef);
            return;
        }

        store.putComponent(
                horseRef,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(updatedSaddleStack)
        );
        refreshSaddledHorseTracking(store, horseRef);
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

        ItemStackItemContainer saddleBagContainer = ensureSaddleContainerCapacity(
                horseInventoryContainer,
                HORSE_GEAR_SLOT_INDEX,
                getConfiguredSaddleSlotCount()
        );
        if (saddleBagContainer == null) {
            clearHorseBagSlots(horseInventoryContainer);
            return;
        }

        for (short slot = 0; slot < getConfiguredSaddleSlotCount(); slot++) {
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

        ItemStackItemContainer saddleBagContainer = ensureSaddleContainerCapacity(
                horseInventoryContainer,
                HORSE_GEAR_SLOT_INDEX,
                getConfiguredSaddleSlotCount()
        );
        if (saddleBagContainer == null) {
            return;
        }

        for (short slot = 0; slot < getConfiguredSaddleSlotCount(); slot++) {
            saddleBagContainer.setItemStackForSlot(
                    slot,
                    horseInventoryContainer.getItemStack((short) (HORSE_BAG_SLOT_START + slot))
            );
        }
    }

    private static void clearHorseBagSlots(SimpleItemContainer horseInventoryContainer) {
        for (short slot = 0; slot < getConfiguredSaddleSlotCount(); slot++) {
            horseInventoryContainer.setItemStackForSlot((short) (HORSE_BAG_SLOT_START + slot), ItemStack.EMPTY);
        }
    }

    private static ItemStackItemContainer ensureSaddleContainerCapacity(
            ItemContainer parentContainer,
            short slot,
            short capacity
    ) {
        ItemStack itemStack = parentContainer.getItemStack(slot);
        if (ItemStack.isEmpty(itemStack)) {
            return null;
        }

        ItemStackItemContainer existingContainer = ItemStackItemContainer.getContainer(parentContainer, slot);
        if (existingContainer != null && existingContainer.getCapacity() == capacity) {
            return existingContainer;
        }

        if (existingContainer == null) {
            return ItemStackItemContainer.ensureContainer(parentContainer, slot, capacity);
        }

        ItemStack[] resizedItems = new ItemStack[capacity];
        short slotsToCopy = (short) Math.min(existingContainer.getCapacity(), capacity);
        for (short itemSlot = 0; itemSlot < capacity; itemSlot++) {
            resizedItems[itemSlot] = itemSlot < slotsToCopy
                    ? normalizeItemStack(existingContainer.getItemStack(itemSlot))
                    : ItemStack.EMPTY;
        }

        BsonDocument metadata = itemStack.getMetadata() == null
                ? new BsonDocument()
                : itemStack.getMetadata().clone();
        BsonDocument containerMetadata = itemStack.getFromMetadataOrNull(ItemStackItemContainer.CONTAINER_CODEC);
        BsonDocument updatedContainerMetadata = containerMetadata == null
                ? new BsonDocument()
                : containerMetadata.clone();
        ItemStackItemContainer.CONTAINER_CODEC.put(metadata, updatedContainerMetadata, EmptyExtraInfo.EMPTY);
        ItemStackItemContainer.CAPACITY_CODEC.put(updatedContainerMetadata, capacity, EmptyExtraInfo.EMPTY);
        ItemStackItemContainer.ITEMS_CODEC.put(updatedContainerMetadata, resizedItems, EmptyExtraInfo.EMPTY);
        parentContainer.setItemStackForSlot(slot, itemStack.withMetadata(metadata));
        return ItemStackItemContainer.getContainer(parentContainer, slot);
    }

    private static void clearHorseSaddle(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef,
            Ref<EntityStore> horseRef
    ) {
        boolean wasMounted = dismountHorsePassengers(store, horseRef);
        if (wasMounted) {
            closePlayerInventoryView(store, playerRef);
            scheduleFinalizeHorseUnsaddle(store, horseRef, 0);
            return;
        }

        finalizeHorseUnsaddle(store, horseRef);
        closePlayerInventoryView(store, playerRef);
    }

    private static void finalizeHorseUnsaddle(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef
    ) {
        forceClearHorseMountState(store, horseRef);
        store.putComponent(
                horseRef,
                EquippedSaddleComponent.getComponentType(),
                new EquippedSaddleComponent(ItemStack.EMPTY)
        );
        refreshSaddledHorseTracking(store, horseRef);

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse == null) {
            return;
        }

        Role currentRole = horse.getRole();
        if (currentRole == null || currentRole.isRoleChangeRequested()) {
            return;
        }

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

    private static void scheduleFinalizeHorseUnsaddle(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef,
            int attempt
    ) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return;
        }

        mountedUnsaddleExecutor.schedule(
                () -> store.getExternalData().getWorld().execute(() -> {
                    if (horseRef == null || !horseRef.isValid()) {
                        return;
                    }

                    if (hasHorsePassengers(store, horseRef) && attempt < MAX_MOUNTED_UNSADDLE_FINALIZE_ATTEMPTS) {
                        scheduleFinalizeHorseUnsaddle(store, horseRef, attempt + 1);
                        return;
                    }

                    finalizeHorseUnsaddle(store, horseRef);
                }),
                MOUNTED_UNSADDLE_FINALIZE_DELAY_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private static boolean dismountHorsePassengers(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return false;
        }

        MountedByComponent mountedByComponent = store.getComponent(horseRef, MountedByComponent.getComponentType());
        if (mountedByComponent == null || mountedByComponent.getPassengers().isEmpty()) {
            return false;
        }

        NPCMountComponent npcMountComponent = store.getComponent(horseRef, NPCMountComponent.getComponentType());
        HorseOverhaul plugin = HorseOverhaul.get();
        List<Ref<EntityStore>> passengers = new ArrayList<>(mountedByComponent.getPassengers());
        boolean hadPassengers = false;
        for (Ref<EntityStore> passengerRef : passengers) {
            if (passengerRef == null || !passengerRef.isValid()) {
                mountedByComponent.removePassenger(passengerRef);
                continue;
            }

            hadPassengers = true;

            Player mountedPlayer = store.getComponent(passengerRef, Player.getComponentType());
            if (mountedPlayer != null) {
                if (npcMountComponent != null && mountedPlayer.getMountEntityId() != 0) {
                    MountPlugin.checkDismountNpc(store, passengerRef, mountedPlayer);
                    npcMountComponent = store.getComponent(horseRef, NPCMountComponent.getComponentType());
                } else {
                    mountedPlayer.setMountEntityId(0);
                    MountPlugin.resetOriginalPlayerMovementSettings(passengerRef, store);
                    mountedByComponent.removePassenger(passengerRef);
                    store.tryRemoveComponent(passengerRef, MountedComponent.getComponentType());
                }
            } else {
                mountedByComponent.removePassenger(passengerRef);
                store.tryRemoveComponent(passengerRef, MountedComponent.getComponentType());
            }

            PlayerRef mountedPlayerRef = store.getComponent(passengerRef, PlayerRef.getComponentType());
            if (plugin != null
                    && plugin.getSaddleInputInterceptor() != null
                    && mountedPlayerRef != null) {
                plugin.getSaddleInputInterceptor().clearMountedTracking(mountedPlayerRef.getUuid());
            }
        }

        return hadPassengers;
    }

    private static void forceClearHorseMountState(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return;
        }

        HorseOverhaul plugin = HorseOverhaul.get();
        MountedByComponent mountedByComponent = store.getComponent(horseRef, MountedByComponent.getComponentType());
        if (mountedByComponent != null) {
            List<Ref<EntityStore>> passengers = new ArrayList<>(mountedByComponent.getPassengers());
            for (Ref<EntityStore> passengerRef : passengers) {
                mountedByComponent.removePassenger(passengerRef);
                if (passengerRef == null || !passengerRef.isValid()) {
                    continue;
                }

                store.tryRemoveComponent(passengerRef, MountedComponent.getComponentType());

                Player mountedPlayer = store.getComponent(passengerRef, Player.getComponentType());
                if (mountedPlayer != null) {
                    mountedPlayer.setMountEntityId(0);
                }

                PlayerRef mountedPlayerRef = store.getComponent(passengerRef, PlayerRef.getComponentType());
                if (plugin != null
                        && plugin.getSaddleInputInterceptor() != null
                        && mountedPlayerRef != null) {
                    plugin.getSaddleInputInterceptor().clearMountedTracking(mountedPlayerRef.getUuid());
                }
            }

            if (mountedByComponent.getPassengers().isEmpty()) {
                store.removeComponentIfExists(horseRef, MountedByComponent.getComponentType());
            }
        }

        store.removeComponentIfExists(horseRef, NPCMountComponent.getComponentType());
    }

    private static boolean hasHorsePassengers(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef
    ) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return false;
        }

        MountedByComponent mountedByComponent = store.getComponent(horseRef, MountedByComponent.getComponentType());
        return mountedByComponent != null && !mountedByComponent.getPassengers().isEmpty();
    }

    private static short getConfiguredSaddleSlotCount() {
        HorseOverhaul plugin = HorseOverhaul.get();
        return plugin != null && plugin.getHorseOverhaulConfig() != null
                ? plugin.getHorseOverhaulConfig().getSaddleStorageSlots()
                : HORSE_BAG_SLOT_START;
    }

    private static short getConfiguredSaddleRowCount() {
        HorseOverhaul plugin = HorseOverhaul.get();
        return plugin != null && plugin.getHorseOverhaulConfig() != null
                ? plugin.getHorseOverhaulConfig().getSaddleStorageRows()
                : 1;
    }

    private static short getConfiguredHorseInventorySlotCount() {
        HorseOverhaul plugin = HorseOverhaul.get();
        return plugin != null && plugin.getHorseOverhaulConfig() != null
                ? plugin.getHorseOverhaulConfig().getHorseInventorySlots()
                : (short) (HORSE_BAG_SLOT_START + getConfiguredSaddleSlotCount());
    }

    private static short getConfiguredHorseInventoryRowCount() {
        HorseOverhaul plugin = HorseOverhaul.get();
        return plugin != null && plugin.getHorseOverhaulConfig() != null
                ? plugin.getHorseOverhaulConfig().getHorseInventoryRows()
                : (short) (1 + getConfiguredSaddleRowCount());
    }

    private static String getConfiguredSaddledHorseRoleName() {
        HorseOverhaul plugin = HorseOverhaul.get();
        return plugin != null
                && plugin.getHorseOverhaulConfig() != null
                && plugin.getHorseOverhaulConfig().isSaddledHorsePettingEnabled()
                ? PETTABLE_SADDLED_HORSE_ROLE_NAME
                : SADDLED_HORSE_ROLE_NAME;
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
        if (mountedComponent != null) {
            Ref<EntityStore> mountedHorseRef = mountedComponent.getMountedToEntity();
            if (mountedHorseRef != null && mountedHorseRef.isValid()) {
                NPCEntity mountedHorse = store.getComponent(mountedHorseRef, NPCEntity.getComponentType());
                if (mountedHorse != null && isSaddledHorse(store, mountedHorseRef)) {
                    return mountedHorseRef;
                }
            }
        }

        return findMountedSaddledHorseByPassenger(store, playerRef);
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
                new Rotation3f(pitch, yaw, 0.0F)
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

    public static void syncConfiguredSaddledHorseRoles(com.hypixel.hytale.server.core.universe.world.World world) {
        if (world == null) {
            return;
        }

        world.execute(() -> {
            Store<EntityStore> store = world.getEntityStore().getStore();
            String configuredRoleName = getConfiguredSaddledHorseRoleName();
            int configuredRoleIndex = NPCPlugin.get().getIndex(configuredRoleName);
            if (configuredRoleIndex < 0) {
                return;
            }

            store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    NPCEntity horse = chunk.getComponent(index, NPCEntity.getComponentType());
                    EquippedSaddleComponent equippedSaddle = chunk.getComponent(index, EquippedSaddleComponent.getComponentType());
                    if (horse == null
                            || equippedSaddle == null
                            || ItemStack.isEmpty(equippedSaddle.getSaddleStack())
                            || configuredRoleName.equals(horse.getRoleName())) {
                        continue;
                    }

                    Role currentRole = horse.getRole();
                    if (currentRole == null || currentRole.isRoleChangeRequested()) {
                        continue;
                    }

                    Ref<EntityStore> horseRef = chunk.getReferenceTo(index);
                    if (horseRef == null || !horseRef.isValid()) {
                        continue;
                    }

                    RoleChangeSystem.requestRoleChange(
                            horseRef,
                            currentRole,
                            configuredRoleIndex,
                            true,
                            store
                    );
                }
            });
        });
    }

    private static void refreshSaddledHorseTracking(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (plugin == null || plugin.getSaddleInputInterceptor() == null) {
            return;
        }

        plugin.getSaddleInputInterceptor().refreshSaddledHorseTarget(store, horseRef);
    }

    private static Ref<EntityStore> findMountedSaddledHorseByPassenger(
            Store<EntityStore> store,
            Ref<EntityStore> playerRef
    ) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] result = new Ref[1];

        store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> ignored) -> {
            if (result[0] != null) {
                return;
            }

            for (int index = 0; index < chunk.size(); index++) {
                NPCEntity horse = chunk.getComponent(index, NPCEntity.getComponentType());
                MountedByComponent mountedBy = chunk.getComponent(index, MountedByComponent.getComponentType());
                if (horse == null || mountedBy == null || !containsPassenger(mountedBy.getPassengers(), playerRef)) {
                    continue;
                }

                Ref<EntityStore> horseRef = chunk.getReferenceTo(index);
                if (horseRef != null && horseRef.isValid() && isSaddledHorse(store, horseRef)) {
                    result[0] = horseRef;
                    return;
                }
            }
        });

        return result[0];
    }

    private static boolean containsPassenger(List<Ref<EntityStore>> passengers, Ref<EntityStore> playerRef) {
        if (passengers == null || passengers.isEmpty() || playerRef == null) {
            return false;
        }

        for (Ref<EntityStore> passenger : passengers) {
            if (passenger != null
                    && passenger.getStore() == playerRef.getStore()
                    && passenger.getIndex() == playerRef.getIndex()) {
                return true;
            }
        }

        return false;
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
