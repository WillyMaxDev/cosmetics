package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class EstelarSolarListener implements Listener {

    private final NetsuCosmetics plugin;
    private static final Random RNG = new Random();
    
    private final Map<UUID, Long> arrowSpawnTime = new HashMap<>();
    private final Map<UUID, Long> particleLastSpawn = new HashMap<>();
    private final Map<Location, BlockData> originalBlock = new HashMap<>();
    private final Map<Location, BlockData> originalAbove = new HashMap<>();
    private final Map<Location, BlockData> originalAbove2 = new HashMap<>();
    private final Map<Location, Long> blockTimestamps = new HashMap<>();
    
    private static final Set<Material> BLOQUEADOS = new HashSet<>(Arrays.asList(
            Material.BEDROCK, Material.WATER, Material.LAVA,
            Material.AIR, Material.CAVE_AIR, Material.VOID_AIR,
            Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY,
            Material.END_PORTAL_FRAME, Material.BARRIER
    ));

    private static final Set<Material> TALL_TOP = new HashSet<>(Arrays.asList(
            Material.TALL_GRASS, Material.LARGE_FERN,
            Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.PITCHER_PLANT, Material.TALL_SEAGRASS
    ));

    public EstelarSolarListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startRestoreTask();
        startSpiralTask();
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Arrow || projectile instanceof Projectile)) return;
        if (!(projectile.getShooter() instanceof Player player)) return;

        ItemStack bow = player.getInventory().getItemInMainHand();
        if (bow.getType() != Material.BOW && bow.getType() != Material.CROSSBOW) return;

        CosmeticData data = getEstelarSolarData();
        if (data == null || !bowHasCosmetico(bow, data)) return;

        UUID arrowId = projectile.getUniqueId();
        arrowSpawnTime.put(arrowId, System.currentTimeMillis());
        particleLastSpawn.put(arrowId, System.currentTimeMillis());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile instanceof Arrow)) return;

        UUID arrowId = projectile.getUniqueId();
        if (!arrowSpawnTime.containsKey(arrowId)) return;

        Entity hitEntity = event.getHitEntity();
        Block hitBlock = event.getHitBlock();

        if (hitEntity instanceof LivingEntity living) {
            spawnParticlesOnEntity(projectile, living);
            applyBlocksToEntity(living);
        } else if (hitBlock != null) {
            spawnParticlesOnBlock(projectile, hitBlock.getLocation());
        }
    }

    private void spawnParticlesOnEntity(Projectile arrow, LivingEntity entity) {
        Location loc = entity.getLocation().add(0, entity.getHeight() / 2.0, 0);
        spawnSandParticles(arrow.getWorld(), loc);

        new BukkitRunnable() {
            int duration = 0;

            @Override
            public void run() {
                if (duration++ >= 60 || arrow.isDead()) { // 3 segundos = 60 ticks
                    cancel();
                    return;
                }
                spawnSandParticles(arrow.getWorld(), loc);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnParticlesOnBlock(Projectile arrow, Location loc) {
        spawnSandParticles(arrow.getWorld(), loc.add(0, 0.5, 0));

        new BukkitRunnable() {
            int duration = 0;

            @Override
            public void run() {
                if (duration++ >= 60 || arrow.isDead()) { // 3 segundos = 60 ticks
                    cancel();
                    return;
                }
                spawnSandParticles(arrow.getWorld(), loc);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnSandParticles(World world, Location loc) {
        BlockData sandData = Bukkit.createBlockData(Material.SAND);
        try {
            world.spawnParticle(Particle.BLOCK, loc, 8, 0.3, 0.3, 0.3, 0.05, sandData);
        } catch (Exception ignored) {}
    }

    private void applyBlocksToEntity(LivingEntity entity) {
        Location center = entity.getLocation().getBlock().getLocation();
        CosmeticData data = getEstelarSolarData();
        if (data == null) return;

        long now = System.currentTimeMillis();
        List<Material> bloques = new ArrayList<>();
        bloques.add(Material.SAND);

        int blocksChanged = 0;
        int maxBlocks = 6;
        List<Location> toChange = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block under = center.clone().add(dx, -1, dz).getBlock();
                Location loc = under.getLocation();

                if (dx == 0 && dz == 0) {
                    toChange.add(0, loc); // Centro obligatorio
                } else if (blocksChanged < maxBlocks - 1) {
                    toChange.add(loc);
                    blocksChanged++;
                }
            }
        }

        for (Location loc : toChange) {
            Block under = loc.getBlock();
            
            if (originalBlock.containsKey(loc)) continue;
            if (!canReplace(under)) continue;
            if (entity instanceof Player player && !WorldGuardHelper.canBuild(player, loc)) continue;

            Material replacement = bloques.get(RNG.nextInt(bloques.size()));
            BlockData newBd = Bukkit.createBlockData(replacement);
            originalBlock.put(loc, under.getBlockData());
            blockTimestamps.put(loc, now);

            Block above1 = under.getRelative(0, 1, 0);
            if (!above1.getType().isAir() && !above1.getType().isSolid()) {
                originalAbove.put(loc, above1.getBlockData());
                Block above2 = under.getRelative(0, 2, 0);
                if (TALL_TOP.contains(above2.getType())) {
                    originalAbove2.put(loc, above2.getBlockData());
                    above2.setType(Material.AIR, false);
                }
                above1.setType(Material.AIR, false);
            }

            under.setBlockData(newBd, false);
        }
    }

    private void startRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                List<Location> toRemove = new ArrayList<>();

                for (Map.Entry<Location, Long> entry : blockTimestamps.entrySet()) {
                    if (now - entry.getValue() >= 3000L) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (Location loc : toRemove) {
                    restoreBlock(loc);
                    blockTimestamps.remove(loc);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void restoreBlock(Location loc) {
        BlockData above2 = originalAbove2.remove(loc);
        if (above2 != null) {
            try {
                loc.getBlock().getRelative(0, 2, 0).setBlockData(above2, false);
            } catch (Exception ignored) {}
        }
        BlockData above = originalAbove.remove(loc);
        if (above != null) {
            try {
                loc.getBlock().getRelative(0, 1, 0).setBlockData(above, false);
            } catch (Exception ignored) {}
        }
        BlockData bd = originalBlock.remove(loc);
        if (bd != null) {
            try {
                loc.getBlock().setBlockData(bd, false);
            } catch (Exception ignored) {}
        }
    }

    private void startSpiralTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    for (Entity entity : player.getWorld().getEntities()) {
                        if (!(entity instanceof Arrow arrow)) continue;

                        UUID arrowId = arrow.getUniqueId();
                        if (!arrowSpawnTime.containsKey(arrowId)) continue;

                        spawnSpiralParticles(arrow);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void spawnSpiralParticles(Arrow arrow) {
        Location loc = arrow.getLocation();
        double rotation = (System.currentTimeMillis() / 20.0) % 360;

        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(rotation + (i * (360.0 / points)));
            Location particleLoc = loc.clone().add(
                    Math.cos(angle) * 0.3,
                    Math.sin(angle) * 0.3 + 0.5,
                    Math.sin(angle * 2) * 0.2
            );

            try {
                arrow.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
            } catch (Exception ignored) {}
        }
    }

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        if (name.contains("PORTAL") || name.contains("GATEWAY")) return false;
        return mat.isSolid();
    }

    private boolean bowHasCosmetico(ItemStack bow, CosmeticData data) {
        if (bow == null || bow.getType().isAir() || !bow.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return bow.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getEstelarSolarData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("ESTELA_SOLAR")) return d;
        }
        return null;
    }
}
