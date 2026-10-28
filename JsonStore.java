package com.example.permscmds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

/** Lectura/escritura de JSON en config/permscmds/. */
final class JsonStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonStore() {}

    static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("permscmds");
    }

    static <T> T load(String fileName, Class<T> type, Supplier<T> defaults) {
        Path file = dir().resolve(fileName);
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    T value = GSON.fromJson(reader, type);
                    if (value != null) return value;
                } catch (Exception e) {
                    Path backup = file.resolveSibling(fileName + ".corrupto-" + System.currentTimeMillis());
                    PermsCmdsMod.LOGGER.error("No se pudo leer {} ({}). Se guarda una copia en {}", file, e, backup);
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            PermsCmdsMod.LOGGER.error("Error de E/S con {}", file, e);
        }
        T value = defaults.get();
        save(fileName, value);
        return value;
    }

    static void save(String fileName, Object value) {
        Path file = dir().resolve(fileName);
        Path tmp = file.resolveSibling(fileName + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            PermsCmdsMod.LOGGER.error("No se pudo guardar {}", file, e);
        }
    }
}
