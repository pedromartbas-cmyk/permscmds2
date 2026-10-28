package com.example.permscmds;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class Text {
    private Text() {}

    /** Codigo de color segun la latencia. */
    static String pingColor(int ms) {
        if (ms < 80) return "&a";
        if (ms < 150) return "&e";
        return "&c";
    }

    /** Convierte codigos &a, &c... en colores de Minecraft. */
    static MutableComponent color(String s) {
        return Component.literal(s.replace('&', '\u00a7'));
    }
}
