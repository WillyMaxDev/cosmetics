package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.WorldGuardHelper;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CaminoSoleadoListener implements Listener {

    private final NetsuCosmetics plugin;

    private final Map<Location, BlockData> originalBlock = new HashMap<>();
    private final Map<Location, BlockData> originalAbove = new HashMap<>();
    private final Map<Location, BlockData> originalAbove2 = new HashMap<>();
    // Bloques que NO deben expirar (jugador quieto encima)
    private final Set<Location> frozenBlocks = new HashSet<>();
    private final Set<Location> playerPlaced = new HashSet<>();
    private final Map<UUID, Long> lastMoved = new HashMap<>();
    private final Map<UUID, BukkitRunnable> spiralTasks = new HashMap<>();
    // Intensidad de partículas por jugador (0.0 - 1.0)
    private final Map<UUID, Double> particleIntensity = new HashMap<>();

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

    public CaminoSoleadoListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
        startRestoreTask();
        startIntensityTask();
    }

    private void startRestoreTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<Location, BlockData>> it = originalBlock.entrySet().iterator();
                while (it.hasNext()) {
                    Location loc = it.next().getKey();
                    if (!frozenBlocks.contains(loc)) {
                        restoreBlock(loc);
                        it.remove();
                    }
                }
                frozenBlocks.clear();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // Tarea que actualiza la intensidad de partículas gradualmente
    private void startIntensityTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                CosmeticData data = getCaminoSoleadoData();
                if (data == null) return;
                long now = System.currentTimeMillis();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (!bootsHaveCosmetico(player.getInventory().getBoots(), data)) continue;

                    long lastMove = lastMoved.getOrDefault(player.getUniqueId(), now);
                    long quietoMs = now - lastMove;
                    double intensity = particleIntensity.getOrDefault(player.getUniqueId(), 0.0);

                    if (quietoMs >= 2000L) {
                        // Rampa up: de 0 a 1 entre 2s y 5s
                        double target = Math.min(1.0, (quietoMs - 2000.0) / 3000.0);
                        intensity = Math.min(target, intensity + 0.05);
                    } else {
                        // Rampa down: bajar gradualmente al moverse
                        intensity = Math.max(0.0, intensity - 0.08);
                    }
                    particleIntensity.put(player.getUniqueId(), intensity);

                    // Emitir partículas idle según intensidad
                    if (intensity > 0.05) {
                        spawnIdleParticles(player, data, intensity);
                    }

                    // Espiral a los 5s
                    if (quietoMs >= 5000L && !spiralTasks.containsKey(player.getUniqueId())) {
                        startSpiral(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void spawnIdleParticles(Player player, CosmeticData data, double intensity) {
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
        if (particula == null) return;

        Random random = new Random();
        // Número de partículas proporcional a la intensidad (máx 6)
        int count = (int) Math.ceil(intensity * 6);
        Location center = player.getLocation().getBlock().getLocation();

        for (int i = 0; i < count; i++) {
            int dx = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            Location loc = center.clone().add(dx, 0, dz);
            if (!originalBlock.containsKey(loc.clone().add(0, -1, 0))) continue;

            Material mat = bloques.get(random.nextInt(bloques.size()));
            BlockData bd = Bukkit.createBlockData(mat);
            Location spawnLoc = loc.clone().add(0.5 + (random.nextDouble() - 0.5) * 0.4, 1.0, 0.5 + (random.nextDouble() - 0.5) * 0.4);
            try {
                if (particula == Particle.BLOCK || particula == Particle.FALLING_DUST) {
                    player.getWorld().spawnParticle(particula, spawnLoc, 1, 0.1, 0.05, 0.1, 0, bd);
                } else {
                    player.getWorld().spawnParticle(particula, spawnLoc, 1, 0.1, 0.05, 0.1);
                }
            } catch (Exception ignored) {}
        }
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
        playerPlaced.remove(loc.clone().add(0, 1, 0));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (originalBlock.containsKey(loc)) { event.setCancelled(true); return; }
        if (originalAbove.containsKey(loc.clone().add(0, -1, 0))) event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location above = event.getBlock().getLocation();
        if (originalBlock.containsKey(above.clone().add(0, -1, 0))) playerPlaced.add(above);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        CosmeticData data = getCaminoSoleadoData();
        if (data == null) return;
        if (!bootsHaveCosmetico(player.getInventory().getBoots(), data)) return;

        long now = System.currentTimeMillis();
        Location center = player.getLocation().getBlock().getLocation();

        // Restaurar forzado si cayó más de 3 bloques
        if (GroundLandListener.shouldForceRestore(player, plugin)) {
            List<Location> toRestore = new ArrayList<>(originalBlock.keySet());
            for (Location loc : toRestore) restoreBlock(loc);
            originalBlock.clear();
            frozenBlocks.clear();
            cancelSpiral(player.getUniqueId());
            return;
        }

        if (event.hasChangedBlock()) {
            lastMoved.put(player.getUniqueId(), now);
            cancelSpiral(player.getUniqueId());
            applyBlocks(player, data, now);
        } else {
            // Quieto: marcar bloques bajo los pies como congelados
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Location loc = center.clone().add(dx, -1, dz).getBlock().getLocation();
                    if (originalBlock.containsKey(loc)) frozenBlocks.add(loc);
                }
            }
        }
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
        Random random = new Random();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block under = center.clone().add(dx, -1, dz).getBlock();
                Location loc = under.getLocation();

                if (originalBlock.containsKey(loc)) continue;
                if (!canReplace(under)) continue;
                if (!WorldGuardHelper.canBuild(player, loc)) continue;

                Material replacement = bloques.get(random.nextInt(bloques.size()));
                BlockData newBd = Bukkit.createBlockData(replacement);
                originalBlock.put(loc, under.getBlockData());

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

                if (particula != null) {
                    Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
                    try {
                        if (particula == Particle.FALLING_DUST || particula == Particle.BLOCK) {
                            player.getWorld().spawnParticle(particula, spawnLoc, pCantidad, 0.3, 0.05, 0.3, 0, newBd);
                        } else {
                            player.getWorld().spawnParticle(particula, spawnLoc, pCantidad, 0.3, 0.05, 0.3);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void startSpiral(Player player) {
        BlockData sandData = Bukkit.createBlockData(Material.SAND);
        BukkitRunnable task = new BukkitRunnable() {
            double angle = 0;
            double height = 2.0;
            boolean goingDown = true;

            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); spiralTasks.remove(player.getUniqueId()); return; }
                CosmeticData d = getCaminoSoleadoData();
                if (d == null || !bootsHaveCosmetico(player.getInventory().getBoots(), d)) {
                    cancel(); spiralTasks.remove(player.getUniqueId()); return;
                }
                Location base = player.getLocation();
                for (int i = 0; i < 3; i++) {
                    double a = angle + Math.toRadians(i * 120);
                    Location loc = base.clone().add(Math.cos(a) * 0.6, height, Math.sin(a) * 0.6);
                    try { player.getWorld().spawnParticle(Particle.BLOCK, loc, 1, 0, 0, 0, 0, sandData); } catch (Exception ignored) {}
                }
                angle += Math.toRadians(18);
                height = goingDown ? height - 0.08 : height + 0.08;
                if (height <= 0.1) goingDown = false;
                else if (height >= 2.0) goingDown = true;
            }
        };
        spiralTasks.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private void cancelSpiral(UUID uuid) {
        BukkitRunnable t = spiralTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    private boolean canReplace(Block block) {
        Material mat = block.getType();
        if (BLOQUEADOS.contains(mat)) return false;
        String name = mat.name();
        if (name.contains("PORTAL") || name.contains("GATEWAY")) return false;
        return mat.isSolid();
    }

    private boolean bootsHaveCosmetico(ItemStack boots, CosmeticData data) {
        if (boots == null || boots.getType().isAir() || !boots.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "cosmetic_" + data.id);
        return boots.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private CosmeticData getCaminoSoleadoData() {
        for (CosmeticData d : plugin.getCosmeticManager().getAll()) {
            if (d.tipo.equalsIgnoreCase("CAMINO_SOLEADO")) return d;
        }
        return null;
    }
}
