package com.example.permscmds;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** /perm: administracion de grupos y permisos. */
public final class PermCommand {
    private static final SuggestionProvider<CommandSourceStack> GROUPS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(PermissionManager.groupNames(), builder);

    private PermCommand() {}

    private static boolean canAdmin(CommandSourceStack s) {
        if (s.hasPermission(4)) return true;
        return s.getEntity() instanceof ServerPlayer p && PermissionManager.has(p.getUUID(), "permscmds.admin", false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("perm")
                .requires(PermCommand::canAdmin)
                .then(Commands.literal("groups").executes(PermCommand::groups))
                .then(Commands.literal("creategroup")
                        .then(Commands.argument("name", StringArgumentType.word()).executes(PermCommand::createGroup)))
                .then(Commands.literal("deletegroup")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(GROUPS)
                                .executes(PermCommand::deleteGroup)))
                .then(Commands.literal("group")
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(GROUPS)
                                .then(Commands.literal("info").executes(PermCommand::groupInfo))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("permission", StringArgumentType.greedyString())
                                                .executes(PermCommand::groupAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("permission", StringArgumentType.greedyString())
                                                .executes(PermCommand::groupRemove)))
                                .then(Commands.literal("parent")
                                        .then(Commands.literal("add")
                                                .then(Commands.argument("parent", StringArgumentType.word()).suggests(GROUPS)
                                                        .executes(PermCommand::parentAdd)))
                                        .then(Commands.literal("remove")
                                                .then(Commands.argument("parent", StringArgumentType.word()).suggests(GROUPS)
                                                        .executes(PermCommand::parentRemove))))
                                .then(Commands.literal("prefix")
                                        .then(Commands.argument("prefix", StringArgumentType.greedyString())
                                                .executes(PermCommand::groupPrefix)))
                                .then(Commands.literal("weight")
                                        .then(Commands.argument("weight", IntegerArgumentType.integer(-1000, 1000))
                                                .executes(PermCommand::groupWeight)))))
                .then(Commands.literal("user")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.literal("info").executes(PermCommand::userInfo))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("permission", StringArgumentType.greedyString())
                                                .executes(PermCommand::userAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("permission", StringArgumentType.greedyString())
                                                .executes(PermCommand::userRemove)))
                                .then(Commands.literal("setgroup")
                                        .then(Commands.argument("group", StringArgumentType.word()).suggests(GROUPS)
                                                .executes(PermCommand::userSetGroup)))
                                .then(Commands.literal("addgroup")
                                        .then(Commands.argument("group", StringArgumentType.word()).suggests(GROUPS)
                                                .executes(PermCommand::userAddGroup)))
                                .then(Commands.literal("removegroup")
                                        .then(Commands.argument("group", StringArgumentType.word()).suggests(GROUPS)
                                                .executes(PermCommand::userRemoveGroup)))))
                .then(Commands.literal("check")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("permission", StringArgumentType.greedyString())
                                        .executes(PermCommand::check))))
                .then(Commands.literal("reload").executes(PermCommand::reload)));
    }

    // ------------------------------------------------------------ utilidades

    private static String str(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Text.color("&a" + msg), false);
        PermissionEvents.refreshAll(c.getSource().getServer());
        return 1;
    }

    private static int plain(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Text.color("&c" + msg));
        return 0;
    }

    private static String names(Collection<GameProfile> profiles) {
        List<String> out = new ArrayList<>();
        for (GameProfile p : profiles) out.add(p.getName());
        return String.join(", ", out);
    }

    private static boolean badNode(CommandContext<CommandSourceStack> c, String node) {
        if (PermissionManager.validNode(node)) return false;
        fail(c, "Permiso no valido. Usa letras, numeros y . _ : / * - (sin espacios). Ej: command.gamemode, -command.kill, permscmds.*");
        return true;
    }

    private static String join(Collection<String> items, String empty) {
        return items.isEmpty() ? empty : String.join(", ", items);
    }

    // ------------------------------------------------------------ grupos

    private static int groups(CommandContext<CommandSourceStack> c) {
        return plain(c, "Grupos: " + join(PermissionManager.groupNames(), "(ninguno)"));
    }

    private static int createGroup(CommandContext<CommandSourceStack> c) {
        String n = str(c, "name");
        if (!n.matches("[A-Za-z0-9_-]+")) return fail(c, "Nombre de grupo no valido.");
        return PermissionManager.createGroup(n) ? ok(c, "Grupo '" + n.toLowerCase() + "' creado.") : fail(c, "Ese grupo ya existe.");
    }

    private static int deleteGroup(CommandContext<CommandSourceStack> c) {
        String n = str(c, "name");
        return PermissionManager.deleteGroup(n) ? ok(c, "Grupo '" + n + "' eliminado.")
                : fail(c, "No existe, o es el grupo por defecto (no se puede borrar).");
    }

    private static int groupInfo(CommandContext<CommandSourceStack> c) {
        String n = str(c, "name");
        PermissionManager.Group g = PermissionManager.getGroup(n);
        if (g == null) return fail(c, "Ese grupo no existe.");
        return plain(c, "Grupo " + n.toLowerCase()
                + "\n  peso: " + g.weight
                + "\n  prefijo: " + (g.prefix.isEmpty() ? "(ninguno)" : g.prefix)
                + "\n  hereda de: " + join(g.parents, "(nadie)")
                + "\n  permisos: " + join(g.permissions, "(ninguno)"));
    }

    private static int groupAdd(CommandContext<CommandSourceStack> c) {
        String n = str(c, "name");
        String perm = str(c, "permission").trim();
        if (badNode(c, perm)) return 0;
        return PermissionManager.addGroupPermission(n, perm)
                ? ok(c, "Permiso '" + perm + "' anadido al grupo " + n + ".")
                : fail(c, "Ese grupo no existe.");
    }

    private static int groupRemove(CommandContext<CommandSourceStack> c) {
        String n = str(c, "name");
        String perm = str(c, "permission").trim();
        return PermissionManager.removeGroupPermission(n, perm)
                ? ok(c, "Permiso '" + perm + "' quitado del grupo " + n + ".")
                : fail(c, "El grupo no existe o no tenia ese permiso exacto.");
    }

    private static int parentAdd(CommandContext<CommandSourceStack> c) {
        String err = PermissionManager.addParent(str(c, "name"), str(c, "parent"));
        return err == null ? ok(c, "Herencia anadida.") : fail(c, err);
    }

    private static int parentRemove(CommandContext<CommandSourceStack> c) {
        return PermissionManager.removeParent(str(c, "name"), str(c, "parent"))
                ? ok(c, "Herencia quitada.") : fail(c, "No existe esa herencia.");
    }

    private static int groupPrefix(CommandContext<CommandSourceStack> c) {
        String prefix = str(c, "prefix");
        if (prefix.equalsIgnoreCase("none")) prefix = "";
        return PermissionManager.setPrefix(str(c, "name"), prefix)
                ? ok(c, prefix.isEmpty() ? "Prefijo eliminado." : "Prefijo actualizado.")
                : fail(c, "Ese grupo no existe.");
    }

    private static int groupWeight(CommandContext<CommandSourceStack> c) {
        return PermissionManager.setWeight(str(c, "name"), IntegerArgumentType.getInteger(c, "weight"))
                ? ok(c, "Peso actualizado.") : fail(c, "Ese grupo no existe.");
    }

    // ------------------------------------------------------------ usuarios

    private static int userInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            PermissionManager.User u = PermissionManager.getUser(p.getId());
            plain(c, "Jugador " + p.getName()
                    + "\n  grupos: " + join(PermissionManager.effectiveGroups(p.getId()), "(ninguno)")
                    + "\n  permisos propios: " + (u == null ? "(ninguno)" : join(u.permissions, "(ninguno)")));
        }
        return 1;
    }

    private static int userAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String perm = str(c, "permission").trim();
        if (badNode(c, perm)) return 0;
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        for (GameProfile p : profiles) PermissionManager.addUserPermission(p.getId(), p.getName(), perm);
        return ok(c, "Permiso '" + perm + "' anadido a " + names(profiles) + ".");
    }

    private static int userRemove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String perm = str(c, "permission").trim();
        int removed = 0;
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            if (PermissionManager.removeUserPermission(p.getId(), perm)) removed++;
        }
        return removed > 0 ? ok(c, "Permiso '" + perm + "' quitado (" + removed + ").")
                : fail(c, "Nadie tenia ese permiso exacto.");
    }

    private static int userSetGroup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String group = str(c, "group");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        for (GameProfile p : profiles) {
            if (!PermissionManager.setUserGroup(p.getId(), p.getName(), group)) return fail(c, "Ese grupo no existe.");
        }
        return ok(c, names(profiles) + " ahora esta solo en el grupo " + group.toLowerCase() + ".");
    }

    private static int userAddGroup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String group = str(c, "group");
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(c, "player");
        for (GameProfile p : profiles) {
            if (!PermissionManager.addUserGroup(p.getId(), p.getName(), group)) return fail(c, "Ese grupo no existe.");
        }
        return ok(c, names(profiles) + " anadido(s) al grupo " + group.toLowerCase() + ".");
    }

    private static int userRemoveGroup(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String group = str(c, "group");
        int removed = 0;
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            if (PermissionManager.removeUserGroup(p.getId(), group)) removed++;
        }
        return removed > 0 ? ok(c, "Quitado del grupo " + group + " (" + removed + ").")
                : fail(c, "Nadie estaba en ese grupo.");
    }

    // ------------------------------------------------------------ varios

    private static int check(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String node = str(c, "permission").trim();
        for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
            Boolean r = PermissionManager.check(p.getId(), node);
            plain(c, p.getName() + " -> " + node + ": " + (r == null ? "sin definir (se usa el valor por defecto)" : r.toString()));
        }
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        Settings.load();
        PermissionManager.load();
        return ok(c, "Permisos y ajustes recargados. (Los comandos personalizados necesitan /reload.)");
    }
}
