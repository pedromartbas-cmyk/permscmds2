package com.example.permscmds;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** config/permscmds/data.json: homes y spawn. */
public final class PlayerData {

    public static class Loc {
        public String dimension = "minecraft:overworld";
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        static Loc of(ServerPlayer p) {
            Loc l = new Loc();
            l.dimension = p.serverLevel().dimension().location().toString();
            l.x = p.getX();
            l.y = p.getY();
            l.z = p.getZ();
            l.yaw = p.getYRot();
            l.pitch = p.getXRot();
            return l;
        }
    }

    public static class Warp {
        public Loc loc = new Loc();
        /** Si es true, solo pueden usarlo quienes tengan permscmds.warp.limited (u ops). */
        public boolean opsOnly = false;
        public String creator = "";
    }

    public static class Kit {
        public int cooldownSeconds = 0;
        /** Un objeto por hueco del inventario, en SNBT ("" = vacio). */
        public java.util.List<String> items = new java.util.ArrayList<>();
    }

    public static class Data {
        public Map<String, Map<String, Loc>> homes = new HashMap<>();
        public Loc spawn = null;
        public Map<String, Warp> warps = new TreeMap<>();
        /** UUID -> apodo (con codigos &) */
        public Map<String, String> nicks = new HashMap<>();
        public Map<String, Kit> kits = new TreeMap<>();
        /** uuid -> (kit -> ultimo uso en ms) */
        public Map<String, Map<String, Long>> kitUses = new HashMap<>();
        /** uuid -> (numero de vault -> objetos en SNBT) */
        public Map<String, Map<String, java.util.List<String>>> vaults = new HashMap<>();
    }

    private static Data data = new Data();

    private PlayerData() {}

    public static Data get() {
        return data;
    }

    public static void load() {
        data = JsonStore.load("data.json", Data.class, Data::new);
        if (data.homes == null) data.homes = new HashMap<>();
        if (data.warps == null) data.warps = new TreeMap<>();
        if (data.nicks == null) data.nicks = new HashMap<>();
        if (data.kits == null) data.kits = new TreeMap<>();
        if (data.kitUses == null) data.kitUses = new HashMap<>();
        if (data.vaults == null) data.vaults = new HashMap<>();
    }

    public static String nickOf(UUID id) {
        return data.nicks.get(id.toString());
    }

    public static void save() {
        JsonStore.save("data.json", data);
    }
}
