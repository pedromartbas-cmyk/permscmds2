package com.example.permscmds;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(PermsCmdsMod.MODID)
public class PermsCmdsMod {
    public static final String MODID = "permscmds";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PermsCmdsMod() {
        Settings.load();
        PermissionManager.load();
        PlayerData.load();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new PermissionEvents());
        MinecraftForge.EVENT_BUS.register(new ExtraEvents());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CustomCommands.load();
        PermCommand.register(event.getDispatcher());
        UtilityCommands.register(event.getDispatcher());
        ExtraCommands.register(event.getDispatcher());
        AdminCommands.register(event.getDispatcher());
        WarpCommands.register(event.getDispatcher());
        ToolCommands.register(event.getDispatcher());
        KitCommands.register(event.getDispatcher());
        CustomCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        PermissionManager.save();
        PlayerData.save();
    }
}
