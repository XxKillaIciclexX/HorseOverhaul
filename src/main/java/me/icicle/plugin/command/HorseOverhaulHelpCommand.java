package me.icicle.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.icicle.plugin.ui.HorseOverhaulHelpPage;

public class HorseOverhaulHelpCommand extends AbstractPlayerCommand {

    public HorseOverhaulHelpCommand() {
        super("horseoverhaul", "Open the Horse Overhaul help window");
        setPermissionGroups(
                GameMode.Adventure.toString(),
                GameMode.Creative.toString()
        );
    }

    @Override
    protected void execute(
            CommandContext commandContext,
            Store<EntityStore> store,
            Ref<EntityStore> playerEntityRef,
            PlayerRef playerRef,
            World world
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.getPageManager().openCustomPage(
                playerEntityRef,
                store,
                new HorseOverhaulHelpPage(playerRef)
        );
    }
}
