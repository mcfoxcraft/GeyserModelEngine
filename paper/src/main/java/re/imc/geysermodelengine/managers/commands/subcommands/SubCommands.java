package re.imc.geysermodelengine.managers.commands.subcommands;

import org.bukkit.command.CommandSender;

public interface SubCommands {
    String getName();
    String getPermission();
    void execute(CommandSender sender);
}
