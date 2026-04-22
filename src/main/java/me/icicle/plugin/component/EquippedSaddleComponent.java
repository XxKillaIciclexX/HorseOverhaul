package me.icicle.plugin.component;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.icicle.plugin.HorseOverhaul;

public class EquippedSaddleComponent implements Component<EntityStore> {

    private static final KeyedCodec<ItemStack> SADDLE_STACK_CODEC = new KeyedCodec<>("SaddleStack", ItemStack.CODEC);

    public static final BuilderCodec<EquippedSaddleComponent> CODEC = BuilderCodec.builder(
            EquippedSaddleComponent.class,
            EquippedSaddleComponent::new
    ).addField(
            SADDLE_STACK_CODEC,
            EquippedSaddleComponent::setSaddleStack,
            EquippedSaddleComponent::getSaddleStack
    ).build();

    private ItemStack saddleStack = ItemStack.EMPTY;

    public EquippedSaddleComponent() {
    }

    public EquippedSaddleComponent(ItemStack saddleStack) {
        setSaddleStack(saddleStack);
    }

    public static ComponentType<EntityStore, EquippedSaddleComponent> getComponentType() {
        return HorseOverhaul.get().getEquippedSaddleComponentType();
    }

    public ItemStack getSaddleStack() {
        return saddleStack;
    }

    public void setSaddleStack(ItemStack saddleStack) {
        this.saddleStack = saddleStack == null ? ItemStack.EMPTY : saddleStack;
    }

    @Override
    public Component<EntityStore> clone() {
        return new EquippedSaddleComponent(saddleStack);
    }
}
