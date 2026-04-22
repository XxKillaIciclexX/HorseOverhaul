package me.icicle.plugin.ui;

import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public class HorseOverhaulHelpPage extends BasicCustomUIPage {
    public HorseOverhaulHelpPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(UICommandBuilder ui) {
        ui.append("Pages/HorseOverhaulHelp.ui");
    }
}
