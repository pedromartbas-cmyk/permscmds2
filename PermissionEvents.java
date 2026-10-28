package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Locale;

public final class PermissionEvents {

    /**
     * Controla los comandos de jugadores con el nodo "command.<ruta>" (ej. command.gamemode.creative).
     * - Sin regla definida: se aplica el comportamiento normal de Minecraft (op/nivel).
     * - Regla "false": se bloquea.
     * - Regla "true": se permite, subiendo temporalmente el nivel solo lo necesario.
     */
    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack source = parse.getContext().getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        if (Settings.get().opsBypass && source.hasPermission(4)) return;

        String command = parse.getReader().getString();
        CommandDispatcher<CommandSourceStack> dispatcher = source.getServer().getCommands().getDispatcher();

        // Parseo "como si fuera op" para conocer la ruta real del comando aunque el jugador no pueda usarlo.
        ParseResults<CommandSourceStack> full = dispatcher.parse(command, source.withPermission(4));
        List<ParsedCommandNode<CommandSourceStack>> fullNodes = full.getContext().getNodes();
        if (fullNodes.isEmpty()) return;

        // Comandos desactivados con /disable: solo operadores.
        if (!Settings.get().disabledCommands.isEmpty() && !source.hasPermission(2)
                && Settings.get().disabledCommands.contains(rootLiteral(fullNodes))) {
            source.sendFailure(Text.color(Settings.get().noPermissionMessage));
            event.setCanceled(true);
            return;
        }

        Boolean allowed = PermissionManager.check(player.getUUID(), commandNode(fullNodes));
        if (allowed == null) return;

        if (!allowed) {
            source.sendFailure(Text.color(Settings.get().noPermissionMessage));
            event.setCanceled(true);
            return;
        }

        // Permitido. Si Minecraft ya lo deja ejecutar, no hay que tocar nada.
        if (parse.getContext().getNodes().size() >= fullNodes.size()) return;

        // Si no, usamos el nivel minimo que haga falta.
        for (int level = 1; level <= 4; level++) {
            ParseResults<CommandSourceStack> elevated = dispatcher.parse(command, source.withPermission(level));
            if (elevated.getContext().getNodes().size() >= fullNodes.size()) {
                event.setParseResults(elevated);
                return;
            }
        }
        event.setParseResults(full);
    }

    private static String rootLiteral(List<ParsedCommandNode<CommandSourceStack>> nodes) {
        for (ParsedCommandNode<CommandSourceStack> parsed : nodes) {
            if (parsed.getNode() instanceof LiteralCommandNode) {
                String name = parsed.getNode().getName().toLowerCase(Locale.ROOT);
                int i = name.indexOf(':');
                return i >= 0 ? name.substring(i + 1) : name;
            }
        }
        return "";
    }

    private static String commandNode(List<ParsedCommandNode<CommandSourceStack>> nodes) {
        StringBuilder sb = new StringBuilder("command");
        boolean first = true;
        for (ParsedCommandNode<CommandSourceStack> parsed : nodes) {
            if (!(parsed.getNode() instanceof LiteralCommandNode)) continue;
            String name = parsed.getNode().getName().toLowerCase(Locale.ROOT);
            if (first) {
                int i = name.indexOf(':'); // "minecraft:gamemode" -> "gamemode"
                if (i >= 0) name = name.substring(i + 1);
                first = false;
            }
            sb.append('.').append(name);
        }
        return sb.toString();
    }

    @SubscribeEvent
    public void onNameFormat(PlayerEvent.NameFormat event) {
        String prefix = PermissionManager.prefixFor(event.getEntity().getUUID());
        String nick = PlayerData.nickOf(event.getEntity().getUUID());
        if (prefix.isBlank() && nick == null) return;
        Component base = nick != null ? Text.color(nick) : event.getDisplayname();
        event.setDisplayname(Text.color(prefix).append(base));
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        PermissionManager.touch(event.getEntity().getUUID(), event.getEntity().getGameProfile().getName());
    }

    static void refreshAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.refreshDisplayName();
            p.refreshTabListName();
        }
    }
}
