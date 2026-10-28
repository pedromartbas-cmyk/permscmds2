package com.example.permscmds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Motor de permisos. Orden de resolucion:
 *  1) permisos propios del jugador, 2) grupos del jugador (por peso, con herencia), 3) grupo por defecto.
 * La primera capa que tenga alguna coincidencia decide. Dentro de una capa gana la coincidencia
 * mas especifica (mas larga) y, a igual especificidad, la negacion (-nodo).
 * Soporta comodines: "*" y "a.b.*". Un nodo "a.b" tambien concede/niega "a.b.c".
 */
public final class PermissionManager {

    public static class Group {
        public String prefix = "";
        public int weight = 0;
        public Set<String> parents = new LinkedHashSet<>();
        public Set<String> permissions = new TreeSet<>();
    }

    public static class User {
        public String name = "";
        public Set<String> groups = new LinkedHashSet<>();
        public Set<String> permissions = new TreeSet<>();
    }

    public static class Data {
        public String defaultGroup = "user";
        public Map<String, Group> groups = new LinkedHashMap<>();
        public Map<String, User> users = new LinkedHashMap<>();
    }

    private static final Pattern VALID_NODE = Pattern.compile("-?[a-z0-9_:/*.-]+");
    private static Data data = new Data();

    private PermissionManager() {}

    // ---------------------------------------------------------------- carga / guardado

    private static Data defaults() {
        Data d = new Data();
        Group user = new Group();
        user.prefix = "&a[User] ";
        user.permissions.addAll(List.of("permscmds.home", "permscmds.sethome", "permscmds.spawn",
                "permscmds.tpa", "permscmds.back", "permscmds.ping"));
        d.groups.put("user", user);

        Group admin = new Group();
        admin.prefix = "&c[Admin] ";
        admin.weight = 100;
        admin.parents.add("user");
        admin.permissions.addAll(List.of("permscmds.*", "command.*"));
        d.groups.put("admin", admin);
        return d;
    }

    public static synchronized void load() {
        data = JsonStore.load("permissions.json", Data.class, PermissionManager::defaults);
        normalize();
    }

    public static synchronized void save() {
        JsonStore.save("permissions.json", data);
    }

    private static void normalize() {
        if (data.groups == null) data.groups = new LinkedHashMap<>();
        if (data.users == null) data.users = new LinkedHashMap<>();
        if (data.defaultGroup == null || data.defaultGroup.isBlank()) data.defaultGroup = "user";
        data.defaultGroup = lower(data.defaultGroup);

        Map<String, Group> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, Group> e : data.groups.entrySet()) {
            Group g = e.getValue() == null ? new Group() : e.getValue();
            if (g.prefix == null) g.prefix = "";
            if (g.parents == null) g.parents = new LinkedHashSet<>();
            if (g.permissions == null) g.permissions = new TreeSet<>();
            fixed.put(lower(e.getKey()), g);
        }
        data.groups = fixed;
        data.groups.computeIfAbsent(data.defaultGroup, k -> new Group());

        for (User u : data.users.values()) {
            if (u.name == null) u.name = "";
            if (u.groups == null) u.groups = new LinkedHashSet<>();
            if (u.permissions == null) u.permissions = new TreeSet<>();
        }
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    public static boolean validNode(String node) {
        return node != null && VALID_NODE.matcher(lower(node)).matches();
    }

    // ---------------------------------------------------------------- comprobacion

    /** true/false si hay una regla que aplique; null si el nodo no esta definido. */
    public static synchronized Boolean check(UUID id, String node) {
        String n = lower(node);
        for (Set<String> layer : layers(id)) {
            Boolean r = evaluate(layer, n);
            if (r != null) return r;
        }
        return null;
    }

    public static boolean has(UUID id, String node, boolean def) {
        Boolean r = check(id, node);
        return r == null ? def : r;
    }

    private static Boolean evaluate(Set<String> perms, String node) {
        int best = -1;
        boolean result = false;
        for (String raw : perms) {
            String p = lower(raw);
            boolean negative = p.startsWith("-");
            String entry = negative ? p.substring(1) : p;
            int score = matchScore(entry, node);
            if (score < 0) continue;
            if (score > best || (score == best && negative)) {
                best = score;
                result = !negative;
            }
        }
        return best < 0 ? null : result;
    }

    /** Mayor puntuacion = coincidencia mas especifica. -1 = no coincide. */
    private static int matchScore(String entry, String node) {
        if (entry.equals("*")) return 10;
        if (entry.equals(node)) return entry.length() * 10 + 3;
        if (entry.endsWith(".*")) {
            String prefix = entry.substring(0, entry.length() - 1); // conserva el punto
            return node.startsWith(prefix) ? prefix.length() * 10 + 1 : -1;
        }
        if (node.startsWith(entry + ".")) return entry.length() * 10 + 2;
        return -1;
    }

    private static int weight(String group) {
        Group g = data.groups.get(group);
        return g == null ? 0 : g.weight;
    }

    private static void sortByWeightDesc(List<String> names) {
        names.sort((a, b) -> Integer.compare(weight(b), weight(a)));
    }

    private static List<Set<String>> layers(UUID id) {
        List<Set<String>> out = new ArrayList<>();
        User u = data.users.get(id.toString());
        if (u != null && !u.permissions.isEmpty()) out.add(u.permissions);

        List<String> names = new ArrayList<>();
        if (u != null) names.addAll(u.groups);
        sortByWeightDesc(names);

        Set<String> seen = new HashSet<>();
        for (String n : names) collect(n, seen, out);
        collect(data.defaultGroup, seen, out);
        return out;
    }

    private static void collect(String name, Set<String> seen, List<Set<String>> out) {
        if (!seen.add(name)) return;
        Group g = data.groups.get(name);
        if (g == null) return;
        out.add(g.permissions);
        List<String> parents = new ArrayList<>(g.parents);
        sortByWeightDesc(parents);
        for (String p : parents) collect(p, seen, out);
    }

    // ---------------------------------------------------------------- consultas

    public static synchronized Collection<String> groupNames() {
        return new ArrayList<>(data.groups.keySet());
    }

    public static synchronized Group getGroup(String name) {
        return data.groups.get(lower(name));
    }

    public static synchronized User getUser(UUID id) {
        return data.users.get(id.toString());
    }

    public static synchronized List<String> effectiveGroups(UUID id) {
        User u = data.users.get(id.toString());
        List<String> names = new ArrayList<>();
        if (u != null) names.addAll(u.groups);
        if (names.isEmpty()) names.add(data.defaultGroup);
        sortByWeightDesc(names);
        return names;
    }

    public static synchronized String prefixFor(UUID id) {
        for (String n : effectiveGroups(id)) {
            Group g = data.groups.get(n);
            if (g != null && g.prefix != null && !g.prefix.isBlank()) {
                // El chat recorta los espacios finales al escribir el comando, asi que se anade aqui.
                return g.prefix.endsWith(" ") ? g.prefix : g.prefix + " ";
            }
        }
        return "";
    }

    // ---------------------------------------------------------------- grupos

    public static synchronized boolean createGroup(String name) {
        String n = lower(name);
        if (data.groups.containsKey(n)) return false;
        data.groups.put(n, new Group());
        save();
        return true;
    }

    public static synchronized boolean deleteGroup(String name) {
        String n = lower(name);
        if (n.equals(data.defaultGroup) || data.groups.remove(n) == null) return false;
        for (Group g : data.groups.values()) g.parents.remove(n);
        for (User u : data.users.values()) u.groups.remove(n);
        save();
        return true;
    }

    private static void addNode(Set<String> set, String node) {
        String n = lower(node);
        set.remove(n.startsWith("-") ? n.substring(1) : "-" + n);
        set.add(n);
    }

    public static synchronized boolean addGroupPermission(String group, String node) {
        Group g = data.groups.get(lower(group));
        if (g == null) return false;
        addNode(g.permissions, node);
        save();
        return true;
    }

    /** @return false si el grupo no existe o no tenia ese nodo exacto. */
    public static synchronized boolean removeGroupPermission(String group, String node) {
        Group g = data.groups.get(lower(group));
        if (g == null || !g.permissions.remove(lower(node))) return false;
        save();
        return true;
    }

    /** @return mensaje de error o null si todo fue bien. */
    public static synchronized String addParent(String group, String parent) {
        String gn = lower(group);
        String pn = lower(parent);
        Group g = data.groups.get(gn);
        if (g == null) return "El grupo '" + gn + "' no existe.";
        if (!data.groups.containsKey(pn)) return "El grupo '" + pn + "' no existe.";
        if (wouldCycle(gn, pn)) return "Eso crearia una herencia circular.";
        g.parents.add(pn);
        save();
        return null;
    }

    private static boolean wouldCycle(String group, String parent) {
        return reaches(parent, group, new HashSet<>());
    }

    private static boolean reaches(String from, String target, Set<String> seen) {
        if (from.equals(target)) return true;
        if (!seen.add(from)) return false;
        Group g = data.groups.get(from);
        if (g == null) return false;
        for (String p : g.parents) if (reaches(p, target, seen)) return true;
        return false;
    }

    public static synchronized boolean removeParent(String group, String parent) {
        Group g = data.groups.get(lower(group));
        if (g == null || !g.parents.remove(lower(parent))) return false;
        save();
        return true;
    }

    public static synchronized boolean setPrefix(String group, String prefix) {
        Group g = data.groups.get(lower(group));
        if (g == null) return false;
        g.prefix = prefix;
        save();
        return true;
    }

    public static synchronized boolean setWeight(String group, int weight) {
        Group g = data.groups.get(lower(group));
        if (g == null) return false;
        g.weight = weight;
        save();
        return true;
    }

    // ---------------------------------------------------------------- usuarios

    private static User user(UUID id, String name) {
        User u = data.users.computeIfAbsent(id.toString(), k -> new User());
        if (name != null && !name.isBlank()) u.name = name;
        return u;
    }

    private static void prune(UUID id) {
        User u = data.users.get(id.toString());
        if (u != null && u.groups.isEmpty() && u.permissions.isEmpty()) data.users.remove(id.toString());
    }

    /** Actualiza el nombre guardado si el jugador ya tiene datos propios. */
    public static synchronized void touch(UUID id, String name) {
        User u = data.users.get(id.toString());
        if (u != null && !name.equals(u.name)) {
            u.name = name;
            save();
        }
    }

    public static synchronized void addUserPermission(UUID id, String name, String node) {
        addNode(user(id, name).permissions, node);
        save();
    }

    public static synchronized boolean removeUserPermission(UUID id, String node) {
        User u = data.users.get(id.toString());
        if (u == null || !u.permissions.remove(lower(node))) return false;
        prune(id);
        save();
        return true;
    }

    public static synchronized boolean setUserGroup(UUID id, String name, String group) {
        String g = lower(group);
        if (!data.groups.containsKey(g)) return false;
        User u = user(id, name);
        u.groups.clear();
        if (!g.equals(data.defaultGroup)) u.groups.add(g);
        prune(id);
        save();
        return true;
    }

    public static synchronized boolean addUserGroup(UUID id, String name, String group) {
        String g = lower(group);
        if (!data.groups.containsKey(g)) return false;
        user(id, name).groups.add(g);
        save();
        return true;
    }

    public static synchronized boolean removeUserGroup(UUID id, String group) {
        User u = data.users.get(id.toString());
        if (u == null || !u.groups.remove(lower(group))) return false;
        prune(id);
        save();
        return true;
    }
}
