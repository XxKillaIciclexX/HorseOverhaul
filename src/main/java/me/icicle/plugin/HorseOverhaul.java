package me.icicle.plugin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.command.system.CommandRegistration;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.icicle.plugin.command.HorseOverhaulHelpCommand;
import me.icicle.plugin.component.EquippedSaddleComponent;
import me.icicle.plugin.input.MountedInputTraceWatcher;
import me.icicle.plugin.input.SaddleInputInterceptor;
import me.icicle.plugin.interaction.EquipSaddleOnHorseInteraction;
import me.icicle.plugin.interaction.OpenSaddleInventoryInteraction;
import me.icicle.plugin.saddle.SaddleActions;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class HorseOverhaul extends JavaPlugin {

    private static HorseOverhaul instance;

    private ComponentType<EntityStore, EquippedSaddleComponent> equippedSaddleComponentType;
    private PacketFilter saddleUsePacketFilter;
    private PacketFilter mountedInputTracePacketFilter;
    private CommandRegistration horseOverhaulHelpCommandRegistration;
    private SaddleInputInterceptor saddleInputInterceptor;

    public HorseOverhaul(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    public static HorseOverhaul get() {
        return instance;
    }

    public ComponentType<EntityStore, EquippedSaddleComponent> getEquippedSaddleComponentType() {
        return equippedSaddleComponentType;
    }

    public SaddleInputInterceptor getSaddleInputInterceptor() {
        return saddleInputInterceptor;
    }

    @Override
    protected void setup() {
        super.setup();
        instance = this;
        equippedSaddleComponentType = getEntityStoreRegistry().registerComponent(
                EquippedSaddleComponent.class,
                "horse_overhaul_equipped_saddle",
                EquippedSaddleComponent.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                "horse_overhaul_open_saddle_inventory",
                OpenSaddleInventoryInteraction.class,
                OpenSaddleInventoryInteraction.CODEC
        );
        this.getCodecRegistry(Interaction.CODEC).register(
                "horse_overhaul_equip_saddle_on_horse",
                EquipSaddleOnHorseInteraction.class,
                EquipSaddleOnHorseInteraction.CODEC
        );

        saddleInputInterceptor = new SaddleInputInterceptor();
        saddleUsePacketFilter = PacketAdapters.registerInbound((com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter) saddleInputInterceptor);
        mountedInputTracePacketFilter = PacketAdapters.registerInbound(new MountedInputTraceWatcher());
        horseOverhaulHelpCommandRegistration = getCommandRegistry().registerCommand(new HorseOverhaulHelpCommand());
    }

    @Override
    protected void start() {
        super.start();
        Universe universe = Universe.get();
        if (universe == null || saddleInputInterceptor == null) {
            return;
        }

        universe.getUniverseReady().thenRun(() ->
                universe.getWorlds().values().forEach(saddleInputInterceptor::primeSaddledHorseTargets)
        );
    }

    @Override
    protected void shutdown() {
        SaddleActions.shutdown();
        if (saddleInputInterceptor != null) {
            saddleInputInterceptor.shutdown();
            saddleInputInterceptor = null;
        }
        if (saddleUsePacketFilter != null) {
            PacketAdapters.deregisterInbound(saddleUsePacketFilter);
            saddleUsePacketFilter = null;
        }
        if (mountedInputTracePacketFilter != null) {
            PacketAdapters.deregisterInbound(mountedInputTracePacketFilter);
            mountedInputTracePacketFilter = null;
        }
        if (horseOverhaulHelpCommandRegistration != null) {
            horseOverhaulHelpCommandRegistration.unregister();
            horseOverhaulHelpCommandRegistration = null;
        }
        super.shutdown();
    }
}
