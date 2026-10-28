package com.example.permscmds;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

/** Guardar y leer objetos como texto SNBT (para kits y vaults). */
final class Stacks {
    private Stacks() {}

    static String toSnbt(ItemStack st) {
        return st.isEmpty() ? "" : st.save(new CompoundTag()).toString();
    }

    static ItemStack fromSnbt(String s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        try {
            return ItemStack.of(TagParser.parseTag(s));
        } catch (Exception e) {
            PermsCmdsMod.LOGGER.warn("Objeto guardado ilegible: {}", e.toString());
            return ItemStack.EMPTY;
        }
    }
}
