package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CaminoSoleadoListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();

    private final Map<Location, BlockState>  originalBlock     = new HashMap<>();
    private final Map<Location, BlockState>  originalAbove     = new HashMap<>();
    private final Map<Location, BlockState>  originalAbove2    = new HashMap<>();
    private final Map<Location, Long>        blockTimestamps   = new HashMap<>();
    private final Set<Location>              playerPlaced      = new HashSet<>();
    private final Map<UUID, Long>            lastMoved         = new HashMap<>();
    private final Map<UUID, BukkitRunnable>  spiralTasks       = new HashMap<>();

    private static final Set<Material> BLOQUEADOS = EnumSet.of(
        Material.BEDROCK, Material.WATER, Material.LAVA,
        Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
        Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
        Material.END_PORTAL_FRAME, Material.BARRIER, Material.OBSIDIAN
    );

    private static final Set<Material> TALL_TOP = EnumSet.of(
        Material.TALL_GRASS, Material.LARGE_FERN,
        Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
        Material.PITCHER_PLANT, Material.TALL_SEAGRASS
    );

    private CosmeticData  cachedData = null;
    private NamespacedKey cachedKey  = null;

    public CaminoSoleadoListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startRestoreTask();
    }

    private void startRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (blockTimestamps.isEmpty()) return;
                long now = System.currentTimeMillis();
                blockTimestamps.entrySet().removeIf(entry -> {
                    if (now - entry.getValue() >= 3000L) {
                        restoreBlock(entry.getKey());
                        return true;
                    }
                    return false;
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restoreBlock(Location loc) {
        BlockState above2 = originalAbove2.remove(loc);
        if (above2 != null) {
            try { above2.update(true, false); } catch (Exception ignored) {}
        }
        BlockState above = originalAbove.remove(loc);
        if (above != null) {
            try { above.update(true, false); } catch (Exception ignored) {}
        }
        BlockState state = originalBlock.remove(loc);
        if (state != null) {
            try { state.update(true, false); } catch (Exception ignored) {}
        }
        playerPlaced.remove(loc.clone().add(0, 1, 0));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (originalBlock.containsKey(loc) || originalAbove.containsKey(loc.clone().add(0, -1, 0))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location above = event.getBlock().getLocation();
        if (originalBlock.containsKey(above.clone().add(0, -1, 0))) {
            playerPlaced.add(above);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (originalBlock.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (originalBlock.containsKey(b.getLocation()) || originalAbove.containsKey(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (originalBlock.containsKey(b.getLocation()) || originalAbove.containsKey(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> originalBlock.containsKey(b.getLocation()) || originalAbove.containsKey(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> originalBlock.containsKey(b.getLocation()) || originalAbove.containsKey(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (originalBlock.containsKey(event.getBlock().getLocation()) || originalAbove.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (originalBlock.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        CosmeticData data = getCaminoSoleadoData();
        if (data == null) return;
        if (!bootsHaveCosmetico(player.getInventory().getBoots())) return;

        long now = System.currentTimeMillis();

        if (GroundLandListener.shouldForceRestore(player, plugin)) {
            new ArrayList<>(originalBlock.keySet()).forEach(this::restoreBlock);
            originalBlock.clear();
            blockTimestamps.clear();
            cancelSpiral(player.getUniqueId());
            return;
        }

        lastMoved.put(player.getUniqueId(), now);
        cancelSpiral(player.getUniqueId());
        applyBlocks(player, data, now);
    }

    private void applyBlocks(Player player, CosmeticData data, long now) {
        ConfigurationSection cfg = data.configSection;
        if (cfg == null) return;

        List<Material> bloques = new ArrayList<>();
        for (String s : cfg.getStringList("bloques")) {
            Material m = Material.matchMaterial(s);
            if (m != null) bloques.add(m);
        }
        if (bloques.isEmpty()) bloques.add(Material.SAND);

        Particle particula = null;
        try { particula = Particle.valueOf(cfg.getString("particulas.tipo", "BLOCK")); } catch (Exception ignored) {}
        int pCantidad = cfg.getInt("particulas.cantidad", 5);

        Location center = player.getLocation().getBlock().getLocation();
        World world = player.getWorld();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block under = center.clone().add(dx, -1, dz).getBlock();
                Location loc = under.getLocation();

                if (originalBlock.containsKey(loc)) continue;
                if (!canReplace(under)) continue;
                if (!WorldGuardHelper.canBuild(player, loc)) continue;

                Material replacement = bloques.get(RNG.nextInt(bloques.size()));
                BlockData newBd = replacement.createBlockData();

                BlockState savedState = under.getState();

                if (savedState instanceof Container) {
                    BlockState clearState = under.getState();
                    if (clearState instanceof org.bukkit.block.Chest) {
                        ((org.bukkit.block.Chest) clearState).getBlockInventory().clear();
                    } else {
                        ((Container) clearState).getInventory().clear();
                    }
                    clearState.update(true, false);
                }

                originalBlock.put(loc, savedState);
                blockTimestamps.put(loc, now);

                Block above1 = under.getRelative(0, 1, 0);
                if (!above1.getType().isAir() && !above1.getType().isSolid()) {
                    originalAbove.put(loc, above1.getState());
                    Block above2 = under.getRelative(0, 2, 0);
                    if (TALL_TOP.contains(above2.getType())) {
                        originalAbove2.put(loc, above2.getState());
                        above2.setType(Material.AIR, false);
                    }
                    above1.setType(Material.AIR, false);
                }

                under.setBlockData(newBd, false);

                if (particula != null) {
                    Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
                    try {
                        if (particula == Particle.FALLING_DUST || particula == Particle.BLOCK) {
                            world.spawnParticle(particula, spawnLoc, pCantidad, 0.3, 0.05, 0.3, 0, newBd);
                        } else {
                            world.spawnParticle(particula, spawnLoc, pCantidad, 0.3, 0.05, 0.3);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void cancelSpiral(UUID uuid) {
        BukkitRunnable t = spiralTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (!mat.isSolid() || BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        return !name.contains("PORTAL") && !name.contains("GATEWAY") && !name.contains("BED");
    }

    private boolean bootsHaveCosmetico(ItemStack boots) {
        if (boots == null || boots.getType().isAir() || !boots.hasItemMeta()) return false;
        if (cachedKey == null) {
            CosmeticData d = getCaminoSoleadoData();
            if (d == null) return false;
            cachedKey = new NamespacedKey(plugin, "cosmetic_" + d.id);
        }
        return boots.getItemMeta().getPersistentDataContainer().has(cachedKey, PersistentDataType.BYTE);
    }

    private CosmeticData getCaminoSoleadoData() {
        if (cachedData == null) {
            for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
                if (d.tipo.equalsIgnoreCase("CAMINO_SOLEADO")) {
                    cachedData = d;
                    break;
                }
            }
        }
        return cachedData;
    }
}
