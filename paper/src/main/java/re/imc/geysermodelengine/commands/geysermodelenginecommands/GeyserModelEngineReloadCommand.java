package re.imc.geysermodelengine.commands.geysermodelenginecommands;

import org.bukkit.command.CommandSender;
import org.bukkit.Bukkit;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.commands.subcommands.SubCommands;
import re.imc.geysermodelengine.util.ColourUtils;

public class GeyserModelEngineReloadCommand implements SubCommands {

    private final GeyserModelEngine plugin;

    private final ColourUtils colourUtils = new ColourUtils();

    public GeyserModelEngineReloadCommand(GeyserModelEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getPermission() { return "geysermodelengine.commands.reload"; }

    @Override
    public void execute(CommandSender sender) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> plugin.getConfigManager().load());
        sender.sendMessage(colourUtils.miniFormat(plugin.getConfigManager().getLang().getString("commands.reload.successfully-reloaded")));
    }
}
