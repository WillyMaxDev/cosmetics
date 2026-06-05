package dev.netsu.cosmetics.command;

import dev.netsu.cosmetics.NetsuCosmetics;
import dev.netsu.cosmetics.cosmetic.CosmeticData;
import dev.netsu.cosmetics.util.ColorUtil;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class NcCommand implements CommandExecutor {

    private final NetsuCosmetics plugin;

    public NcCommand(NetsuCosmetics plugin) {
        this.plugin = plugin;
    }

    private void msg(Audience audience, String key, String... replacements) {
        String text = plugin.getMessages().get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        audience.sendMessage(ColorUtil.toComponent(plugin.getMessages().prefix() + " " + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            msg(sender, "sin_permiso");
            return true;
        }

        if (args.length == 0) {
            msg(sender, "uso_general");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> {
                if (args.length < 3) { msg(sender, "uso_give"); return true; }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { msg(sender, "jugador_no_encontrado"); return true; }

                String efectoId = args[2].toLowerCase();
                CosmeticData data = plugin.getCosmeticManager().getCosmetic(efectoId);
                if (data == null) { msg(sender, "efecto_no_existe", "{efecto}", efectoId); return true; }

                boolean enchant = args.length >= 4 && args[3].equalsIgnoreCase("#ENCHANT");

                ItemStack skull = plugin.getCosmeticManager().buildSkull(data, data.usos);

                if (enchant) {
                    ItemMeta meta = skull.getItemMeta();
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    skull.setItemMeta(meta);
                }

                target.getInventory().addItem(skull);
                msg(target, "cosmetico_recibido", "{nombre}", data.nombreDisplay);
                msg(sender, "cosmetico_entregado", "{nombre}", data.nombreDisplay, "{jugador}", target.getName());
            }
            case "reload" -> {
                plugin.reload();
                msg(sender, "recargado");
            }
            default -> msg(sender, "subcomando_desconocido");
        }
        return true;
    }
}
