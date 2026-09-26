package re.imc.geysermodelengine.managers.commands.managers.geysermodelengine;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.commands.geysermodelenginecommands.GeyserModelEngineReloadCommand;
import re.imc.geysermodelengine.managers.commands.CommandManagers;
import re.imc.geysermodelengine.managers.commands.subcommands.SubCommands;

import java.util.ArrayList;

public class GeyserModelEngineCommandManager implements CommandManagers {

    private final ArrayList<SubCommands> commands = new ArrayList<>();

    public GeyserModelEngineCommandManager(GeyserModelEngine plugin) {
        commands.add(new GeyserModelEngineReloadCommand(plugin));

        registerCommand(plugin);
    }

    // This command only has literal subcommands: Paper's public API avoids the
    // CommandAPI NMS adapter, which has no Minecraft 26.3 implementation.
    private void registerCommand(GeyserModelEngine plugin) {
        plugin.registerCommand(getName(), new BasicCommand() {
            @Override
            public boolean canUse(CommandSender sender) {
                return commands.stream().anyMatch(command -> sender.hasPermission(command.getPermission()));
            }

            @Override
            public void execute(CommandSourceStack source, String[] args) {
                CommandSender sender = source.getSender();
                if (args.length == 1) {
                    for (SubCommands command : commands) {
                        if (command.getName().equalsIgnoreCase(args[0]) && sender.hasPermission(command.getPermission())) {
                            command.execute(sender);
                            return;
                        }
                    }
                }
                sender.sendMessage("Usage: /geysermodelengine reload");
            }

            @Override
            public Collection<String> suggest(CommandSourceStack source, String[] args) {
                if (args.length > 1) return List.of();
                String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return commands.stream()
                    .filter(command -> source.getSender().hasPermission(command.getPermission()))
                    .map(SubCommands::getName).filter(name -> name.startsWith(prefix)).toList();
            }
        });
    }

    @Override
    public String getName() {
        return "geysermodelengine";
    }

    @Override
    public ArrayList<SubCommands> getCommands() {
        return commands;
    }
}
