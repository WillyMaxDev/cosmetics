package dev.netsu.cosmetics.command;

import dev.netsu.cosmetics.NetsuCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NcTabCompleter implements TabCompleter {

    private final NetsuCosmetics plugin;

    public NcTabCompleter(NetsuCosmetics plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) return List.of();

        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : List.of("give", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) result.add(sub);
            }
            return result;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) result.add(p.getName());
                }
                return result;
            }
            if (args.length == 3) {
                for (String id : plugin.getCosmeticManager().getAll().stream()
                        .map(d -> d.id).toList()) {
                    if (id.startsWith(args[2].toLowerCase())) result.add(id);
                }
                return result;
            }
            if (args.length == 4) {
                if ("#enchant".startsWith(args[3].toLowerCase())) result.add("#ENCHANT");
                return result;
            }
        }

        return result;
    }
}
