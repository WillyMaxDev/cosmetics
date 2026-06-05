package dev.netsu.cosmetics.data;

import dev.netsu.cosmetics.NetsuCosmetics;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessagesManager {

    private final NetsuCosmetics plugin;
    private YamlConfiguration yaml;

    public MessagesManager(NetsuCosmetics plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String key) {
        return yaml.getString(key, "&c[messages." + key + " no encontrado]");
    }

    public String prefix() {
        return plugin.getConfig().getString("prefix", "&c[NC]");
    }
}
