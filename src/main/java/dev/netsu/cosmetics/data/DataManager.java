package dev.netsu.cosmetics.data;

import dev.netsu.cosmetics.NetsuCosmetics;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataManager {

    private final NetsuCosmetics plugin;
    private File file;
    private YamlConfiguration yaml;

    public DataManager(NetsuCosmetics plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        save();
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public int getUsos(UUID uuid, String cosmeticoId) {
        return yaml.getInt(uuid + "." + cosmeticoId, 0);
    }

    public void setUsos(UUID uuid, String cosmeticoId, int usos) {
        yaml.set(uuid + "." + cosmeticoId, usos);
    }

    public void save() {
        if (yaml == null || file == null) return;
        try { yaml.save(file); } catch (IOException ignored) {}
    }
}
