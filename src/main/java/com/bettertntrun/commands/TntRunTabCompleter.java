package com.bettertntrun.commands;

import com.bettertntrun.BetterTntRun;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TntRunTabCompleter implements TabCompleter {

    private final BetterTntRun plugin;

    public TntRunTabCompleter(BetterTntRun plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("join", "leave", "top", "hub"));
            if (sender.hasPermission("bettertntrun.admin")) {
                subs.addAll(Arrays.asList("config", "setspawn", "setpos1", "setpos2", "setplayer", "deftime", "start", "NCPspawn"));
            }
            return filter(subs, args[0]);
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "join", "config", "setspawn", "setpos1", "setpos2", "deftime", "start", "ncpspawn":
                    return filter(new ArrayList<>(plugin.getConfigManager().getMaps().keySet()), args[1]);
                case "hub":
                    if (sender.hasPermission("bettertntrun.admin")) return filter(List.of("setposition"), args[1]);
                    break;
                case "setplayer":
                    return List.of("<min>");
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "setplayer":
                    return List.of("<max>");
                case "hub":
                    if (args[1].equalsIgnoreCase("setposition"))
                        return filter(new ArrayList<>(plugin.getConfigManager().getMaps().keySet()), args[2]);
                    break;
                case "ncpspawn":
                    return filter(Arrays.asList("north", "south", "east", "west", "ne", "nw", "se", "sw"), args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("setplayer")) {
            return filter(new ArrayList<>(plugin.getConfigManager().getMaps().keySet()), args[3]);
        }

        return completions;
    }

    private List<String> filter(List<String> options, String input) {
        return options.stream().filter(s -> s.toLowerCase().startsWith(input.toLowerCase())).collect(Collectors.toList());
    }
}
