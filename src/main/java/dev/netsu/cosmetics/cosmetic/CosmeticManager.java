package dev.netsu.cosmetics.cosmetic;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class CosmeticManager {

    private final NetsuCosmetics plugin;
    private final Map<String, CosmeticData> cosmetics = new LinkedHashMap<>();

    public CosmeticManager(NetsuCosmetics plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cosmetics.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("cosmeticos");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection cs = sec.getConfigurationSection(key);
            if (cs != null) cosmetics.put(key, new CosmeticData(key, cs));
        }
    }

    public CosmeticData getCosmetic(String id) {
        return cosmetics.get(id);
    }

    public Collection<CosmeticData> getAll() {
        return cosmetics.values();
    }

    public ItemStack buildSkull(CosmeticData data, int usosRestantes) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (!data.skullTexture.isEmpty()) {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "NetsuCosmetic");
                PlayerTextures textures = profile.getTextures();
                String decoded = new String(Base64.getDecoder().decode(data.skullTexture));
                String urlStr = decoded.replaceAll(".*\"url\":\"([^\"]+)\".*", "$1");
                textures.setSkin(new URL(urlStr));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
            } catch (MalformedURLException | IllegalArgumentException ignored) {}
        }

        meta.displayName(ColorUtil.toComponent(data.nombre));

        List<Component> lore = new ArrayList<>();
        for (String line : data.lore) {
            lore.add(ColorUtil.toComponent(line.replace("{usos}", String.valueOf(usosRestantes))));
        }
        meta.lore(lore);
        skull.setItemMeta(meta);
        return skull;
    }

    public boolean isCosmeticSkull(ItemStack item) {
        return getCosmeticFromSkull(item) != null;
    }

    public CosmeticData getCosmeticFromSkull(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) return null;
        if (!item.hasItemMeta()) return null;
        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return null;
        for (CosmeticData data : cosmetics.values()) {
            Component expected = ColorUtil.toComponent(data.nombre);
            if (displayName.equals(expected)) return data;
        }
        return null;
    }
}
