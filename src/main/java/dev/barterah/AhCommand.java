package dev.barterah;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AhCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "sell", "buy", "list", "my", "offers", "accept", "reject", "withdraw", "cancel", "claim", "help");
    private static final int PAGE_SIZE = 8;
    private static final Component PREFIX = Component.text("[AH] ", NamedTextColor.GOLD);

    private final JavaPlugin plugin;
