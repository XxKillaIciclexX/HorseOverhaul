package me.icicle.plugin.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.icicle.plugin.saddle.SaddleActions;

public class OpenSaddleInventoryInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<OpenSaddleInventoryInteraction> CODEC = BuilderCodec.builder(
            OpenSaddleInventoryInteraction.class,
            OpenSaddleInventoryInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(InteractionType interactionType, InteractionContext interactionContext, CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        ItemStack heldItemStack = interactionContext.getHeldItem();
        if (heldItemStack == null || heldItemStack.isEmpty()) {
            return;
        }

        byte heldItemSlot = interactionContext.getHeldItemSlot();
        if (heldItemSlot < 0) {
            return;
        }

        if (interactionType != InteractionType.Use) {
            return;
        }

        SaddleActions.openSaddleInventory(commandBuffer.getExternalData().getStore(), ref, (short) heldItemSlot);
    }
}
