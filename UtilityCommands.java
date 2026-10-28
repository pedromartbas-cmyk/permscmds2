package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** /sethome /home /delhome /homes /spawn /setspawn /tpa /tpaccept /tpdeny */
public final class UtilityCommands {
    /** clave = jugador que recibe la solicitud */
    private static final Map<UUID, TpaRequest> TPA = new HashMap<>();

    /** Ultima posicion de cada jugador antes de teletransportarse o morir (solo en memoria). */
    private static final Map<UUID, PlayerData.Loc> BACK = new HashMap<>();

    private record TpaRequest(UUID from, long expiresAt, boolean here) {}

    /** Guarda la posicion actual del jugador para /back. */
    public static void remember(ServerPlayer p) {
        BACK.put(p.getUUID(), PlayerData.Loc.of(p));
    }

    private static final SuggestionProvider<CommandSourceStack> HOME_NAMES = (ctx, builder) -> {
        if (ctx.getSource().getEntity() instanceof ServerPlayer p) {
            return SharedSuggestionProvider.suggest(homesOf(p).keySet(), builder);
        }
        return Suggestions.empty();
    };

    private UtilityCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("sethome")
                .executes(c -> setHome(c, "home"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(c -> setHome(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("home")
                .executes(c -> home(c, null))
                .then(Commands.argument("name", StringArgumentType.word()).suggests(HOME_NAMES)
                        .executes(c -> home(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("delhome")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(HOME_NAMES)
                        .executes(c -> delHome(c, StringArgumentType.getString(c, "name")))));

        d.register(Commands.literal("homes").executes(UtilityCommands::homes));
        d.register(Commands.literal("spawn").executes(UtilityCommands::spawn));
        d.register(Commands.literal("setspawn").executes(UtilityCommands::setSpawn));
        d.register(Commands.literal("back").executes(UtilityCommands::back));

        d.register(Commands.literal("tpa")
                .then(Commands.argument("player", EntityArgument.player()).executes(UtilityCommands::tpa)));
        d.register(Commands.literal("tpahere")
                .then(Commands.argument("player", EntityArgument.player()).executes(UtilityCommands::tpaHere)));
        d.register(Commands.literal("tpaccept").executes(UtilityCommands::tpAccept));
        d.register(Commands.literal("tpdeny").executes(UtilityCommands::tpDeny));
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, PlayerData.Loc> homesOf(ServerPlayer p) {
        return PlayerData.get().homes.computeIfAbsent(p.getUUID().toString(), k -> new TreeMap<>());
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Text.color("&c" + msg));
        return 0;
    }

    private static int maxHomes(CommandSourceStack source, ServerPlayer p) {
        if (Settings.get().opsBypass && source.hasPermission(4)) return Integer.MAX_VALUE;
        if (Boolean.TRUE.equals(PermissionManager.check(p.getUUID(), "permscmds.homes.unlimited"))) return Integer.MAX_VALUE;
        for (int n = 100; n >= 1; n--) {
            if (Boolean.TRUE.equals(PermissionManager.check(p.getUUID(), "permscmds.homes." + n))) return n;
        }
        int def = Settings.get().defaultMaxHomes;
        return def < 0 ? Integer.MAX_VALUE : def;
    }

    static boolean teleport(ServerPlayer p, PlayerData.Loc l) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(l.dimension));
            ServerLevel level = p.server.getLevel(key);
            if (level == null) return false;
            remember(p);
            p.teleportTo(level, l.x, l.y, l.z, l.yaw, l.pitch);
            return true;
        } catch (Exception e) {
            PermsCmdsMod.LOGGER.warn("Teletransporte fallido: {}", e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------ homes

    private static int setHome(CommandContext<CommandSourceStack> c, String rawName) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.sethome", true)) return 0;
        String name = rawName.toLowerCase(Locale.ROOT);
        Map<String, PlayerData.Loc> homes = homesOf(p);
        int max = maxHomes(s, p);
        if (!homes.containsKey(name) && homes.size() >= max) {
            return fail(c, "Has alcanzado el maximo de homes (" + max + "). Borra uno con /delhome.");
        }
        homes.put(name, PlayerData.Loc.of(p));
        PlayerData.save();
        s.sendSuccess(() -> Text.color("&aHome '" + name + "' guardado."), false);
        return 1;
    }

    private static int home(CommandContext<CommandSourceStack> c, String rawName) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.home", true)) return 0;
        Map<String, PlayerData.Loc> homes = homesOf(p);
        if (homes.isEmpty()) return fail(c, "No tienes ningun home. Usa /sethome.");

        String name;
        if (rawName != null) name = rawName.toLowerCase(Locale.ROOT);
        else if (homes.containsKey("home")) name = "home";
        else if (homes.size() == 1) name = homes.keySet().iterator().next();
        else return fail(c, "Indica cual: " + String.join(", ", homes.keySet()));

        PlayerData.Loc loc = homes.get(name);
        if (loc == null) return fail(c, "No existe el home '" + name + "'. Tus homes: " + String.join(", ", homes.keySet()));
        if (!teleport(p, loc)) return fail(c, "La dimension de ese home ya no esta disponible.");
        s.sendSuccess(() -> Text.color("&aTeletransportado a '" + name + "'."), false);
        return 1;
    }

    private static int delHome(CommandContext<CommandSourceStack> c, String rawName) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.sethome", true)) return 0;
        String name = rawName.toLowerCase(Locale.ROOT);
        if (homesOf(p).remove(name) == null) return fail(c, "No existe el home '" + name + "'.");
        PlayerData.save();
        c.getSource().sendSuccess(() -> Text.color("&aHome '" + name + "' borrado."), false);
        return 1;
    }

    private static int homes(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.home", true)) return 0;
        Map<String, PlayerData.Loc> homes = homesOf(p);
        String list = homes.isEmpty() ? "(ninguno)" : String.join(", ", homes.keySet());
        c.getSource().sendSuccess(() -> Text.color("&eHomes: &f" + list), false);
        return 1;
    }

    // ------------------------------------------------------------ spawn

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.spawn", true)) return 0;

        PlayerData.Loc loc = PlayerData.get().spawn;
        if (loc == null) {
            BlockPos pos = p.server.overworld().getSharedSpawnPos();
            loc = new PlayerData.Loc();
            loc.dimension = "minecraft:overworld";
            loc.x = pos.getX() + 0.5;
            loc.y = pos.getY();
            loc.z = pos.getZ() + 0.5;
        }
        if (!teleport(p, loc)) return fail(c, "No se pudo ir al spawn.");
        s.sendSuccess(() -> Text.color("&aTeletransportado al spawn."), false);
        return 1;
    }

    private static int setSpawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.setspawn", false)) return 0;
        PlayerData.get().spawn = PlayerData.Loc.of(p);
        PlayerData.save();
        c.getSource().sendSuccess(() -> Text.color("&aSpawn establecido en tu posicion."), false);
        return 1;
    }

    // ------------------------------------------------------------ back

    private static int back(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.back", true)) return 0;
        PlayerData.Loc loc = BACK.get(p.getUUID());
        if (loc == null) return fail(c, "No tienes ninguna posicion anterior guardada.");
        // teleport() guarda la posicion actual, asi que /back otra vez te devuelve aqui.
        if (!teleport(p, loc)) return fail(c, "No se pudo volver a esa posicion.");
        s.sendSuccess(() -> Text.color("&aHas vuelto a tu posicion anterior."), false);
        return 1;
    }

    // ------------------------------------------------------------ tpa

    private static MutableComponent button(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(
                Style.EMPTY.withColor(color).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int tpa(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer from = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.tpa", true)) return 0;
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        if (to.getUUID().equals(from.getUUID())) return fail(c, "No puedes enviarte una solicitud a ti mismo.");

        long expires = System.currentTimeMillis() + Settings.get().tpaTimeoutSeconds * 1000L;
        TPA.put(to.getUUID(), new TpaRequest(from.getUUID(), expires, false));

        String fromName = from.getGameProfile().getName();
        String toName = to.getGameProfile().getName();
        s.sendSuccess(() -> Text.color("&aSolicitud enviada a " + toName + "."), false);
        to.sendSystemMessage(Text.color("&e" + fromName + " quiere teletransportarse a ti. ")
                .append(button("[Aceptar]", ChatFormatting.GREEN, "/tpaccept"))
                .append(" ")
                .append(button("[Rechazar]", ChatFormatting.RED, "/tpdeny")));
        return 1;
    }

    private static int tpaHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer from = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.tpa", true)) return 0;
        ServerPlayer to = EntityArgument.getPlayer(c, "player");
        if (to.getUUID().equals(from.getUUID())) return fail(c, "No puedes enviarte una solicitud a ti mismo.");

        long expires = System.currentTimeMillis() + Settings.get().tpaTimeoutSeconds * 1000L;
        TPA.put(to.getUUID(), new TpaRequest(from.getUUID(), expires, true));

        String fromName = from.getGameProfile().getName();
        String toName = to.getGameProfile().getName();
        s.sendSuccess(() -> Text.color("&aSolicitud enviada a " + toName + "."), false);
        to.sendSystemMessage(Text.color("&e" + fromName + " quiere que te teletransportes a el. ")
                .append(button("[Aceptar]", ChatFormatting.GREEN, "/tpaccept"))
                .append(" ")
                .append(button("[Rechazar]", ChatFormatting.RED, "/tpdeny")));
        return 1;
    }

    private static int tpAccept(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer me = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.tpa", true)) return 0;
        TpaRequest r = TPA.remove(me.getUUID());
        if (r == null || r.expiresAt() < System.currentTimeMillis()) return fail(c, "No tienes solicitudes pendientes.");
        ServerPlayer from = s.getServer().getPlayerList().getPlayer(r.from());
        if (from == null) return fail(c, "Ese jugador ya no esta conectado.");

        if (r.here()) {
            // El que acepta va hasta quien envio /tpahere.
            remember(me);
            me.teleportTo(from.serverLevel(), from.getX(), from.getY(), from.getZ(), from.getYRot(), from.getXRot());
        } else {
            remember(from);
            from.teleportTo(me.serverLevel(), me.getX(), me.getY(), me.getZ(), me.getYRot(), me.getXRot());
        }
        from.sendSystemMessage(Text.color("&a" + me.getGameProfile().getName() + " acepto tu solicitud."));
        s.sendSuccess(() -> Text.color("&aSolicitud aceptada."), false);
        return 1;
    }

    private static int tpDeny(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer me = s.getPlayerOrException();
        TpaRequest r = TPA.remove(me.getUUID());
        if (r == null || r.expiresAt() < System.currentTimeMillis()) return fail(c, "No tienes solicitudes pendientes.");
        ServerPlayer from = s.getServer().getPlayerList().getPlayer(r.from());
        if (from != null) from.sendSystemMessage(Text.color("&c" + me.getGameProfile().getName() + " rechazo tu solicitud."));
        s.sendSuccess(() -> Text.color("&eSolicitud rechazada."), false);
        return 1;
    }
}
