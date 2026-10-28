package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /warp /warps /setwarp /delwarp /limitwarp /warpinfo y /rtp (/wild, /rndtp). */
public final class WarpCommands {
    private static final SuggestionProvider<CommandSourceStack> WARPS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(new ArrayList<>(PlayerData.get().warps.keySet()), builder);

    private WarpCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("warp")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS).executes(WarpCommands::warp)));
        d.register(Commands.literal("warps").executes(WarpCommands::warps));
        d.register(Commands.literal("setwarp")
                .then(Commands.argument("name", StringArgumentType.word()).executes(WarpCommands::setWarp)));
        d.register(Commands.literal("delwarp")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS).executes(WarpCommands::delWarp)));
        d.register(Commands.literal("limitwarp")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS).executes(WarpCommands::limitWarp)));
        d.register(Commands.literal("warpinfo")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(WARPS).executes(WarpCommands::warpInfo)));

        for (String name : new String[]{"rtp", "wild", "rndtp"}) {
            d.register(Commands.literal(name).executes(WarpCommands::rtp));
        }
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Text.color("&c" + msg));
        return 0;
    }

    private static String key(CommandContext<CommandSourceStack> c) {
        return StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------ warps

    private static int warp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.warp", true)) return 0;
        String name = key(c);
        PlayerData.Warp w = PlayerData.get().warps.get(name);
        if (w == null) return fail(c, "No existe el warp '" + name + "'.");
        if (w.opsOnly && !Perms.allowed(s, "permscmds.warp.limited", false)) {
            return fail(c, "Ese warp esta limitado a operadores.");
        }
        if (!UtilityCommands.teleport(p, w.loc)) return fail(c, "La dimension de ese warp ya no esta disponible.");
        s.sendSuccess(() -> Text.color("&aTeletransportado al warp '" + name + "'."), false);
        return 1;
    }

    private static int warps(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.warp", true)) return 0;
        List<String> out = new ArrayList<>();
        PlayerData.get().warps.forEach((n, w) -> out.add(w.opsOnly ? n + " (limitado)" : n));
        String list = out.isEmpty() ? "(ninguno)" : String.join(", ", out);
        c.getSource().sendSuccess(() -> Text.color("&eWarps: &f" + list), false);
        return 1;
    }

    private static int setWarp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.setwarp", false)) return 0;
        String name = key(c);
        if (!name.matches("[a-z0-9_-]+")) return fail(c, "Nombre no valido (usa letras, numeros, _ y -).");
        PlayerData.Warp w = PlayerData.get().warps.get(name);
        if (w == null) {
            w = new PlayerData.Warp();
            w.creator = p.getGameProfile().getName();
            PlayerData.get().warps.put(name, w);
        }
        w.loc = PlayerData.Loc.of(p);
        PlayerData.save();
        c.getSource().sendSuccess(() -> Text.color("&aWarp '" + name + "' guardado en tu posicion."), false);
        return 1;
    }

    private static int delWarp(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.delwarp", false)) return 0;
        String name = key(c);
        if (PlayerData.get().warps.remove(name) == null) return fail(c, "No existe el warp '" + name + "'.");
        PlayerData.save();
        c.getSource().sendSuccess(() -> Text.color("&aWarp '" + name + "' borrado."), false);
        return 1;
    }

    private static int limitWarp(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.limitwarp", false)) return 0;
        String name = key(c);
        PlayerData.Warp w = PlayerData.get().warps.get(name);
        if (w == null) return fail(c, "No existe el warp '" + name + "'.");
        w.opsOnly = !w.opsOnly;
        PlayerData.save();
        boolean limited = w.opsOnly;
        c.getSource().sendSuccess(() -> Text.color(limited
                ? "&aEl warp '" + name + "' ahora esta limitado a operadores."
                : "&aEl warp '" + name + "' ahora es para todos."), false);
        return 1;
    }

    private static int warpInfo(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.warp", true)) return 0;
        String name = key(c);
        PlayerData.Warp w = PlayerData.get().warps.get(name);
        if (w == null) return fail(c, "No existe el warp '" + name + "'.");
        String info = "&eWarp &f" + name + "&e\n  creador: &f" + w.creator
                + "&e\n  dimension: &f" + w.loc.dimension
                + "&e\n  posicion: &f" + (int) w.loc.x + ", " + (int) w.loc.y + ", " + (int) w.loc.z
                + "&e\n  limitado a ops: &f" + (w.opsOnly ? "si" : "no");
        c.getSource().sendSuccess(() -> Text.color(info), false);
        return 1;
    }

    // ------------------------------------------------------------ rtp

    /**
     * Teletransporte aleatorio sin limite de radio: el unico limite es el borde del mundo
     * (rtpMaxRadius = 0 en settings.json). Solo funciona en el Overworld.
     */
    private static int rtp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.rtp", true)) return 0;
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return fail(c, "/rtp solo funciona en el Overworld.");

        WorldBorder wb = level.getWorldBorder();
        double cx = wb.getCenterX();
        double cz = wb.getCenterZ();
        double minX = wb.getMinX() + 16;
        double maxX = wb.getMaxX() - 16;
        double minZ = wb.getMinZ() + 16;
        double maxZ = wb.getMaxZ() - 16;
        int maxR = Settings.get().rtpMaxRadius;
        if (maxR > 0) {
            minX = Math.max(minX, cx - maxR);
            maxX = Math.min(maxX, cx + maxR);
            minZ = Math.max(minZ, cz - maxR);
            maxZ = Math.min(maxZ, cz + maxR);
        }
        int minR = Settings.get().rtpMinRadius;
        if (maxX <= minX || maxZ <= minZ) return fail(c, "El borde del mundo es demasiado pequeno.");

        s.sendSuccess(() -> Text.color("&eBuscando un sitio seguro..."), false);
        RandomSource rnd = level.getRandom();
        int attempts = Math.max(1, Settings.get().rtpAttempts);
        for (int i = 0; i < attempts; i++) {
            int x = (int) Math.floor(minX + rnd.nextDouble() * (maxX - minX));
            int z = (int) Math.floor(minZ + rnd.nextDouble() * (maxZ - minZ));
            if (minR > 0 && Math.hypot(x - cx, z - cz) < minR) continue;
            BlockPos spot = safeSpot(level, x, z);
            if (spot == null) continue;

            UtilityCommands.remember(p);
            p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getYRot(), p.getXRot());
            int fx = spot.getX();
            int fz = spot.getZ();
            s.sendSuccess(() -> Text.color("&aTeletransportado a &f" + fx + ", " + fz), false);
            return 1;
        }
        return fail(c, "No se encontro un sitio seguro. Prueba otra vez.");
    }

    /** Carga el chunk y comprueba que el suelo sea firme y sin agua, lava ni peligros. */
    private static BlockPos safeSpot(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) return null;

        BlockPos feet = new BlockPos(x, y, z);
        BlockPos ground = feet.below();
        BlockState below = level.getBlockState(ground);
        if (!below.getFluidState().isEmpty() || !below.isFaceSturdy(level, ground, Direction.UP)) return null;
        if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CACTUS) || below.is(Blocks.CAMPFIRE)
                || below.is(Blocks.SOUL_CAMPFIRE) || below.is(BlockTags.FIRE)) return null;
        if (!level.getBlockState(feet).getFluidState().isEmpty()
                || !level.getBlockState(feet.above()).getFluidState().isEmpty()) return null;
        return feet;
    }
}
