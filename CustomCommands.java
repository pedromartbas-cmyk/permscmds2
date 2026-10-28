package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Comandos definidos en config/permscmds/custom_commands.json.
 * Cada uno puede mostrar mensajes y/o ejecutar otros comandos (como el jugador o como consola).
 * Placeholder disponible: {player}.
 */
public final class CustomCommands {

    public static class Entry {
        public String name = "";
        /** Nodo de permiso. Vacio = todos pueden usarlo. */
        public String permission = "";
        /** "player" o "console" */
        public String runAs = "player";
        public List<String> messages = new ArrayList<>();
        /** Comandos a ejecutar, sin la barra inicial. */
        public List<String> commands = new ArrayList<>();
    }

    public static class Data {
        public List<Entry> commands = new ArrayList<>();
    }

    private static Data data = new Data();

    private CustomCommands() {}

    private static Data defaults() {
        Data d = new Data();
        Entry e = new Entry();
        e.name = "reglas";
        e.messages.add("&6=== Reglas del servidor ===");
        e.messages.add("&f1. Respeta a los demas jugadores");
        e.messages.add("&f2. Nada de griefing ni robos");
        d.commands.add(e);
        return d;
    }

    public static void load() {
        data = JsonStore.load("custom_commands.json", Data.class, CustomCommands::defaults);
        if (data.commands == null) data.commands = new ArrayList<>();
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (Entry e : data.commands) {
            if (e == null || e.name == null || !e.name.matches("[a-z0-9_]+")) {
                PermsCmdsMod.LOGGER.warn("Comando personalizado ignorado (nombre invalido, usa a-z 0-9 _): {}", e == null ? null : e.name);
                continue;
            }
            if (dispatcher.getRoot().getChild(e.name) != null) {
                PermsCmdsMod.LOGGER.warn("Comando personalizado '{}' ignorado: ya existe un comando con ese nombre.", e.name);
                continue;
            }
            if (e.messages == null) e.messages = new ArrayList<>();
            if (e.commands == null) e.commands = new ArrayList<>();
            dispatcher.register(Commands.literal(e.name).executes(c -> run(c, e)));
        }
    }

    private static int run(CommandContext<CommandSourceStack> c, Entry e) {
        CommandSourceStack s = c.getSource();
        if (e.permission != null && !e.permission.isBlank()
                && !Perms.allowed(s, e.permission.toLowerCase(Locale.ROOT), false)) {
            s.sendFailure(Text.color(Settings.get().noPermissionMessage));
            return 0;
        }
        String player = s.getTextName();
        for (String m : e.messages) {
            s.sendSuccess(() -> Text.color(m.replace("{player}", player)), false);
        }
        CommandSourceStack exec = "console".equalsIgnoreCase(e.runAs) ? s.getServer().createCommandSourceStack() : s;
        for (String line : e.commands) {
            s.getServer().getCommands().performPrefixedCommand(exec, line.replace("{player}", player));
        }
        return 1;
    }
}
