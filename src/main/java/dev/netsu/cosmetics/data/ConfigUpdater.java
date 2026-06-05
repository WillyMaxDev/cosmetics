package dev.netsu.cosmetics.data;

import dev.netsu.cosmetics.NetsuCosmetics;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class ConfigUpdater {

    private static final Set<String> LORE_KEYS = Set.of("lore", "lore_aplicado");

    public static void update(NetsuCosmetics plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream == null) return;
        YamlConfiguration def = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));

        boolean changed = addMissing(disk, def, "");
        changed |= removeObsolete(disk, def, "");

        if (changed) {
            try { disk.save(file); } catch (Exception ignored) {}
        }
        plugin.reloadConfig();
    }

    private static boolean addMissing(ConfigurationSection disk, ConfigurationSection def, String path) {
        boolean changed = false;
        for (String key : def.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;

            // Nunca tocar lores
            if (LORE_KEYS.contains(key)) continue;

            if (!disk.contains(key)) {
                disk.set(key, def.get(key));
                changed = true;
            } else if (def.isConfigurationSection(key) && disk.isConfigurationSection(key)) {
                changed |= addMissing(disk.getConfigurationSection(key),
                        def.getConfigurationSection(key), fullKey);
            }
        }
        return changed;
    }

    private static boolean removeObsolete(ConfigurationSection disk, ConfigurationSection def, String path) {
        boolean changed = false;
        for (String key : disk.getKeys(false)) {
            // Nunca tocar lores
            if (LORE_KEYS.contains(key)) continue;

            if (!def.contains(key)) {
                disk.set(key, null);
                changed = true;
            } else if (def.isConfigurationSection(key) && disk.isConfigurationSection(key)) {
                changed |= removeObsolete(disk.getConfigurationSection(key),
                        def.getConfigurationSection(key), path.isEmpty() ? key : path + "." + key);
            }
        }
        return changed;
    }
}
