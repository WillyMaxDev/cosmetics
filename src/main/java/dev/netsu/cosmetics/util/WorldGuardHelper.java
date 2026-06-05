package dev.netsu.cosmetics.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldGuardHelper {

    private static boolean enabled = false;

    public static void init() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            enabled = true;
        } catch (ClassNotFoundException ignored) {}
    }

    public static boolean canBuild(Player player, Location loc) {
        if (!enabled) return true;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldguard.LocalPlayer wgPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            return query.testBuild(BukkitAdapter.adapt(loc), wgPlayer);
        } catch (Exception e) {
            return true;
        }
    }
}
