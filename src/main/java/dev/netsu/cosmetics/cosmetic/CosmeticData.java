package dev.netsu.cosmetics.cosmetic;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class CosmeticData {

    public final String id;
    public final String tipo;
    public final int usos;
    public final String skullTexture;
    public final String nombre;
    public final List<String> lore;
    public final String nombreDisplay;
    public final String itemAplicable;
    public final List<String> loreAplicado;
    public final ConfigurationSection configSection;

    public CosmeticData(String id, ConfigurationSection sec) {
        this.id = id;
        this.tipo = sec.getString("tipo", id);
        this.usos = sec.getInt("usos", 1);
        this.skullTexture = sec.getString("cabeza.skull_texture", "");
        this.nombre = sec.getString("cabeza.nombre", id);
        this.lore = sec.getStringList("cabeza.lore");
        this.nombreDisplay = sec.getString("cabeza.nombre_display", id);
        this.itemAplicable = sec.getString("item_aplicable", "BOOTS").toUpperCase();
        this.loreAplicado = sec.getStringList("lore_aplicado");
        this.configSection = sec.getConfigurationSection("config");
    }
}
