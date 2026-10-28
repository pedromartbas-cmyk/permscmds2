package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Comandos de administracion y utilidades (todos solo de servidor):
 * /gm /gmc /gms /gma /gmsp /hat /repair(/fix) /enderchest(/ec) /invsee /vanish /top /jump /spawnmob
 * /explode /fireball /superpickaxe /cmdblock /consolecommand /day /night /sun /rain /thunder
 * /easy /normal /hard /peaceful /speed /nick
 * Por defecto solo /hat esta permitido a todos; el resto necesita su permiso (permscmds.<nombre>).
 */
public final class AdminCommands {
    public static final Set<UUID> SUPERPICK = new HashSet<>();

    private AdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ---- modos de juego
        for (String[] g : new String[][]{{"gmc", "creative"}, {"gms", "survival"}, {"gma", "adventure"}, {"gmsp", "spectator"}}) {
            GameType type = GameType.byName(g[1]);
            d.register(Commands.literal(g[0])
                    .executes(c -> setMode(c, type))
                    .then(Commands.argument("player", EntityArgument.player()).executes(c -> setMode(c, type))));
        }
        d.register(Commands.literal("gm")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                List.of("survival", "creative", "adventure", "spectator"), b))
                        .executes(AdminCommands::gm)
                        .then(Commands.argument("player", EntityArgument.player()).executes(AdminCommands::gm))));

        // ---- objetos
        d.register(Commands.literal("hat").executes(AdminCommands::hat));
        for (String name : new String[]{"repair", "fix"}) {
            d.register(Commands.literal(name)
                    .executes(c -> repair(c, false))
                    .then(Commands.literal("all").executes(c -> repair(c, true))));
        }
        for (String name : new String[]{"enderchest", "ec"}) {
            d.register(Commands.literal(name)
                    .executes(AdminCommands::enderChest)
                    .then(Commands.argument("player", EntityArgument.player()).executes(AdminCommands::enderChest)));
        }
        d.register(Commands.literal("invsee")
                .then(Commands.argument("player", EntityArgument.player()).executes(AdminCommands::invSee)));
        d.register(Commands.literal("cmdblock").executes(AdminCommands::cmdBlock));

        // ---- jugador
        for (String name : new String[]{"vanish", "v"}) {
            d.register(Commands.literal(name)
                    .executes(AdminCommands::vanish)
                    .then(Commands.argument("player", EntityArgument.player()).executes(AdminCommands::vanish)));
        }
        d.register(Commands.literal("speed")
                .then(Commands.literal("reset")
                        .executes(c -> speed(c, 1F))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> speed(c, 1F))))
                .then(Commands.argument("speed", FloatArgumentType.floatArg(0F, 10F))
                        .executes(c -> speed(c, FloatArgumentType.getFloat(c, "speed")))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> speed(c, FloatArgumentType.getFloat(c, "speed"))))));
        d.register(Commands.literal("nick")
                .then(Commands.argument("nickname", StringArgumentType.greedyString()).executes(AdminCommands::nick)));
        d.register(Commands.literal("superpickaxe").executes(AdminCommands::superPickaxe));

        // ---- movimiento
        d.register(Commands.literal("top").executes(AdminCommands::top));
        d.register(Commands.literal("jump").executes(AdminCommands::jump));

        // ---- diversion / mundo
        d.register(Commands.literal("spawnmob")
                .then(Commands.argument("mob", ResourceLocationArgument.id())
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES)
                        .executes(c -> spawnMob(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                                .executes(c -> spawnMob(c, IntegerArgumentType.getInteger(c, "count"))))));
        d.register(Commands.literal("explode")
                .executes(AdminCommands::explode)
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(AdminCommands::explode)
                        .then(Commands.argument("power", DoubleArgumentType.doubleArg(0D, 1000D))
                                .executes(AdminCommands::explode)
                                .then(Commands.argument("fire", BoolArgumentType.bool())
                                        .executes(AdminCommands::explode)
                                        .then(Commands.argument("destruct", BoolArgumentType.bool())
                                                .executes(AdminCommands::explode)
                                                .then(Commands.argument("launch", BoolArgumentType.bool())
                                                        .executes(AdminCommands::explode)))))));
        d.register(Commands.literal("fireball")
                .executes(c -> fireball(c, 1))
                .then(Commands.argument("power", IntegerArgumentType.integer(1, 100))
                        .executes(c -> fireball(c, IntegerArgumentType.getInteger(c, "power")))));

        d.register(Commands.literal("day").executes(c -> setTime(c, 1000, "Es de dia.")));
        d.register(Commands.literal("night").executes(c -> setTime(c, 13000, "Es de noche.")));
        d.register(Commands.literal("sun").executes(c -> weather(c, false, false, "Tiempo despejado.")));
        d.register(Commands.literal("rain").executes(c -> weather(c, true, false, "Esta lloviendo.")));
        d.register(Commands.literal("thunder").executes(c -> weather(c, true, true, "Hay tormenta.")));
        for (Difficulty df : new Difficulty[]{Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD}) {
            d.register(Commands.literal(df.getKey()).executes(c -> difficulty(c, df)));
        }

        // ---- solo operadores nivel 4 (no se puede delegar con permisos)
        for (String name : new String[]{"console", "consolecommand"}) {
            d.register(Commands.literal(name)
                    .requires(s -> s.hasPermission(4))
                    .then(Commands.argument("command", StringArgumentType.greedyString()).executes(AdminCommands::consoleCommand)));
        }
    }

    // ------------------------------------------------------------ utilidades

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Text.color("&a" + msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Text.color("&c" + msg));
        return 0;
    }

    /** Jugador objetivo: el argumento "player" si existe, o quien ejecuta. null = sin permiso. */
    private static ServerPlayer resolve(CommandContext<CommandSourceStack> c, String node, boolean defSelf)
            throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer t;
        try {
            t = EntityArgument.getPlayer(c, "player");
        } catch (IllegalArgumentException e) {
            t = s.getPlayerOrException();
        }
        boolean self = s.getEntity() != null && s.getEntity().getUUID().equals(t.getUUID());
        return Perms.require(c, self ? node : node + ".others", self && defSelf) ? t : null;
    }

    private static void notify(CommandContext<CommandSourceStack> c, ServerPlayer t, String msg) {
        t.sendSystemMessage(Text.color(msg));
        CommandSourceStack s = c.getSource();
        if (s.getEntity() == null || !s.getEntity().getUUID().equals(t.getUUID())) {
            String who = t.getGameProfile().getName();
            s.sendSuccess(() -> Text.color("&7(" + who + ") " + msg), false);
        }
    }

    /** Posicion libre junto al bloque al que mira el jugador, o null. */
    private static BlockPos lookTarget(ServerPlayer p) {
        HitResult hit = p.pick(Settings.get().lookRange, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult b = (BlockHitResult) hit;
        return b.getBlockPos().relative(b.getDirection());
    }

    // ------------------------------------------------------------ modos de juego

    private static int setMode(CommandContext<CommandSourceStack> c, GameType gt) throws CommandSyntaxException {
        ServerPlayer t = resolve(c, "permscmds.gamemode", false);
        if (t == null) return 0;
        t.setGameMode(gt);
        notify(c, t, "&aModo de juego: &f" + gt.getName());
        return 1;
    }

    private static int gm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        GameType gt = switch (StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameType.SURVIVAL;
            case "1", "c", "creative" -> GameType.CREATIVE;
            case "2", "a", "adventure" -> GameType.ADVENTURE;
            case "3", "sp", "spectator" -> GameType.SPECTATOR;
            default -> null;
        };
        if (gt == null) return fail(c, "Modo no valido: survival, creative, adventure o spectator.");
        return setMode(c, gt);
    }

    // ------------------------------------------------------------ objetos

    private static int hat(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.hat", true)) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(c, "No llevas nada en la mano.");
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        p.setItemSlot(EquipmentSlot.HEAD, held.copy());
        p.setItemInHand(InteractionHand.MAIN_HAND, head);
        return ok(c, "Te has puesto el objeto en la cabeza.");
    }

    private static int repair(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, all ? "permscmds.repair.all" : "permscmds.repair", false)) return 0;
        int count = 0;
        if (all) {
            for (ItemStack st : p.getInventory().items) count += fix(st);
            for (ItemStack st : p.getInventory().armor) count += fix(st);
            for (ItemStack st : p.getInventory().offhand) count += fix(st);
        } else {
            count = fix(p.getMainHandItem());
        }
        return count > 0 ? ok(c, "Reparado(s): " + count + ".") : fail(c, "No hay nada que reparar.");
    }

    private static int fix(ItemStack st) {
        if (!st.isEmpty() && st.isDamageableItem() && st.getDamageValue() > 0) {
            st.setDamageValue(0);
            return 1;
        }
        return 0;
    }

    private static int enderChest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayer t;
        try {
            t = EntityArgument.getPlayer(c, "player");
        } catch (IllegalArgumentException e) {
            t = p;
        }
        boolean self = t.getUUID().equals(p.getUUID());
        if (!Perms.require(c, self ? "permscmds.enderchest" : "permscmds.enderchest.others", self)) return 0;
        final ServerPlayer owner = t;
        p.openMenu(new SimpleMenuProvider(
                (id, inv, pl) -> ChestMenu.threeRows(id, inv, owner.getEnderChestInventory()),
                Component.translatable("container.enderchest")));
        return 1;
    }

    /** Vista de los 36 huecos del inventario de otro jugador (sin la restriccion de distancia). */
    private static final class InvView implements Container {
        private final Inventory inv;

        InvView(Inventory inv) {
            this.inv = inv;
        }

        @Override public int getContainerSize() { return 36; }
        @Override public boolean isEmpty() { return inv.isEmpty(); }
        @Override public ItemStack getItem(int i) { return inv.getItem(i); }
        @Override public ItemStack removeItem(int i, int n) { return inv.removeItem(i, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { return inv.removeItemNoUpdate(i); }
        @Override public void setItem(int i, ItemStack s) { inv.setItem(i, s); }
        @Override public void setChanged() { inv.setChanged(); }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() { }
    }

    private static int invSee(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.invsee", false)) return 0;
        ServerPlayer t = EntityArgument.getPlayer(c, "player");
        InvView view = new InvView(t.getInventory());
        p.openMenu(new SimpleMenuProvider(
                (id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, view, 4),
                Component.literal("Inventario de " + t.getGameProfile().getName())));
        return 1;
    }

    private static int cmdBlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.cmdblock", false)) return 0;
        ItemStack st = new ItemStack(Items.COMMAND_BLOCK);
        if (!p.getInventory().add(st)) p.drop(st, false);
        return ok(c, "Bloque de comandos entregado.");
    }

    // ------------------------------------------------------------ jugador

    private static int vanish(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer t = resolve(c, "permscmds.vanish", false);
        if (t == null) return 0;
        if (t.hasEffect(MobEffects.INVISIBILITY)) {
            t.removeEffect(MobEffects.INVISIBILITY);
            notify(c, t, "&cYa no eres invisible.");
        } else {
            t.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false));
            notify(c, t, "&aEres invisible (la armadura puesta se sigue viendo).");
        }
        return 1;
    }

    private static int speed(CommandContext<CommandSourceStack> c, float factor) throws CommandSyntaxException {
        ServerPlayer t = resolve(c, "permscmds.speed", false);
        if (t == null) return 0;
        AttributeInstance walk = t.getAttribute(Attributes.MOVEMENT_SPEED);
        if (walk != null) walk.setBaseValue(0.1D * factor);
        t.getAbilities().setFlyingSpeed(0.05F * factor);
        t.onUpdateAbilities();
        notify(c, t, "&aVelocidad: &fx" + factor);
        return 1;
    }

    private static int nick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.nick", true)) return 0;
        String n = StringArgumentType.getString(c, "nickname").trim();
        String id = p.getUUID().toString();

        if (n.equalsIgnoreCase("off") || n.equalsIgnoreCase("reset")) {
            PlayerData.get().nicks.remove(id);
            PlayerData.save();
            p.refreshDisplayName();
            p.refreshTabListName();
            return ok(c, "Apodo eliminado.");
        }
        String plain = n.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
        if (plain.isBlank()) return fail(c, "El apodo esta vacio.");
        if (plain.length() > Settings.get().nickMaxLength) {
            return fail(c, "Maximo " + Settings.get().nickMaxLength + " caracteres (sin contar colores).");
        }
        for (ServerPlayer other : c.getSource().getServer().getPlayerList().getPlayers()) {
            if (!other.getUUID().equals(p.getUUID()) && other.getGameProfile().getName().equalsIgnoreCase(plain)) {
                return fail(c, "Ese apodo coincide con el nombre de otro jugador.");
            }
        }
        PlayerData.get().nicks.put(id, n);
        PlayerData.save();
        p.refreshDisplayName();
        p.refreshTabListName();
        return ok(c, "Apodo cambiado a: " + n.replace('&', '\u00a7'));
    }

    private static int superPickaxe(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.superpickaxe", false)) return 0;
        if (SUPERPICK.remove(p.getUUID())) return ok(c, "Superpico desactivado.");
        SUPERPICK.add(p.getUUID());
        return ok(c, "Superpico activado: rompes bloques al instante con un pico.");
    }

    // ------------------------------------------------------------ movimiento

    private static int top(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.top", true)) return 0;
        ServerLevel level = p.serverLevel();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ());
        if (y <= level.getMinBuildHeight()) return fail(c, "No se encontro suelo encima de ti.");
        UtilityCommands.remember(p);
        p.teleportTo(level, p.getX(), y, p.getZ(), p.getYRot(), p.getXRot());
        return ok(c, "Teletransportado a lo mas alto.");
    }

    private static int jump(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.jump", false)) return 0;
        BlockPos pos = lookTarget(p);
        if (pos == null) return fail(c, "No hay ningun bloque a la vista.");
        UtilityCommands.remember(p);
        p.teleportTo(p.serverLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
        return ok(c, "Teletransportado.");
    }

    // ------------------------------------------------------------ diversion

    private static int spawnMob(CommandContext<CommandSourceStack> c, int count) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.spawnmob", false)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(c, "mob");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || !type.canSummon()) return fail(c, "Ese mob no existe o no se puede invocar.");
        BlockPos pos = lookTarget(p);
        if (pos == null) return fail(c, "No hay ningun bloque a la vista.");

        ServerLevel level = p.serverLevel();
        int n = Math.min(count, Settings.get().spawnMobMax);
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            Entity e = type.create(level);
            if (e == null) continue;
            e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), 0F);
            if (e instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
            }
            level.addFreshEntity(e);
            spawned++;
        }
        return ok(c, "Invocados: " + spawned + ".");
    }

    private static int explode(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.explode", false)) return 0;

        Vec3 at = p.position();
        double power = 4D;
        boolean fire = false;
        boolean destruct = true;
        boolean launch = false;
        try { at = Vec3Argument.getVec3(c, "pos"); } catch (IllegalArgumentException ignored) { }
        try { power = DoubleArgumentType.getDouble(c, "power"); } catch (IllegalArgumentException ignored) { }
        try { fire = BoolArgumentType.getBool(c, "fire"); } catch (IllegalArgumentException ignored) { }
        try { destruct = BoolArgumentType.getBool(c, "destruct"); } catch (IllegalArgumentException ignored) { }
        try { launch = BoolArgumentType.getBool(c, "launch"); } catch (IllegalArgumentException ignored) { }

        float pw = (float) Math.min(power, Settings.get().explodeMaxPower);
        p.serverLevel().explode(null, at.x, at.y, at.z, pw, fire,
                destruct ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        if (launch) {
            p.setDeltaMovement(p.getDeltaMovement().add(0, 1.0 + pw * 0.3, 0));
            p.hurtMarked = true;
        }
        return 1;
    }

    private static int fireball(CommandContext<CommandSourceStack> c, int power) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.fireball", false)) return 0;
        int pw = Math.min(power, Settings.get().explodeMaxPower);
        Vec3 look = p.getLookAngle();
        LargeFireball fb = new LargeFireball(p.serverLevel(), p, look.x, look.y, look.z, pw);
        fb.setPos(p.getX() + look.x * 1.5, p.getEyeY() + look.y * 1.5, p.getZ() + look.z * 1.5);
        p.serverLevel().addFreshEntity(fb);
        return 1;
    }

    // ------------------------------------------------------------ mundo

    private static int setTime(CommandContext<CommandSourceStack> c, int timeOfDay, String msg) {
        if (!Perms.require(c, "permscmds.time", false)) return 0;
        for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
            long t = level.getDayTime();
            long target = t - (t % 24000L) + timeOfDay;
            if (target < t) target += 24000L;
            level.setDayTime(target);
        }
        return ok(c, msg);
    }

    private static int weather(CommandContext<CommandSourceStack> c, boolean rain, boolean thunder, String msg) {
        if (!Perms.require(c, "permscmds.weather", false)) return 0;
        ServerLevel overworld = c.getSource().getServer().overworld();
        if (rain) overworld.setWeatherParameters(0, 6000, true, thunder);
        else overworld.setWeatherParameters(6000, 0, false, false);
        return ok(c, msg);
    }

    private static int difficulty(CommandContext<CommandSourceStack> c, Difficulty df) {
        if (!Perms.require(c, "permscmds.difficulty", false)) return 0;
        c.getSource().getServer().setDifficulty(df, true);
        return ok(c, "Dificultad: " + df.getKey());
    }

    // ------------------------------------------------------------ consola

    private static int consoleCommand(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        String cmd = StringArgumentType.getString(c, "command");
        s.getServer().getCommands().performPrefixedCommand(s.getServer().createCommandSourceStack(), cmd);
        return 1;
    }
}
