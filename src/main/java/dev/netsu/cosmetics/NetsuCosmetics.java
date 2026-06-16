package dev.netsu.cosmetics;
 
import dev.netsu.cosmetics.command.NcCommand;
import dev.netsu.cosmetics.command.NcTabCompleter;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.cosmetic.CosmeticManager;
import dev.netsu.cosmetics.data.ConfigUpdater;
import dev.netsu.cosmetics.data.DataManager;
import dev.netsu.cosmetics.data.MessagesManager;
import dev.netsu.cosmetics.listener.GroundLandListener;
import dev.netsu.cosmetics.listener.BrisaMarinaListener;
import dev.netsu.cosmetics.listener.AuraVeraniegarListener;
import dev.netsu.cosmetics.listener.CosmeticApplyListener;
import dev.netsu.cosmetics.listener.CaminoSoleadoListener;
import dev.netsu.cosmetics.listener.EstelarSolarListener;
import dev.netsu.cosmetics.util.ColorUtil;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
 
import java.util.ArrayList;
import java.util.List;
 
public class NetsuCosmetics extends JavaPlugin {
 
    private static NetsuCosmetics instance;
    private CosmeticManager cosmeticManager;
    private DataManager dataManager;
    private MessagesManager messagesManager;
 
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        ConfigUpdater.update(this);
        WorldGuardHelper.init();
 
        dataManager = new DataManager(this);
        messagesManager = new MessagesManager(this);
        cosmeticManager = new CosmeticManager(this);
 
        getCommand("nc").setExecutor(new NcCommand(this));
        getCommand("nc").setTabCompleter(new NcTabCompleter(this));
        getServer().getPluginManager().registerEvents(new CosmeticApplyListener(this), this);
        getServer().getPluginManager().registerEvents(new CaminoSoleadoListener(this), this);
        getServer().getPluginManager().registerEvents(new AuraVeraniegarListener(this), this);
        getServer().getPluginManager().registerEvents(new BrisaMarinaListener(this), this);
        getServer().getPluginManager().registerEvents(new GroundLandListener(this), this);
        getServer().getPluginManager().registerEvents(new EstelarSolarListener(this), this);
    }
 
    @Override
    public void onDisable() {
        if (dataManager != null) dataManager.save();
    }
 
    public void reload() {
        ConfigUpdater.update(this);
        dataManager.reload();
        messagesManager.reload();
        cosmeticManager.reload();
        updateOnlinePlayersItems();
    }
 
    private void updateOnlinePlayersItems() {
        for (Player player : getServer().getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                updateItemLores(item);
            }
            player.updateInventory();
        }
    }
 
    private void updateItemLores(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return;
        List<Component> currentLore = meta.lore();
        if (currentLore == null) return;
 
        for (CosmeticData data : cosmeticManager.getAll()) {
            boolean found = false;
            for (Component line : currentLore) {
                if (line != null && ColorUtil.plain(line).contains(data.nombreDisplay)) { found = true; break; }
            }
            if (!found) continue;
 
            List<Component> newLore = new ArrayList<>();
            int i = 0;
            while (i < currentLore.size()) {
                Component line = currentLore.get(i);
                String plain = line == null ? "" : ColorUtil.plain(line);
                if (plain.contains(data.nombreDisplay)) {
                    int start = Math.max(0, i - (data.loreAplicado.size() - 1));
                    for (int j = start; j < i; j++) {
                        if (!newLore.isEmpty()) newLore.remove(newLore.size() - 1);
                    }
                    for (String loreLine : data.loreAplicado) {
                        newLore.add(ColorUtil.toComponent(loreLine.replace("{nombre}", data.nombreDisplay)));
                    }
                    i++;
                    i += Math.max(0, data.loreAplicado.size() - 1 - (i - 1 - start));
                    continue;
                }
                newLore.add(line);
                i++;
            }
            meta.lore(newLore);
        }
        item.setItemMeta(meta);
    }
 
    public static NetsuCosmetics getInstance() { return instance; }
    public CosmeticManager getCosmeticManager() { return cosmeticManager; }
    public DataManager getDataManager() { return dataManager; }
    public MessagesManager getMessages() { return messagesManager; }
}
