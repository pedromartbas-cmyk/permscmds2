package com.example.permscmds;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** /createkit /delkit /kit /kits y /vault. */
public final class KitCommands {
    private static final SuggestionProvider<CommandSourceStack> KITS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(new ArrayList<>(PlayerData.get().kits.keySet()), builder);

    private static final MenuType<?>[] ROWS = {
            MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
            MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};

    private KitCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("createkit")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("cooldown", IntegerArgumentType.integer(0))
                                .executes(KitCommands::createKit))));
        d.register(Commands.literal("delkit")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS).executes(KitCommands::delKit)));
        d.register(Commands.literal("kit")
                .then(Commands.argument("name", StringArgumentType.word()).suggests(KITS).executes(KitCommands::kit)));
        d.register(Commands.literal("kits").executes(KitCommands::kits));
        d.register(Commands.literal("vault")
                .then(Commands.argument("vault", IntegerArgumentType.integer(1)).executes(KitCommands::vault)));
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Text.color("&c" + msg));
        return 0;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Text.color("&a" + msg), false);
        return 1;
    }

    // ------------------------------------------------------------ kits

    /** Convierte tu inventario actual en un kit. El cooldown (segundos) puede ser 0 = sin espera. */
    private static int createKit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.kit.create", false)) return 0;
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_-]+")) return fail(c, "Nombre no valido (usa letras, numeros, _ y -).");

        PlayerData.Kit kit = new PlayerData.Kit();
        kit.cooldownSeconds = IntegerArgumentType.getInteger(c, "cooldown");
        int count = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            kit.items.add(Stacks.toSnbt(st));
            if (!st.isEmpty()) count++;
        }
        if (count == 0) return fail(c, "Tu inventario esta vacio.");
        PlayerData.get().kits.put(name, kit);
        PlayerData.save();
        return ok(c, "Kit '" + name + "' creado con " + count + " objeto(s). Espera: " + kit.cooldownSeconds + " s.");
    }

    private static int delKit(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.kit.delete", false)) return 0;
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        if (PlayerData.get().kits.remove(name) == null) return fail(c, "No existe el kit '" + name + "'.");
        PlayerData.save();
        return ok(c, "Kit '" + name + "' borrado.");
    }

    private static int kits(CommandContext<CommandSourceStack> c) {
        if (!Perms.require(c, "permscmds.kit", true)) return 0;
        String list = PlayerData.get().kits.isEmpty() ? "(ninguno)" : String.join(", ", PlayerData.get().kits.keySet());
        c.getSource().sendSuccess(() -> Text.color("&eKits: &f" + list), false);
        return 1;
    }

    private static int kit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack s = c.getSource();
        ServerPlayer p = s.getPlayerOrException();
        if (!Perms.require(c, "permscmds.kit", true)) return 0;
        String name = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
        PlayerData.Kit kit = PlayerData.get().kits.get(name);
        if (kit == null) return fail(c, "No existe el kit '" + name + "'.");
        if (!Perms.require(c, "permscmds.kit." + name, true)) return 0;

        String id = p.getUUID().toString();
        if (kit.cooldownSeconds > 0 && !Perms.allowed(s, "permscmds.kit.nocooldown", false)) {
            Long last = PlayerData.get().kitUses.getOrDefault(id, new HashMap<>()).get(name);
            long now = System.currentTimeMillis();
            if (last != null && now - last < kit.cooldownSeconds * 1000L) {
                long left = (kit.cooldownSeconds * 1000L - (now - last) + 999) / 1000;
                return fail(c, "Podras volver a usar este kit en " + left + " s.");
            }
        }
        PlayerData.get().kitUses.computeIfAbsent(id, k -> new HashMap<>()).put(name, System.currentTimeMillis());
        PlayerData.save();

        for (String snbt : kit.items) {
            ItemStack st = Stacks.fromSnbt(snbt);
            if (st.isEmpty()) continue;
            if (!p.getInventory().add(st)) p.drop(st, false);
        }
        return ok(c, "Has recibido el kit '" + name + "'.");
    }

    // ------------------------------------------------------------ vaults

    private static int vault(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!Perms.require(c, "permscmds.vault", true)) return 0;
        int n = IntegerArgumentType.getInteger(c, "vault");
        int max = Settings.get().vaults;
        if (max <= 0) return fail(c, "Los vaults estan desactivados.");
        if (n > max) return fail(c, "Solo tienes " + max + " vault(s).");

        int rows = Math.max(1, Math.min(6, Settings.get().vaultRows));
        String uuid = p.getUUID().toString();
        String key = String.valueOf(n);

        List<String> saved = PlayerData.get().vaults.computeIfAbsent(uuid, k -> new TreeMap<>()).get(key);
        SimpleContainer container = new SimpleContainer(rows * 9) {
            @Override
            public void stopOpen(Player player) {
                saveVault(uuid, key, this);
            }
        };
        if (saved != null) {
            for (int i = 0; i < container.getContainerSize() && i < saved.size(); i++) {
                container.setItem(i, Stacks.fromSnbt(saved.get(i)));
            }
        }
        MenuType<?> type = ROWS[rows - 1];
        p.openMenu(new SimpleMenuProvider(
                (id, inv, pl) -> new ChestMenu(type, id, inv, container, rows),
                Component.literal("Vault " + n)));
        return 1;
    }

    private static void saveVault(String uuid, String key, Container container) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) out.add(Stacks.toSnbt(container.getItem(i)));
        PlayerData.get().vaults.computeIfAbsent(uuid, k -> new TreeMap<>()).put(key, out);
        PlayerData.save();
    }
}
