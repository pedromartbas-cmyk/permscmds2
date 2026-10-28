package com.example.permscmds;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resto de comandos de MoreCommands que funcionan solo en servidor.
 * Elevados (permiso, desactivados por defecto): barrier break broadcast cannon defuse dimension eclone enchantmax
 *   extinguish more rename smite spawner unlimited wipeout sysinfo whois disable enable
 * Para todos: anvil craft ascend descend cyclepainting dropstore realname skull suicide through tps whereami
 */
public final class ToolCommands {
    public static final String UNLIMITED_TAG = "permscmds_unlimited";

    private ToolCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        // ------------- elevados
        d.register(Commands.literal("barrier").executes(c -> give(c, "permscmds.barrier", new ItemStack(Items.BARRIER, 64))));
        d.register(Commands.literal("break").executes(ToolCommands::breakBlock));
        d.register(Commands.literal("broadcast")
                .then(Commands.argument("msg", StringArgumentType.greedyString()).executes(ToolCommands::broadcast)));
        d.register(Commands.literal("cannon")
                .executes(c -> cannon(c, 1, 1.5D, 80))
                .then(Commands.argument("power", IntegerArgumentType.integer(1, 50))
                        .executes(c -> cannon(c, IntegerArgumentType.getInteger(c, "power"), 1.5D, 80))
                        .then(Commands.argument("motion", DoubleArgumentType.doubleArg(0D, 20D))
                                .executes(c -> cannon(c, IntegerArgumentType.getInteger(c, "power"),
                                        DoubleArgumentType.getDouble(c, "motion"), 80))
                                .then(Commands.argument("fuse", IntegerArgumentType.integer(1, 1200))
                                        .executes(c -> cannon(c, IntegerArgumentType.getInteger(c, "power"),
                                                DoubleArgumentType.getDouble(c, "motion"),
                                                IntegerArgumentType.getInteger(c, "fuse")))))));
        d.register(Commands.literal("defuse")
                .executes(c -> defuse(c, 20))
                .then(Commands.literal("all").executes(c -> defuse(c, -1)))
                .then(Commands.argument("range", IntegerArgumentType.integer(1, 1000))
                        .executes(c -> defuse(c, IntegerArgumentType.getInteger(c, "range")))));
        d.register(Commands.literal("dimension")
                .then(Commands.argument("dimension", DimensionArgument.dimension()).executes(ToolCommands::dimension)));
        d.register(Commands.literal("eclone")
                .executes(c -> eclone(c, false))
                .then(Commands.argument("entities", EntityArgument.entities()).executes(c -> eclone(c, true))));
        d.register(Commands.literal("enchantmax").executes(ToolCommands::enchantMax));
        d.register(Commands.literal("extinguish").executes(ToolCommands::extinguish));
        d.register(Commands.literal("more").executes(ToolCommands::more));
        d.register(Commands.literal("rename")
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ToolCommands::rename)));
        d.register(Commands.literal("smite")
                .executes(c -> smite(c, false))
                .then(Commands.argument("entities", EntityArgument.entities()).executes(c -> smite(c, true))));
        d.register(Commands.literal("spawner")
                .then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests(SuggestionProviders.SUMMONABLE_ENTITIES).executes(ToolCommands::spawner)));
        d.register(Commands.literal("unlimited").executes(ToolCommands::unlimited));
        d.register(Commands.literal("wipeout").executes(ToolCommands::wipeout));
        d.register(Commands.literal("sysinfo").executes(ToolCommands::sysinfo));
        d.register(Commands.literal("whois")
                .then(Commands.argument("player", EntityArgument.player()).executes(ToolCommands::whois)));
        d.register(Commands.literal("disable")
                .then(Commands.argument("command", StringArgumentType.word()).executes(c -> disable(c, true))));
        d.register(Commands.literal("enable")
                .then(Commands.argument("command", StringArgumentType.word()).executes(c -> disable(c, false))));

        // ------------- para todos
        d.register(Commands.literal("anvil").executes(ToolCommands::anvil));
        d.register(Commands.literal("craft").executes(ToolCommands::craft));
        d.register(Commands.literal("ascend").executes(c -> level(c, true)));
        d.register(Commands.literal("descend").executes(c -> level(c, false)));
        d.register(Commands.literal("cyclepainting").executes(ToolCommands::cyclePainting));
        d.register(Commands.literal("dropstore").executes(ToolCommands::dropStore));
        d.register(Commands.literal("realname")
                .then(Commands.argument("query", StringArgumentType.greedyString()).executes(ToolCommands::realName)));
        d.register(Commands.literal("skull")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> skull(c, 1))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(c -> skull(c, IntegerArgumentType.getInteger(c, "amount"))))));
        d.register(Commands.literal("suicide").executes(ToolCommands::suicide));
        d.register(Commands.literal("through").executes(ToolCommands::through));
        d.register(Commands.literal("tps").executes(ToolCommands::tps));
        d.register(Commands.literal("whereami").executes(ToolCommands::whereAmI));
    }

    // ------------------------------------------------------------ utilidades

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Text.color("&a" + msg), false);
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

    private static int give(CommandContext<CommandSourceStack> c, String node, ItemStack st) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, node, false)) return 0;
        if (!p.getInventory().add(st)) p.drop(st, false);
        return ok(c, "Objeto entregado.");
    }

    private static BlockPos lookBlock(ServerPlayer p) {
        HitResult hit = p.pick(Settings.get().lookRange, 1.0F, false);
        return hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getBlockPos() : null;
    }

    private static Entity lookEntity(ServerPlayer p) {
        double range = Settings.get().lookRange;
        Vec3 start = p.getEyePosition();
        Vec3 dir = p.getLookAngle();
        Vec3 end = start.add(dir.scale(range));
        AABB box = p.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0D);
        EntityHitResult r = ProjectileUtil.getEntityHitResult(
                p, start, end, box, e -> !e.isSpectator() && e.isPickable(), range * range);
        return r == null ? null : r.getEntity();
    }

    private static boolean passable(ServerLevel level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        return bs.getCollisionShape(level, pos).isEmpty() && bs.getFluidState().isEmpty();
    }

    private static boolean solidGround(ServerLevel level, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        return bs.getFluidState().isEmpty() && bs.isFaceSturdy(level, pos, Direction.UP)
                && !bs.is(Blocks.MAGMA_BLOCK) && !bs.is(Blocks.CACTUS);
    }

    /** Primer hueco seguro (de arriba hacia abajo) en una columna, o null. */
    private static Integer safeY(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int min = level.getMinBuildHeight();
        int top = Math.min(level.getMaxBuildHeight(), min + level.getLogicalHeight()) - 2;
        for (int y = top; y > min + 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (passable(level, feet) && passable(level, feet.above()) && solidGround(level, feet.below())) return y;
        }
        return null;
    }

    // ------------------------------------------------------------ elevados

    private static int breakBlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.break", false)) return 0;
        BlockPos pos = lookBlock(p);
        if (pos == null) return fail(c, "No hay ningun bloque a la vista.");
        ServerLevel level = p.serverLevel();
        if (level.getBlockState(pos).getDestroySpeed(level, pos) < 0) return fail(c, "Ese bloque no se puede romper.");
        level.destroyBlock(pos, true, p);
        return 1;
    }

    private static int broadcast(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.broadcast", false)) return 0;
        String msg = StringArgumentType.getString(c, "msg");
        c.getSource().getServer().getPlayerList().broadcastSystemMessage(Text.color(msg), false);
        return 1;
    }

    /** /cannon [cantidad] [velocidad] [mecha]: dispara bloques de TNT hacia donde miras. */
    private static int cannon(CommandContext<CommandSourceStack> c, int count, double motion, int fuse)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.cannon", false)) return 0;
        Vec3 look = p.getLookAngle();
        int n = Math.min(count, Settings.get().spawnMobMax);
        for (int i = 0; i < n; i++) {
            PrimedTnt tnt = new PrimedTnt(p.serverLevel(), p.getX() + look.x * 1.5, p.getEyeY() + look.y * 1.5,
                    p.getZ() + look.z * 1.5, p);
            tnt.setFuse(fuse);
            tnt.setDeltaMovement(look.scale(motion));
            p.serverLevel().addFreshEntity(tnt);
        }
        return 1;
    }

    private static int defuse(CommandContext<CommandSourceStack> c, int range) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.defuse", false)) return 0;
        List<Entity> found = new ArrayList<>();
        for (Entity e : p.serverLevel().getAllEntities()) {
            if (e instanceof PrimedTnt && (range < 0 || e.distanceToSqr(p) <= (double) range * range)) found.add(e);
        }
        found.forEach(Entity::discard);
        return ok(c, "TNT desactivados: " + found.size() + ".");
    }

    private static int dimension(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.dimension", false)) return 0;
        ServerLevel target = DimensionArgument.getDimension(c, "dimension");
        if (target == p.serverLevel()) return fail(c, "Ya estas en esa dimension.");

        UtilityCommands.remember(p);
        if (target.dimension() == net.minecraft.world.level.Level.END) {
            ServerLevel.makeObsidianPlatform(target);
            p.teleportTo(target, 100.5, 49, 0.5, p.getYRot(), p.getXRot());
            return ok(c, "Has viajado al End.");
        }
        int x = p.getBlockX();
        int z = p.getBlockZ();
        Integer y = safeY(target, x, z);
        if (y == null) {
            BlockPos spawn = target.getSharedSpawnPos();
            x = spawn.getX();
            z = spawn.getZ();
            y = safeY(target, x, z);
        }
        if (y == null) return fail(c, "No se encontro un sitio seguro en esa dimension.");
        p.teleportTo(target, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
        return ok(c, "Has viajado a otra dimension.");
    }

    private static int eclone(CommandContext<CommandSourceStack> c, boolean selector) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.eclone", false)) return 0;
        List<Entity> sources = new ArrayList<>();
        if (selector) {
            sources.addAll(EntityArgument.getEntities(c, "entities"));
        } else {
            Entity looked = lookEntity(p);
            if (looked != null) sources.add(looked);
        }
        int cloned = 0;
        for (Entity src : sources) {
            if (src instanceof Player || !(src.level() instanceof ServerLevel level)) continue;
            Entity copy = src.getType().create(level);
            if (copy == null) continue;
            CompoundTag tag = src.saveWithoutId(new CompoundTag());
            tag.remove("UUID");
            copy.load(tag);
            copy.moveTo(src.getX(), src.getY(), src.getZ(), src.getYRot(), src.getXRot());
            level.addFreshEntity(copy);
            cloned++;
        }
        return cloned > 0 ? ok(c, "Clonados: " + cloned + ".") : fail(c, "No hay ninguna entidad que clonar.");
    }

    private static int enchantMax(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.enchantmax", false)) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(c, "No llevas nada en la mano.");
        for (Enchantment en : BuiltInRegistries.ENCHANTMENT) {
            if (!en.isCurse()) held.enchant(en, en.getMaxLevel());
        }
        return ok(c, "Objeto encantado al maximo.");
    }

    private static int extinguish(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.extinguish", false)) return 0;
        p.clearFire();
        return ok(c, "Fuego apagado.");
    }

    private static int more(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.more", false)) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(c, "No llevas nada en la mano.");
        held.setCount(held.getMaxStackSize());
        return ok(c, "Pila completada.");
    }

    private static int rename(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.rename", false)) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(c, "No llevas nada en la mano.");
        String name = StringArgumentType.getString(c, "name");
        held.setHoverName(Text.color(name).withStyle(style -> style.withItalic(false)));
        return ok(c, "Objeto renombrado.");
    }

    private static void strike(ServerLevel level, BlockPos pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(pos));
        level.addFreshEntity(bolt);
    }

    private static int smite(CommandContext<CommandSourceStack> c, boolean selector) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.smite", false)) return 0;
        int n = 0;
        if (selector) {
            for (Entity e : EntityArgument.getEntities(c, "entities")) {
                if (e.level() instanceof ServerLevel level) {
                    strike(level, e.blockPosition());
                    n++;
                }
            }
        } else {
            Entity e = lookEntity(p);
            BlockPos pos = e != null ? e.blockPosition() : lookBlock(p);
            if (pos != null) {
                strike(p.serverLevel(), pos);
                n = 1;
            }
        }
        return n > 0 ? 1 : fail(c, "No hay nada a la vista.");
    }

    private static int spawner(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.spawner", false)) return 0;
        ResourceLocation id = ResourceLocationArgument.getId(c, "type");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || !type.canSummon()) return fail(c, "Ese mob no existe o no se puede invocar.");
        BlockPos pos = lookBlock(p);
        ServerLevel level = p.serverLevel();
        if (pos == null || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return fail(c, "Mira un spawner para cambiarlo.");
        }
        spawner.setEntityId(type, level.getRandom());
        spawner.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
        return ok(c, "Spawner cambiado a " + id + ".");
    }

    private static int unlimited(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.unlimited", false)) return 0;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return fail(c, "No llevas nada en la mano.");
        CompoundTag tag = held.getOrCreateTag();
        if (tag.contains(UNLIMITED_TAG)) {
            tag.remove(UNLIMITED_TAG);
            if (tag.isEmpty()) held.setTag(null);
            return ok(c, "Objeto ilimitado desactivado.");
        }
        held.setCount(held.getMaxStackSize());
        tag.putInt(UNLIMITED_TAG, held.getCount());
        return ok(c, "Este objeto ya no se acaba.");
    }

    /** Elimina todos los mobs, salvo mascotas domadas y mobs con nombre. */
    private static int wipeout(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.wipeout", false)) return 0;
        int total = 0;
        for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
            List<Entity> remove = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Mob m && !m.hasCustomName() && !(m instanceof TamableAnimal t && t.isTame())) remove.add(e);
            }
            remove.forEach(Entity::discard);
            total += remove.size();
        }
        return ok(c, "Mobs eliminados: " + total + " (se respetan mascotas y mobs con nombre).");
    }

    private static int sysinfo(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.sysinfo", false)) return 0;
        Runtime rt = Runtime.getRuntime();
        long mb = 1024L * 1024L;
        long used = (rt.totalMemory() - rt.freeMemory()) / mb;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;
        return plain(c, "Sistema del servidor"
                + "\n  SO: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")"
                + "\n  Java: " + System.getProperty("java.version")
                + "\n  Nucleos: " + rt.availableProcessors()
                + "\n  Memoria: " + used + " / " + (rt.maxMemory() / mb) + " MB"
                + "\n  Tiempo encendido: " + (uptime / 3600) + "h " + ((uptime % 3600) / 60) + "m");
    }

    private static int whois(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        if (!Perms.require(c, "permscmds.whois", false)) return 0;
        ServerPlayer t = EntityArgument.getPlayer(c, "player");
        String nick = PlayerData.nickOf(t.getUUID());
        return plain(c, "Jugador " + t.getGameProfile().getName()
                + "\n  UUID: " + t.getUUID()
                + "\n  Apodo: " + (nick == null ? "(ninguno)" : nick)
                + "\n  Grupos: " + String.join(", ", PermissionManager.effectiveGroups(t.getUUID()))
                + "\n  Modo de juego: " + t.gameMode.getGameModeForPlayer().getName()
                + "\n  Vida: " + Math.round(t.getHealth()) + " / " + Math.round(t.getMaxHealth())
                + "\n  Posicion: " + t.getBlockX() + ", " + t.getBlockY() + ", " + t.getBlockZ()
                + " (" + t.serverLevel().dimension().location() + ")"
                + "\n  Ping: " + t.latency + " ms"
                + "\n  IP: " + t.getIpAddress());
    }

    private static int disable(CommandContext<CommandSourceStack> c, boolean disable) {
        if (!Perms.require(c, "permscmds.disable", false)) return 0;
        String name = StringArgumentType.getString(c, "command").toLowerCase(Locale.ROOT);
        List<String> list = Settings.get().disabledCommands;
        if (disable) {
            if (!list.contains(name)) list.add(name);
        } else if (!list.remove(name)) {
            return fail(c, "Ese comando no estaba desactivado.");
        }
        Settings.save();
        return ok(c, disable ? "/" + name + " ahora es solo para operadores." : "/" + name + " vuelve a estar disponible.");
    }

    // ------------------------------------------------------------ para todos

    private static int anvil(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.anvil", true)) return 0;
        ContainerLevelAccess access = ContainerLevelAccess.create(p.serverLevel(), p.blockPosition());
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new AnvilMenu(id, inv, access) {
            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, Component.translatable("container.repair")));
        return 1;
    }

    private static int craft(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.craft", true)) return 0;
        ContainerLevelAccess access = ContainerLevelAccess.create(p.serverLevel(), p.blockPosition());
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CraftingMenu(id, inv, access) {
            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, Component.translatable("container.crafting")));
        return 1;
    }

    /** /ascend y /descend: pasar al piso de arriba o de abajo atravesando techo o suelo. */
    private static int level(CommandContext<CommandSourceStack> c, boolean up) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, up ? "permscmds.ascend" : "permscmds.descend", true)) return 0;
        ServerLevel level = p.serverLevel();
        int x = p.getBlockX();
        int z = p.getBlockZ();
        int min = level.getMinBuildHeight();
        int max = level.getMaxBuildHeight() - 2;
        Integer target = null;

        if (up) {
            int y = p.getBlockY() + 2;
            while (y < max && passable(level, new BlockPos(x, y, z))) y++;   // hasta el techo
            while (y < max && !passable(level, new BlockPos(x, y, z))) y++;  // atravesarlo
            if (y < max && passable(level, new BlockPos(x, y + 1, z))) target = y;
        } else {
            int y = p.getBlockY() - 1;
            while (y > min + 1 && passable(level, new BlockPos(x, y, z))) y--;   // hasta el suelo
            while (y > min + 1 && !passable(level, new BlockPos(x, y, z))) y--;  // atravesarlo
            while (y > min + 1 && passable(level, new BlockPos(x, y - 1, z))) y--; // caer hasta el siguiente suelo
            if (y > min + 1 && solidGround(level, new BlockPos(x, y - 1, z))
                    && passable(level, new BlockPos(x, y, z)) && passable(level, new BlockPos(x, y + 1, z))) target = y;
        }
        if (target == null) return fail(c, up ? "No hay piso encima." : "No hay piso debajo.");
        UtilityCommands.remember(p);
        p.teleportTo(level, p.getX(), target, p.getZ(), p.getYRot(), p.getXRot());
        return 1;
    }

    private static int cyclePainting(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.cyclepainting", true)) return 0;
        if (!(lookEntity(p) instanceof Painting painting)) return fail(c, "Mira un cuadro.");
        Holder<PaintingVariant> cur = painting.getVariant();
        List<Holder.Reference<PaintingVariant>> same = BuiltInRegistries.PAINTING_VARIANT.holders()
                .filter(h -> h.value().getWidth() == cur.value().getWidth() && h.value().getHeight() == cur.value().getHeight())
                .toList();
        if (same.size() < 2) return fail(c, "Ese cuadro no tiene variantes del mismo tamano.");
        int idx = -1;
        for (int i = 0; i < same.size(); i++) if (same.get(i).value() == cur.value()) idx = i;
        painting.setVariant(same.get((idx + 1) % same.size()));
        return 1;
    }

    /** Guarda todo tu inventario en un cofre doble colocado donde estas. */
    private static int dropStore(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.dropstore", true)) return 0;
        ServerLevel level = p.serverLevel();
        BlockPos a = p.blockPosition();
        BlockPos b = a.east();
        if (!level.getBlockState(a).canBeReplaced() || !level.getBlockState(b).canBeReplaced()) {
            return fail(c, "No hay sitio para un cofre doble aqui.");
        }
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (!st.isEmpty()) all.add(st.copy());
        }
        if (all.isEmpty()) return fail(c, "Tu inventario esta vacio.");

        BlockState left = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState right = Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        level.setBlock(a, left, 3);
        level.setBlock(b, right, 3);
        if (!(level.getBlockEntity(a) instanceof ChestBlockEntity ca) || !(level.getBlockEntity(b) instanceof ChestBlockEntity cb)) {
            return fail(c, "No se pudo crear el cofre.");
        }
        for (int i = 0; i < all.size() && i < 54; i++) {
            if (i < 27) ca.setItem(i, all.get(i));
            else cb.setItem(i - 27, all.get(i));
        }
        p.getInventory().clearContent();
        return ok(c, "Inventario guardado en un cofre doble.");
    }

    private static String stripColors(String s) {
        return s.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    private static int realName(CommandContext<CommandSourceStack> c) {
        String query = StringArgumentType.getString(c, "query").toLowerCase(Locale.ROOT);
        MinecraftServer server = c.getSource().getServer();
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> e : PlayerData.get().nicks.entrySet()) {
            if (!stripColors(e.getValue()).toLowerCase(Locale.ROOT).contains(query)) continue;
            String real = e.getKey();
            try {
                java.util.UUID id = java.util.UUID.fromString(e.getKey());
                ServerPlayer online = server.getPlayerList().getPlayer(id);
                if (online != null) {
                    real = online.getGameProfile().getName();
                } else if (server.getProfileCache() != null) {
                    real = server.getProfileCache().get(id).map(GameProfile::getName).orElse(real);
                }
            } catch (IllegalArgumentException ignored) {
                // clave que no es un UUID: se muestra tal cual
            }
            found.add(stripColors(e.getValue()) + " = " + real);
        }
        return found.isEmpty() ? fail(c, "Nadie tiene un apodo asi.") : plain(c, "Nombres reales: " + String.join(", ", found));
    }

    private static int skull(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.skull", true)) return 0;
        String name = StringArgumentType.getString(c, "player");
        ItemStack head = new ItemStack(Items.PLAYER_HEAD, amount);
        head.getOrCreateTag().putString("SkullOwner", name);
        if (!p.getInventory().add(head)) p.drop(head, false);
        return ok(c, "Cabeza de " + name + " entregada.");
    }

    private static int suicide(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.suicide", true)) return 0;
        p.kill();
        return 1;
    }

    /** /through: atraviesa la pared que tienes delante. */
    private static int through(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.through", true)) return 0;
        ServerLevel level = p.serverLevel();
        Direction dir = p.getDirection();
        BlockPos feet = p.blockPosition();
        boolean seenSolid = false;
        for (int i = 1; i <= 32; i++) {
            BlockPos pos = feet.relative(dir, i);
            boolean free = passable(level, pos) && passable(level, pos.above());
            if (!free) {
                seenSolid = true;
            } else if (seenSolid) {
                UtilityCommands.remember(p);
                p.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYRot(), p.getXRot());
                return 1;
            }
        }
        return fail(c, "No hay ninguna pared que atravesar delante de ti.");
    }

    private static int tps(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.tps", true)) return 0;
        float mspt = c.getSource().getServer().getAverageTickTime();
        double tps = Math.min(20.0D, 1000.0D / Math.max(mspt, 0.001F));
        return plain(c, String.format(Locale.ROOT, "TPS: %.1f   MSPT: %.1f ms", tps, mspt));
    }

    private static int whereAmI(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.whereami", true)) return 0;
        String biome = p.serverLevel().getBiome(p.blockPosition()).unwrapKey()
                .map(k -> k.location().toString()).orElse("?");
        return plain(c, "Estas en " + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ()
                + " (" + p.serverLevel().dimension().location() + ", " + biome + ")");
    }
}
