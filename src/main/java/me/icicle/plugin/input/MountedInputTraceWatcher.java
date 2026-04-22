package me.icicle.plugin.input;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InventoryActionType;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.MouseButtonEvent;
import com.hypixel.hytale.protocol.MouseMotionEvent;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.WorldInteraction;
import com.hypixel.hytale.protocol.packets.entities.MountMovement;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interaction.DismountNPC;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.InventoryAction;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketWatcher;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.icicle.plugin.HorseOverhaul;

public class MountedInputTraceWatcher implements PlayerPacketWatcher {

    private static final long CLIENT_MOVEMENT_LOG_INTERVAL_MS = 1500L;

    private final ConcurrentMap<UUID, Long> lastMountedMovementLogMs = new ConcurrentHashMap<>();

    @Override
    public void accept(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null) {
            return;
        }

        if (packet instanceof SyncInteractionChains interactionChains) {
            logInteractionChains(playerRef, interactionChains);
            return;
        }

        if (packet instanceof MouseInteraction mouseInteraction) {
            logMouseInteraction(playerRef, mouseInteraction);
            return;
        }

        if (packet instanceof MountMovement mountMovement) {
            logMountMovement(playerRef, mountMovement);
            return;
        }

        if (packet instanceof ClientMovement clientMovement) {
            logMountedClientMovement(playerRef, clientMovement);
            return;
        }

        if (packet instanceof InventoryAction inventoryAction) {
            logInventoryAction(playerRef, inventoryAction);
            return;
        }

        if (packet instanceof MoveItemStack moveItemStack) {
            logMoveItemStack(playerRef, moveItemStack);
            return;
        }

        if (packet instanceof SmartMoveItemStack smartMoveItemStack) {
            logSmartMoveItemStack(playerRef, smartMoveItemStack);
            return;
        }

        if (packet instanceof DropItemStack dropItemStack) {
            logDropItemStack(playerRef, dropItemStack);
            return;
        }

        if (packet instanceof SetActiveSlot setActiveSlot) {
            logSetActiveSlot(playerRef, setActiveSlot);
            return;
        }

        if (packet instanceof ClientOpenWindow clientOpenWindow) {
            logClientOpenWindow(playerRef, clientOpenWindow);
            return;
        }

        if (packet instanceof CustomPageEvent customPageEvent) {
            logCustomPageEvent(playerRef, customPageEvent);
            return;
        }

        if (packet instanceof DismountNPC) {
            log(playerRef, "DismountNPC");
        }
    }

    private void logInteractionChains(PlayerRef playerRef, SyncInteractionChains interactionChains) {
        SyncInteractionChain[] updates = interactionChains.updates;
        if (updates == null || updates.length == 0) {
            return;
        }

        List<String> summaries = new ArrayList<>(updates.length);
        for (SyncInteractionChain chain : updates) {
            if (chain == null) {
                summaries.add("null");
                continue;
            }

            InteractionChainData data = chain.data;
            summaries.add(
                    "type=" + chain.interactionType
                            + ",initial=" + chain.initial
                            + ",item=" + chain.itemInHandId
                            + ",slot=" + chain.activeHotbarSlot
                            + ",entity=" + (data == null ? "none" : data.entityId)
                            + ",block=" + describeBlock(data == null ? null : data.blockPosition)
            );
        }

        log(playerRef, "SyncInteractionChains updates=" + summaries.size() + " [" + String.join(" | ", summaries) + "]");
    }

    private void logMouseInteraction(PlayerRef playerRef, MouseInteraction mouseInteraction) {
        WorldInteraction worldInteraction = mouseInteraction.worldInteraction;
        MouseButtonEvent mouseButton = mouseInteraction.mouseButton;
        MouseMotionEvent mouseMotion = mouseInteraction.mouseMotion;

        log(
                playerRef,
                "MouseInteraction activeSlot=" + mouseInteraction.activeSlot
                        + ",item=" + mouseInteraction.itemInHandId
                        + ",button=" + describeMouseButton(mouseButton)
                        + ",motion=" + describeMouseMotion(mouseMotion)
                        + ",entity=" + (worldInteraction == null ? "none" : worldInteraction.entityId)
                        + ",block=" + describeBlock(worldInteraction == null ? null : worldInteraction.blockPosition)
        );
    }

    private void logMountedClientMovement(PlayerRef playerRef, ClientMovement clientMovement) {
        if (clientMovement.mountedTo == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        UUID playerId = playerRef.getUuid();
        Long previousLogAt = lastMountedMovementLogMs.get(playerId);
        if (previousLogAt != null && now - previousLogAt < CLIENT_MOVEMENT_LOG_INTERVAL_MS) {
            return;
        }
        lastMountedMovementLogMs.put(playerId, now);

        log(
                playerRef,
                "ClientMovement mountedTo=" + clientMovement.mountedTo
                        + ",riderStates=" + describeMovementStates(clientMovement.riderMovementStates)
                        + ",wish=" + describePosition(clientMovement.wishMovement)
        );
    }

    private void logMountMovement(PlayerRef playerRef, MountMovement mountMovement) {
        log(
                playerRef,
                "MountMovement body=" + describeDirection(mountMovement.bodyOrientation)
                        + ",states=" + describeMovementStates(mountMovement.movementStates)
                        + ",position=" + describePosition(mountMovement.absolutePosition)
        );
    }

    private void logInventoryAction(PlayerRef playerRef, InventoryAction inventoryAction) {
        InventoryActionType actionType = inventoryAction.inventoryActionType;
        log(
                playerRef,
                "InventoryAction section=" + inventoryAction.inventorySectionId
                        + ",type=" + (actionType == null ? "none" : actionType)
                        + ",data=" + inventoryAction.actionData
        );
    }

    private void logMoveItemStack(PlayerRef playerRef, MoveItemStack moveItemStack) {
        log(
                playerRef,
                "MoveItemStack from=" + moveItemStack.fromSectionId + ":" + moveItemStack.fromSlotId
                        + ",to=" + moveItemStack.toSectionId + ":" + moveItemStack.toSlotId
                        + ",quantity=" + moveItemStack.quantity
        );
    }

    private void logSmartMoveItemStack(PlayerRef playerRef, SmartMoveItemStack smartMoveItemStack) {
        log(
                playerRef,
                "SmartMoveItemStack from=" + smartMoveItemStack.fromSectionId + ":" + smartMoveItemStack.fromSlotId
                        + ",quantity=" + smartMoveItemStack.quantity
                        + ",moveType=" + smartMoveItemStack.moveType
        );
    }

    private void logDropItemStack(PlayerRef playerRef, DropItemStack dropItemStack) {
        log(
                playerRef,
                "DropItemStack section=" + dropItemStack.inventorySectionId
                        + ",slot=" + dropItemStack.slotId
                        + ",quantity=" + dropItemStack.quantity
        );
    }

    private void logSetActiveSlot(PlayerRef playerRef, SetActiveSlot setActiveSlot) {
        log(
                playerRef,
                "SetActiveSlot section=" + setActiveSlot.inventorySectionId
                        + ",slot=" + setActiveSlot.activeSlot
        );
    }

    private void logClientOpenWindow(PlayerRef playerRef, ClientOpenWindow clientOpenWindow) {
        log(
                playerRef,
                "ClientOpenWindow type=" + (clientOpenWindow == null ? "none" : clientOpenWindow.type)
        );
    }

    private void logCustomPageEvent(PlayerRef playerRef, CustomPageEvent customPageEvent) {
        log(
                playerRef,
                "CustomPageEvent type=" + customPageEvent.type
                        + ",data=" + customPageEvent.data
        );
    }

    private void log(PlayerRef playerRef, String message) {
        HorseOverhaul plugin = HorseOverhaul.get();
        if (plugin == null) {
            return;
        }

        plugin.getLogger().atInfo().log(
                "[HorseOverhaul trace] player=%s (%s) %s",
                playerRef.getUsername(),
                playerRef.getUuid(),
                message
        );
    }

    private String describeMouseButton(MouseButtonEvent mouseButton) {
        if (mouseButton == null) {
            return "none";
        }

        return mouseButton.mouseButtonType + "/" + mouseButton.state + "/clicks=" + mouseButton.clicks;
    }

    private String describeMouseMotion(MouseMotionEvent mouseMotion) {
        if (mouseMotion == null) {
            return "none";
        }

        if (mouseMotion.relativeMotion == null) {
            return "relative=none";
        }

        return "relative=(" + mouseMotion.relativeMotion.x + "," + mouseMotion.relativeMotion.y + ")";
    }

    private String describeMovementStates(MovementStates movementStates) {
        if (movementStates == null) {
            return "none";
        }

        List<String> flags = new ArrayList<>(8);
        if (movementStates.idle) {
            flags.add("idle");
        }
        if (movementStates.walking) {
            flags.add("walking");
        }
        if (movementStates.running) {
            flags.add("running");
        }
        if (movementStates.sprinting) {
            flags.add("sprinting");
        }
        if (movementStates.jumping) {
            flags.add("jumping");
        }
        if (movementStates.crouching) {
            flags.add("crouching");
        }
        if (movementStates.mounting) {
            flags.add("mounting");
        }
        if (movementStates.onGround) {
            flags.add("onGround");
        }

        return flags.isEmpty() ? "no-flags" : String.join("+", flags);
    }

    private String describePosition(Position position) {
        if (position == null) {
            return "none";
        }

        return "(" + position.x + "," + position.y + "," + position.z + ")";
    }

    private String describeDirection(Direction direction) {
        if (direction == null) {
            return "none";
        }

        return "(pitch=" + direction.pitch + ",yaw=" + direction.yaw + ",roll=" + direction.roll + ")";
    }

    private String describeBlock(BlockPosition blockPosition) {
        if (blockPosition == null) {
            return "none";
        }

        return "(" + blockPosition.x + "," + blockPosition.y + "," + blockPosition.z + ")";
    }
}
