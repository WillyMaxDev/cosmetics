package dev.netsu.cosmetics.listener;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GroundLandListener implements Listener {

    private final NetsuCosmetics plugin;
    // Altura Y en el momento en que el jugador dejó el suelo
    private final Map<UUID, Double> airborneFromY = new HashMap<>();
    private final Map<UUID, Boolean> wasAirborne = new HashMap<>();

    public GroundLandListener(NetsuCosmetics plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean onGround = player.isOnGround();
        boolean prevAirborne = wasAirborne.getOrDefault(uuid, false);

        if (!onGround) {
            // Registrar la Y más alta desde la que estaba en suelo
            if (!prevAirborne) {
                airborneFromY.put(uuid, player.getLocation().getY());
            }
            wasAirborne.put(uuid, true);
            return;
        }

        if (!prevAirborne) return; // ya estaba en suelo, nada que hacer

        // Acaba de aterrizar
        wasAirborne.put(uuid, false);
        double fromY = airborneFromY.getOrDefault(uuid, player.getLocation().getY());
        double fallDistance = fromY - player.getLocation().getY();

        // Aura Veraniega — cancelar siempre al aterrizar
        cancelAura(player);

        // Brisa Marina — cancelar siempre al aterrizar (no hay efectos persistentes, solo al atacar, nada que cancelar)

        // Camino Soleado — solo cancelar si cayó más de 3 bloques
        if (fallDistance > 3.0) {
            cancelCaminoSoleado(player);
        }
    }

    private void cancelAura(Player player) {
        // El aura no tiene estado persistente que cancelar — las partículas son por tick
        // Solo necesitamos asegurarnos de que no haya efectos visuales residuales
        // (las partículas ya spawneadas no se pueden cancelar, pero la tarea se detiene sola)
    }

    private void cancelCaminoSoleado(Player player) {
        // Notificar al CaminoSoleadoListener que restaure los bloques de este jugador
        // Lo hacemos a través de un PDC temporal en el jugador
        NamespacedKey key = new NamespacedKey(plugin, "cs_force_restore");
        player.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean shouldForceRestore(Player player, NetsuCosmetics plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "cs_force_restore");
        if (player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            player.getPersistentDataContainer().remove(key);
            return true;
        }
        return false;
    }
}
