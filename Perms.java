package com.example.permscmds;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

final class Perms {
    private Perms() {}

    /**
     * Consola y bloques de comandos (sin entidad) siempre pasan. Jugadores: ops nivel 4 pasan
     * (si opsBypass), el resto segun sus permisos; {@code def} se usa si el nodo no esta definido.
     */
    static boolean allowed(CommandSourceStack source, String node, boolean def) {
        if (source.getEntity() == null) return true;
        if (!(source.getEntity() instanceof ServerPlayer player)) return false;
        if (Settings.get().opsBypass && source.hasPermission(4)) return true;
        return PermissionManager.has(player.getUUID(), node, def);
    }

    static boolean require(CommandContext<CommandSourceStack> ctx, String node, boolean def) {
        if (allowed(ctx.getSource(), node, def)) return true;
        ctx.getSource().sendFailure(Text.color(Settings.get().noPermissionMessage));
        return false;
    }
}
