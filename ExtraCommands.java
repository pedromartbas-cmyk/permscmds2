package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;

/** /vuelo (/fly), /ping, /heal, /feed, /god */
public final class ExtraCommands {

    /** Devuelve el mensaje que se envia al jugador afectado. */
    private interface Action {
        String apply(ServerPlayer p);
    }

    private static final Action FLY = p -> {
        if (p.isCreative() || p.isSpectator()) return "&cYa puedes volar por tu modo de juego.";
        Abilities a = p.getAbilities();
        a.mayfly = !a.mayfly;
        if (!a.mayfly) a.flying = false;
        p.onUpdateAbilities();
        return a.mayfly ? "&aVuelo activado." : "&cVuelo desactivado.";
    };

    private static final Action GOD = p -> {
        if (p.isCreative() || p.isSpectator()) return "&cYa eres invulnerable por tu modo de juego.";
        Abilities a = p.getAbilities();
        a.invulnerable = !a.invulnerable;
        p.onUpdateAbilities();
        return a.invulnerable ? "&aModo dios activado." : "&cModo dios desactivado.";
    };

    private static final Action HEAL = p -> {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20.0F);
        p.clearFire();
        p.fallDistance = 0;
        return "&aVida y hambre restauradas.";
    };

    private static final Action FEED = p -> {
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(20.0F);
        return "&aHambre restaurada.";
    };

    private ExtraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // /vuelo [jugador] y alias /fly
        action(d, "vuelo", "permscmds.fly", FLY);
        action(d, "fly", "permscmds.fly", FLY);
        action(d, "god", "permscmds.god", GOD);
        action(d, "heal", "permscmds.heal", HEAL);
        action(d, "feed", "permscmds.feed", FEED);

        d.register(Commands.literal("ping")
                .executes(c -> ping(c, null))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> ping(c, EntityArgument.getPlayer(c, "player")))));
    }

    /**
     * Registra /<name> [jugador]. Para uno mismo se usa el nodo; para otros, el nodo + ".others".
     * Por defecto ninguno esta permitido (solo ops o quien tenga el permiso).
     */
    private static void action(CommandDispatcher<CommandSourceStack> d, String name, String node, Action action) {
        d.register(Commands.literal(name)
                .executes(c -> run(c, null, node, action))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> run(c, EntityArgument.getPlayer(c, "player"), node, action))));
    }

    private static int run(CommandContext<CommandSourceStack> c, ServerPlayer target, String node, Action action)
            throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer t = target != null ? target : s.getPlayerOrException();
        boolean self = s.getEntity() != null && s.getEntity().getUUID().equals(t.getUUID());
        if (!Perms.require(c, self ? node : node + ".others", false)) return 0;

        String msg = action.apply(t);
        t.sendSystemMessage(Text.color(msg));
        if (!self) {
            String who = t.getGameProfile().getName();
            s.sendSuccess(() -> Text.color("&7(" + who + ") " + msg), false);
        }
        return 1;
    }

    private static int ping(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer t = target != null ? target : s.getPlayerOrException();
        boolean self = s.getEntity() != null && s.getEntity().getUUID().equals(t.getUUID());
        if (!Perms.require(c, self ? "permscmds.ping" : "permscmds.ping.others", true)) return 0;

        int ms = t.latency;
        String who = t.getGameProfile().getName();
        s.sendSuccess(() -> Text.color("&eLatencia de &f" + who + "&e: " + Text.pingColor(ms) + ms + " ms"), false);
        return 1;
    }
}
