package me.icicle.plugin.input;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.builtin.mounts.MountedByComponent;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import com.hypixel.hytale.protocol.packets.entities.MountMovement;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import me.icicle.plugin.HorseOverhaul;
import me.icicle.plugin.component.EquippedSaddleComponent;
import me.icicle.plugin.saddle.SaddleActions;

public class SaddleInputInterceptor implements PlayerPacketFilter {

    private static final long LEASH_SYNC_INTERVAL_MS = 250L;
    private static final long TRACKED_ANCHOR_REFRESH_INTERVAL_MS = 500L;
    private static final double MAX_UNMOUNTED_DRIFT_DISTANCE = 0.35D;
    private static final long[] DISMOUNT_LEASH_REFRESH_DELAYS_MS = {0L, 75L, 200L, 500L};
    private static final long PRIMARY_INTERACTION_SUPPRESSION_MS = 400L;

    private final ConcurrentMap<UUID, Boolean> mountedCrouchState = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastMountedLeashSyncMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Ref<EntityStore>> lastMountedHorseRefs = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TrackedHorseAnchor> trackedHorseAnchors = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Boolean> saddledHorseTargetKeys = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Boolean> primedSaddledHorseStores = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SuppressedPrimaryInteraction> suppressedPrimaryInteractions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, RestrictedHotbarWindow> restrictedHotbarWindows = new ConcurrentHashMap<>();
    private final AtomicLong restrictedHotbarWindowIds = new AtomicLong();
    private final ScheduledExecutorService delayedAnchorExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "HorseOverhaul-DismountAnchor");
            thread.setDaemon(true);
            return thread;
        }
    });

    public SaddleInputInterceptor() {
        delayedAnchorExecutor.scheduleAtFixedRate(
                this::refreshTrackedHorseAnchors,
                TRACKED_ANCHOR_REFRESH_INTERVAL_MS,
                TRACKED_ANCHOR_REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        if (packet instanceof MountMovement mountMovement) {
            handleMountMovement(playerRef, mountMovement);
            return false;
        }

        if (packet instanceof DismountNPC) {
            handleDismount(playerRef);
            return false;
        }

        if (packet instanceof ClientOpenWindow clientOpenWindow) {
            return handleMountedHorseWindowOpen(playerRef, clientOpenWindow);
        }

        if (packet instanceof SetActiveSlot setActiveSlot) {
            trackRestrictedHotbarSelection(playerRef, setActiveSlot);
            return false;
        }

        if (packet instanceof MoveItemStack moveItemStack) {
            return shouldBlockRestrictedHotbarMove(playerRef, moveItemStack);
        }

        if (!(packet instanceof SyncInteractionChains interactionChains)) {
            return false;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return false;
        }

        SyncInteractionChain[] updates = interactionChains.updates;
        if (updates == null || updates.length == 0) {
            return false;
        }

        ensureSaddledHorseTargetsPrimed(playerEntityRef.getStore());

        StartedSaddledHorsePrimary startedSaddledHorsePrimary =
                findStartedSaddledHorsePrimaryTarget(playerRef, playerEntityRef.getStore(), updates);
        int startedSaddledHorsePrimaryTargetId =
                startedSaddledHorsePrimary == null ? -1 : startedSaddledHorsePrimary.targetEntityId;
        if (startedSaddledHorsePrimaryTargetId > 0) {
            suppressSaddledHorsePrimary(playerRef.getUuid(), startedSaddledHorsePrimaryTargetId);
            scheduleOpenHorseInventory(
                    playerRef,
                    startedSaddledHorsePrimaryTargetId,
                    startedSaddledHorsePrimary.activeHotbarSlot
            );
        }

        List<SyncInteractionChain> keep = new ArrayList<>(updates.length);
        boolean handled = startedSaddledHorsePrimaryTargetId > 0;
        for (SyncInteractionChain chain : updates) {
            if (shouldHandleSaddleUse(chain)) {
                handled = true;
                scheduleOpenInventory(playerRef, (short) chain.activeHotbarSlot);
                continue;
            }

            if (shouldHandleSaddleSecondary(chain)) {
                handled = true;
                scheduleEquipSaddle(playerRef, (short) chain.activeHotbarSlot, chain.data.entityId);
                continue;
            }

            if (shouldSuppressSaddledHorsePrimary(playerRef, playerEntityRef.getStore(), chain, startedSaddledHorsePrimaryTargetId)) {
                handled = true;
                continue;
            }

            if (shouldInspectUnknownPrimaryTarget(playerEntityRef.getStore(), chain)) {
                scheduleInspectPrimaryTarget(playerRef, chain.data.entityId, (short) chain.activeHotbarSlot);
            }

            keep.add(chain);
        }

        if (!handled) {
            return false;
        }

        if (keep.isEmpty()) {
            return true;
        }

        interactionChains.updates = keep.toArray(new SyncInteractionChain[0]);
        return false;
    }

    private void handleMountMovement(PlayerRef playerRef, MountMovement mountMovement) {
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            clearMountedTracking(playerId);
            return;
        }

        boolean isCrouching = mountMovement != null
                && mountMovement.movementStates != null
                && mountMovement.movementStates.crouching;

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> handleMountMovementOnWorldThread(playerId, playerEntityRef, isCrouching));
    }

    private void handleDismount(PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            clearMountedTracking(playerId);
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> handleDismountOnWorldThread(playerId, playerEntityRef));
    }

    private boolean shouldHandleSaddleUse(SyncInteractionChain chain) {
        return chain != null
                && chain.initial
                && chain.interactionType == InteractionType.Use
                && chain.activeHotbarSlot >= 0
                && SaddleActions.SADDLE_ITEM_ID.equals(chain.itemInHandId);
    }

    private boolean shouldHandleSaddleSecondary(SyncInteractionChain chain) {
        return chain != null
                && chain.initial
                && chain.interactionType == InteractionType.Secondary
                && chain.data != null
                && chain.data.entityId > 0
                && chain.activeHotbarSlot >= 0
                && SaddleActions.SADDLE_ITEM_ID.equals(chain.itemInHandId);
    }

    private boolean shouldHandleSaddledHorsePrimary(Store<EntityStore> store, SyncInteractionChain chain) {
        return chain != null
                && chain.initial
                && chain.interactionType == InteractionType.Primary
                && chain.data != null
                && chain.data.entityId > 0
                && isTrackedSaddledHorseTarget(store, chain.data.entityId);
    }

    private boolean shouldInspectUnknownPrimaryTarget(Store<EntityStore> store, SyncInteractionChain chain) {
        return chain != null
                && chain.initial
                && chain.interactionType == InteractionType.Primary
                && chain.data != null
                && chain.data.entityId > 0
                && !isTrackedSaddledHorseTarget(store, chain.data.entityId);
    }

    private StartedSaddledHorsePrimary findStartedSaddledHorsePrimaryTarget(
            PlayerRef playerRef,
            Store<EntityStore> store,
            SyncInteractionChain[] updates
    ) {
        if (playerRef == null || updates == null) {
            return null;
        }

        clearExpiredPrimarySuppression(playerRef.getUuid());
        for (SyncInteractionChain chain : updates) {
            if (shouldHandleSaddledHorsePrimary(store, chain)) {
                return new StartedSaddledHorsePrimary(chain.data.entityId, (short) chain.activeHotbarSlot);
            }
        }

        return null;
    }

    private boolean shouldSuppressSaddledHorsePrimary(
            PlayerRef playerRef,
            Store<EntityStore> store,
            SyncInteractionChain chain,
            int startedTargetEntityId
    ) {
        if (chain == null
                || chain.interactionType != InteractionType.Primary
                || chain.data == null
                || chain.data.entityId <= 0) {
            return false;
        }

        if (startedTargetEntityId > 0 && chain.data.entityId == startedTargetEntityId) {
            return true;
        }

        if (playerRef == null) {
            return false;
        }

        SuppressedPrimaryInteraction suppressedPrimaryInteraction = suppressedPrimaryInteractions.get(playerRef.getUuid());
        if (suppressedPrimaryInteraction == null || suppressedPrimaryInteraction.isExpired()) {
            suppressedPrimaryInteractions.remove(playerRef.getUuid(), suppressedPrimaryInteraction);
            return false;
        }

        return suppressedPrimaryInteraction.targetEntityId == chain.data.entityId
                && isTrackedSaddledHorseTarget(store, chain.data.entityId);
    }

    private void scheduleOpenInventory(PlayerRef playerRef, short hotbarSlot) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> SaddleActions.openSaddleInventory(store, playerEntityRef, hotbarSlot));
    }

    private void scheduleOpenHorseInventory(PlayerRef playerRef, int targetEntityId, short activeHotbarSlot) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> targetEntity = store.getExternalData().getRefFromNetworkId(targetEntityId);
            if (targetEntity == null || !targetEntity.isValid()) {
                saddledHorseTargetKeys.remove(buildSaddledHorseTargetKey(store, targetEntityId));
                return;
            }

            if (SaddleActions.getMountedSaddledHorse(store, playerEntityRef) != null) {
                logPrimaryTargetResolution(playerRef, targetEntityId, store, targetEntity, true, false, true);
                return;
            }

            if (!SaddleActions.hasEquippedSaddle(store, targetEntity)) {
                refreshSaddledHorseTargetOnWorldThread(store, targetEntity);
                logPrimaryTargetResolution(playerRef, targetEntityId, store, targetEntity, true, false, false);
                return;
            }

            refreshSaddledHorseTargetOnWorldThread(store, targetEntity);
            boolean opened = SaddleActions.openHorseInventory(
                    store,
                    playerEntityRef,
                    targetEntity,
                    activeHotbarSlot
            );
            logPrimaryTargetResolution(playerRef, targetEntityId, store, targetEntity, true, opened, false);
        });
    }

    private void scheduleInspectPrimaryTarget(PlayerRef playerRef, int targetEntityId, short activeHotbarSlot) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> targetEntity = store.getExternalData().getRefFromNetworkId(targetEntityId);
            if (targetEntity == null || !targetEntity.isValid()) {
                return;
            }

            boolean isSaddledHorse = SaddleActions.isSaddledHorse(store, targetEntity);
            if (isSaddledHorse) {
                refreshSaddledHorseTargetOnWorldThread(store, targetEntity);
            }

            if (isSaddledHorse && SaddleActions.getMountedSaddledHorse(store, playerEntityRef) == null) {
                boolean opened = SaddleActions.openHorseInventory(
                        store,
                        playerEntityRef,
                        targetEntity,
                        activeHotbarSlot
                );
                logPrimaryTargetResolution(playerRef, targetEntityId, store, targetEntity, false, opened, false);
                return;
            }

            logPrimaryTargetResolution(playerRef, targetEntityId, store, targetEntity, false, false, false);
        });
    }

    private void scheduleOpenMountedSaddleInventory(Ref<EntityStore> playerEntityRef) {
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> SaddleActions.openMountedSaddleInventory(store, playerEntityRef));
    }

    private void scheduleOpenMountedHorseInventory(Ref<EntityStore> playerEntityRef) {
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> SaddleActions.openMountedHorseInventory(store, playerEntityRef));
    }

    public void registerRestrictedHotbarWindow(UUID playerId, Window window, short restrictedHotbarSlot) {
        if (playerId == null || window == null) {
            return;
        }

        long windowRegistrationId = restrictedHotbarWindowIds.incrementAndGet();
        restrictedHotbarWindows.put(
                playerId,
                new RestrictedHotbarWindow(windowRegistrationId, restrictedHotbarSlot)
        );
        window.registerCloseEvent(event -> clearRestrictedHotbarWindow(playerId, windowRegistrationId));
    }

    public void clearRestrictedHotbarWindow(UUID playerId) {
        if (playerId == null) {
            return;
        }

        restrictedHotbarWindows.remove(playerId);
    }

    private void scheduleEquipSaddle(PlayerRef playerRef, short hotbarSlot, int targetEntityId) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            Ref<EntityStore> targetEntity = store.getExternalData().getRefFromNetworkId(targetEntityId);
            if (targetEntity == null || !targetEntity.isValid()) {
                return;
            }

            SaddleActions.equipSaddleOnHorse(store, playerEntityRef, hotbarSlot, targetEntity);
        });
    }

    public void primeSaddledHorseTargets(World world) {
        if (world == null) {
            return;
        }

        world.execute(() -> rebuildSaddledHorseTargetCache(world.getEntityStore().getStore()));
    }

    public void refreshSaddledHorseTarget(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return;
        }

        if (store.isInThread()) {
            refreshSaddledHorseTargetOnWorldThread(store, horseRef);
            return;
        }

        World world = store.getExternalData().getWorld();
        world.execute(() -> refreshSaddledHorseTargetOnWorldThread(store, horseRef));
    }

    private void syncMountedHorseAnchorIfDue(
            UUID playerId,
            Store<EntityStore> store,
            Ref<EntityStore> mountedHorseRef
    ) {
        long now = System.currentTimeMillis();
        Long previousSyncAt = lastMountedLeashSyncMs.get(playerId);
        if (previousSyncAt != null && now - previousSyncAt < LEASH_SYNC_INTERVAL_MS) {
            return;
        }

        lastMountedLeashSyncMs.put(playerId, now);
        scheduleTrackedHorseAnchorPass(store, mountedHorseRef);
    }

    private void handleMountMovementOnWorldThread(
            UUID playerId,
            Ref<EntityStore> playerEntityRef,
            boolean isCrouching
    ) {
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            clearMountedTracking(playerId);
            return;
        }

        Store<EntityStore> store = playerEntityRef.getStore();
        Ref<EntityStore> mountedHorseRef = SaddleActions.getMountedSaddledHorse(store, playerEntityRef);
        if (mountedHorseRef == null) {
            clearMountedTracking(playerId);
            return;
        }

        lastMountedHorseRefs.put(playerId, mountedHorseRef);
        syncMountedHorseAnchorIfDue(playerId, store, mountedHorseRef);

        boolean wasCrouching = mountedCrouchState.getOrDefault(playerId, false);
        mountedCrouchState.put(playerId, isCrouching);

        if (isCrouching && !wasCrouching) {
            scheduleOpenMountedSaddleInventory(playerEntityRef);
        }
    }

    private void handleDismountOnWorldThread(UUID playerId, Ref<EntityStore> playerEntityRef) {
        mountedCrouchState.remove(playerId);
        lastMountedLeashSyncMs.remove(playerId);

        Store<EntityStore> store = playerEntityRef == null ? null : playerEntityRef.getStore();
        Ref<EntityStore> mountedHorseRef = lastMountedHorseRefs.remove(playerId);
        if ((mountedHorseRef == null || !mountedHorseRef.isValid())
                && store != null
                && playerEntityRef != null
                && playerEntityRef.isValid()) {
            mountedHorseRef = SaddleActions.getMountedSaddledHorse(store, playerEntityRef);
        }

        if (store == null
                || mountedHorseRef == null
                || !mountedHorseRef.isValid()
                || !SaddleActions.hasEquippedSaddle(store, mountedHorseRef)) {
            return;
        }

        rememberHorseAnchor(store, mountedHorseRef);
        scheduleDismountAnchorRefreshes(store, mountedHorseRef);
    }

    private void clearMountedTracking(UUID playerId) {
        mountedCrouchState.remove(playerId);
        lastMountedLeashSyncMs.remove(playerId);
        lastMountedHorseRefs.remove(playerId);
    }

    private void trackRestrictedHotbarSelection(PlayerRef playerRef, SetActiveSlot setActiveSlot) {
        if (playerRef == null
                || setActiveSlot == null
                || setActiveSlot.inventorySectionId != InventoryComponent.HOTBAR_SECTION_ID) {
            return;
        }

        restrictedHotbarWindows.computeIfPresent(
                playerRef.getUuid(),
                (ignored, restrictedHotbarWindow) -> restrictedHotbarWindow.withSlot((short) setActiveSlot.activeSlot)
        );
    }

    private boolean shouldBlockRestrictedHotbarMove(PlayerRef playerRef, MoveItemStack moveItemStack) {
        if (playerRef == null || moveItemStack == null) {
            return false;
        }

        RestrictedHotbarWindow restrictedHotbarWindow = restrictedHotbarWindows.get(playerRef.getUuid());
        if (restrictedHotbarWindow == null) {
            return false;
        }

        boolean windowToActiveHotbar = moveItemStack.fromSectionId >= 0
                && moveItemStack.toSectionId == InventoryComponent.HOTBAR_SECTION_ID
                && moveItemStack.toSlotId == restrictedHotbarWindow.restrictedSlot;
        boolean activeHotbarToWindow = moveItemStack.fromSectionId == InventoryComponent.HOTBAR_SECTION_ID
                && moveItemStack.fromSlotId == restrictedHotbarWindow.restrictedSlot
                && moveItemStack.toSectionId >= 0;
        return windowToActiveHotbar || activeHotbarToWindow;
    }

    private boolean handleMountedHorseWindowOpen(PlayerRef playerRef, ClientOpenWindow clientOpenWindow) {
        if (!shouldHandleMountedHorseWindowOpen(playerRef, clientOpenWindow)) {
            return false;
        }

        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        if (playerEntityRef == null || !playerEntityRef.isValid()) {
            return false;
        }

        scheduleOpenMountedHorseInventory(playerEntityRef);
        return true;
    }

    private boolean shouldHandleMountedHorseWindowOpen(PlayerRef playerRef, ClientOpenWindow clientOpenWindow) {
        return playerRef != null
                && clientOpenWindow != null
                && clientOpenWindow.type == WindowType.PocketCrafting
                && lastMountedHorseRefs.containsKey(playerRef.getUuid());
    }

    private void suppressSaddledHorsePrimary(UUID playerId, int targetEntityId) {
        if (playerId == null || targetEntityId <= 0) {
            return;
        }

        suppressedPrimaryInteractions.put(
                playerId,
                new SuppressedPrimaryInteraction(
                        targetEntityId,
                        System.currentTimeMillis() + PRIMARY_INTERACTION_SUPPRESSION_MS
                )
        );
    }

    private void clearExpiredPrimarySuppression(UUID playerId) {
        if (playerId == null) {
            return;
        }

        SuppressedPrimaryInteraction suppressedPrimaryInteraction = suppressedPrimaryInteractions.get(playerId);
        if (suppressedPrimaryInteraction != null && suppressedPrimaryInteraction.isExpired()) {
            suppressedPrimaryInteractions.remove(playerId, suppressedPrimaryInteraction);
        }
    }

    private void ensureSaddledHorseTargetsPrimed(Store<EntityStore> store) {
        if (store == null) {
            return;
        }

        int storeIndex = store.getStoreIndex();
        if (primedSaddledHorseStores.putIfAbsent(storeIndex, Boolean.TRUE) != null) {
            return;
        }

        World world = store.getExternalData().getWorld();
        world.execute(() -> rebuildSaddledHorseTargetCache(store));
    }

    private void rebuildSaddledHorseTargetCache(Store<EntityStore> store) {
        int storeIndex = store.getStoreIndex();
        for (Long targetKey : saddledHorseTargetKeys.keySet()) {
            if ((int) (targetKey >>> 32) == storeIndex) {
                saddledHorseTargetKeys.remove(targetKey);
            }
        }

        store.forEachChunk((BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>) (chunk, ignored) ->
                collectSaddledHorseTargets(store, chunk)
        );
        primedSaddledHorseStores.put(storeIndex, Boolean.TRUE);
    }

    private void collectSaddledHorseTargets(Store<EntityStore> store, ArchetypeChunk<EntityStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            NPCEntity horse = chunk.getComponent(index, NPCEntity.getComponentType());
            EquippedSaddleComponent equippedSaddle = chunk.getComponent(index, EquippedSaddleComponent.getComponentType());
            if (horse == null || !isTrackedSaddledHorse(horse, equippedSaddle)) {
                continue;
            }

            int networkId = horse.getNetworkId();
            if (networkId > 0) {
                saddledHorseTargetKeys.put(buildSaddledHorseTargetKey(store, networkId), Boolean.TRUE);
            }
        }
    }

    private void refreshSaddledHorseTargetOnWorldThread(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (horse == null || horse.getNetworkId() <= 0) {
            return;
        }

        long targetKey = buildSaddledHorseTargetKey(store, horse.getNetworkId());
        if (SaddleActions.isSaddledHorse(store, horseRef)) {
            saddledHorseTargetKeys.put(targetKey, Boolean.TRUE);
        } else {
            saddledHorseTargetKeys.remove(targetKey);
        }
        primedSaddledHorseStores.put(store.getStoreIndex(), Boolean.TRUE);
    }

    private boolean isTrackedSaddledHorseTarget(Store<EntityStore> store, int networkId) {
        return store != null && networkId > 0
                && saddledHorseTargetKeys.containsKey(buildSaddledHorseTargetKey(store, networkId));
    }

    private long buildSaddledHorseTargetKey(Store<EntityStore> store, int networkId) {
        return ((long) store.getStoreIndex() << 32) | (networkId & 0xffffffffL);
    }

    private boolean isTrackedSaddledHorse(NPCEntity horse, EquippedSaddleComponent equippedSaddle) {
        return horse != null
                && ("Horse_Overhaul_Saddled".equals(horse.getRoleName())
                || (equippedSaddle != null && !ItemStack.isEmpty(equippedSaddle.getSaddleStack())));
    }

    private void logPrimaryTargetResolution(
            PlayerRef playerRef,
            int targetEntityId,
            Store<EntityStore> store,
            Ref<EntityStore> targetEntity,
            boolean fromCache,
            boolean opened,
            boolean blockedMounted
    ) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (plugin == null || playerRef == null) {
            return;
        }

        plugin.getLogger().atInfo().log(
                "[HorseOverhaul primary] player=%s entity=%s cached=%s opened=%s blockedMounted=%s target=%s",
                playerRef.getUsername(),
                targetEntityId,
                fromCache,
                opened,
                blockedMounted,
                SaddleActions.describeHorseTarget(store, targetEntity)
        );
    }

    private void scheduleTrackedHorseAnchorPass(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef
    ) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        World world = store.getExternalData().getWorld();
        world.execute(() -> refreshTrackedHorseAnchor(store, horseRef));
    }

    private void scheduleDismountAnchorRefreshes(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        if (horseRef == null || !horseRef.isValid()) {
            return;
        }

        for (long delayMs : DISMOUNT_LEASH_REFRESH_DELAYS_MS) {
            delayedAnchorExecutor.schedule(
                    () -> {
                        if (horseRef.isValid()) {
                            scheduleTrackedHorseAnchorPass(store, horseRef);
                        }
                    },
                    delayMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void rememberHorseAnchor(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        if (store == null || horseRef == null || !horseRef.isValid()) {
            return;
        }

        scheduleTrackedHorseAnchorPass(store, horseRef);
    }

    private void refreshTrackedHorseAnchors() {
        for (TrackedHorseAnchor trackedHorseAnchor : trackedHorseAnchors.values()) {
            if (!trackedHorseAnchor.horseRef.isValid()) {
                trackedHorseAnchors.remove(trackedHorseAnchor.key);
                continue;
            }

            scheduleTrackedHorseAnchorPass(trackedHorseAnchor.store, trackedHorseAnchor.horseRef);
        }
    }

    private void refreshTrackedHorseAnchor(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        String horseAnchorKey = getHorseAnchorKey(store, horseRef);
        if (!horseRef.isValid() || !SaddleActions.hasEquippedSaddle(store, horseRef)) {
            trackedHorseAnchors.remove(horseAnchorKey);
            return;
        }

        if (isHorseMounted(store, horseRef)) {
            TrackedHorseAnchor trackedHorseAnchor = captureCurrentHorseAnchor(store, horseRef, horseAnchorKey);
            if (trackedHorseAnchor != null) {
                trackedHorseAnchors.put(horseAnchorKey, trackedHorseAnchor);
                SaddleActions.anchorHorseWanderToLocation(
                        store,
                        horseRef,
                        trackedHorseAnchor.positionX,
                        trackedHorseAnchor.positionY,
                        trackedHorseAnchor.positionZ,
                        trackedHorseAnchor.pitch,
                        trackedHorseAnchor.yaw
                );
            }
            return;
        }

        TrackedHorseAnchor trackedHorseAnchor = trackedHorseAnchors.get(horseAnchorKey);
        if (trackedHorseAnchor == null) {
            trackedHorseAnchor = captureCurrentHorseAnchor(store, horseRef, horseAnchorKey);
            if (trackedHorseAnchor == null) {
                return;
            }
            trackedHorseAnchors.put(horseAnchorKey, trackedHorseAnchor);
        }

        SaddleActions.anchorHorseWanderToLocation(
                store,
                horseRef,
                trackedHorseAnchor.positionX,
                trackedHorseAnchor.positionY,
                trackedHorseAnchor.positionZ,
                trackedHorseAnchor.pitch,
                trackedHorseAnchor.yaw
        );
        snapHorseToTrackedAnchorIfNeeded(store, horseRef, trackedHorseAnchor);
    }

    private TrackedHorseAnchor captureCurrentHorseAnchor(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef,
            String horseAnchorKey
    ) {
        TransformComponent transform = store.getComponent(horseRef, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null || transform.getRotation() == null) {
            return null;
        }

        Vector3d position = transform.getPosition();
        Vector3f rotation = transform.getRotation();
        return new TrackedHorseAnchor(
                horseAnchorKey,
                store,
                horseRef,
                position.x,
                position.y,
                position.z,
                rotation.getPitch(),
                rotation.getYaw(),
                rotation.getRoll()
        );
    }

    private boolean isHorseMounted(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        MountedByComponent mountedByComponent = store.getComponent(horseRef, MountedByComponent.getComponentType());
        return mountedByComponent != null && !mountedByComponent.getPassengers().isEmpty();
    }

    private void snapHorseToTrackedAnchorIfNeeded(
            Store<EntityStore> store,
            Ref<EntityStore> horseRef,
            TrackedHorseAnchor trackedHorseAnchor
    ) {
        TransformComponent transform = store.getComponent(horseRef, TransformComponent.getComponentType());
        NPCEntity horse = store.getComponent(horseRef, NPCEntity.getComponentType());
        if (transform == null || transform.getPosition() == null || horse == null) {
            return;
        }

        if (transform.getPosition().distanceSquaredTo(
                trackedHorseAnchor.positionX,
                trackedHorseAnchor.positionY,
                trackedHorseAnchor.positionZ
        ) <= MAX_UNMOUNTED_DRIFT_DISTANCE * MAX_UNMOUNTED_DRIFT_DISTANCE) {
            return;
        }

        horse.moveTo(
                horseRef,
                trackedHorseAnchor.positionX,
                trackedHorseAnchor.positionY,
                trackedHorseAnchor.positionZ,
                store
        );
        if (transform.getRotation() != null) {
            transform.getRotation().assign(
                    trackedHorseAnchor.pitch,
                    trackedHorseAnchor.yaw,
                    trackedHorseAnchor.roll
            );
        }
    }

    private String getHorseAnchorKey(Store<EntityStore> store, Ref<EntityStore> horseRef) {
        return store.getStoreIndex() + ":" + horseRef.getIndex();
    }

    public void shutdown() {
        lastMountedHorseRefs.clear();
        trackedHorseAnchors.clear();
        saddledHorseTargetKeys.clear();
        primedSaddledHorseStores.clear();
        suppressedPrimaryInteractions.clear();
        restrictedHotbarWindows.clear();
        delayedAnchorExecutor.shutdownNow();
    }

    private void clearRestrictedHotbarWindow(UUID playerId, long windowRegistrationId) {
        if (playerId == null) {
            return;
        }

        restrictedHotbarWindows.computeIfPresent(
                playerId,
                (ignored, restrictedHotbarWindow) ->
                        restrictedHotbarWindow.windowRegistrationId == windowRegistrationId ? null : restrictedHotbarWindow
        );
    }

    private final class TrackedHorseAnchor {

        private final String key;
        private final Store<EntityStore> store;
        private final Ref<EntityStore> horseRef;
        private final double positionX;
        private final double positionY;
        private final double positionZ;
        private final float pitch;
        private final float yaw;
        private final float roll;

        private TrackedHorseAnchor(
                String key,
                Store<EntityStore> store,
                Ref<EntityStore> horseRef,
                double positionX,
                double positionY,
                double positionZ,
                float pitch,
                float yaw,
                float roll
        ) {
            this.key = key;
            this.store = store;
            this.horseRef = horseRef;
            this.positionX = positionX;
            this.positionY = positionY;
            this.positionZ = positionZ;
            this.pitch = pitch;
            this.yaw = yaw;
            this.roll = roll;
        }
    }

    private static final class SuppressedPrimaryInteraction {

        private final int targetEntityId;
        private final long expiresAtMs;

        private SuppressedPrimaryInteraction(int targetEntityId, long expiresAtMs) {
            this.targetEntityId = targetEntityId;
            this.expiresAtMs = expiresAtMs;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    private static final class StartedSaddledHorsePrimary {

        private final int targetEntityId;
        private final short activeHotbarSlot;

        private StartedSaddledHorsePrimary(int targetEntityId, short activeHotbarSlot) {
            this.targetEntityId = targetEntityId;
            this.activeHotbarSlot = activeHotbarSlot;
        }
    }

    private static final class RestrictedHotbarWindow {

        private final long windowRegistrationId;
        private final short restrictedSlot;

        private RestrictedHotbarWindow(long windowRegistrationId, short restrictedSlot) {
            this.windowRegistrationId = windowRegistrationId;
            this.restrictedSlot = restrictedSlot;
        }

        private RestrictedHotbarWindow withSlot(short updatedRestrictedSlot) {
            return new RestrictedHotbarWindow(windowRegistrationId, updatedRestrictedSlot);
        }
    }
}
