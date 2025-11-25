package dev.naruto.astrox.payload.commands;

import dev.naruto.astrox.payload.CommandHandler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class HelpCommand implements Command {
    private final CommandHandler handler;

    public HelpCommand(CommandHandler handler) {
        this.handler = handler;
    }

    @Override
    public void execute(Player sender, String[] args, JavaPlugin plugin) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        Map<String, Command> commands = handler.getCommands();
        List<String> cmdList = new ArrayList<>(commands.keySet());
        Collections.sort(cmdList);

        int perPage = 8;
        int totalPages = (int) Math.ceil(cmdList.size() / (double) perPage);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§lShadow Admin §8| §7Page " + page + "/" + totalPages);
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, cmdList.size());

        for (int i = start; i < end; i++) {
            String cmdName = cmdList.get(i);
            Command cmd = commands.get(cmdName);

            String usage = cmd.getUsage().isEmpty() ? cmdName : cmdName + " " + cmd.getUsage();
            sender.sendMessage("§e#" + usage + " §8- §7" + cmd.getDescription());
        }

        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (page < totalPages) {
            sender.sendMessage("§7Type §e#help " + (page + 1) + "§7 for next page");
        }
    }

    @Override
    public String getDescription() {
        return "Show available commands";
    }

    @Override
    public String getUsage() {
        return "[page]";
    }
}
