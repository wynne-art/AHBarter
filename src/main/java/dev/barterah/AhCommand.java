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
    private final Store store;

    public AhCommand(JavaPlugin plugin, Store store) {
        this.plugin = plugin;
        this.store = store;
    }

    // ---------- util pesan ----------

    private void info(CommandSender s, String text) {
        s.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.YELLOW)));
    }

    private void err(CommandSender s, String text) {
        s.sendMessage(PREFIX.append(Component.text(text, NamedTextColor.RED)));
    }

    private void notifyPlayer(UUID uuid, Component msg) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.sendMessage(PREFIX.append(msg));
        }
    }

    private static Component itemText(ItemStack item) {
        return Component.text(item.getAmount() + "x ", NamedTextColor.WHITE).append(item.displayName());
    }

    /** Simpan data, lalu kirim item klaim ke pemain yang online. */
    private void finish(List<UUID> uuids) {
        store.save();
        for (UUID u : uuids) {
            int left = store.flush(u);
            if (left > 0) {
                notifyPlayer(u, Component.text("Inventory penuh, " + left
                        + " item menunggu. Kosongkan lalu ketik /ah claim", NamedTextColor.YELLOW));
            }
        }
    }

    // ---------- dispatch ----------

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Command ini hanya untuk pemain.");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> sell(p, args);
            case "buy", "offer" -> buy(p, args);
            case "list" -> list(p, args);
            case "my" -> my(p);
            case "offers" -> offers(p, args);
            case "accept" -> accept(p, args);
            case "reject" -> reject(p, args);
            case "withdraw" -> withdraw(p, args);
            case "cancel" -> cancel(p, args);
            case "claim" -> claim(p);
            case "reload" -> reload(p);
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(Component.text("=== Auction House Barter ===", NamedTextColor.GOLD));
        String[] lines = {
                "/ah sell [item yang diinginkan] - jual item di tangan (satu stack penuh)",
                "/ah list [halaman] - lihat semua listing",
                "/ah buy <id> - ajukan tawaran pakai item di tangan",
                "/ah my - lihat listing & tawaranmu",
                "/ah offers <id> - lihat tawaran masuk di listingmu",
                "/ah accept <id> <pemain> - terima tawaran",
                "/ah reject <id> <pemain> - tolak tawaran",
                "/ah withdraw <id> - tarik tawaranmu",
                "/ah cancel <id> - batalkan listingmu",
                "/ah claim - ambil item yang tertahan"
        };
        for (String l : lines) {
            p.sendMessage(Component.text(l, NamedTextColor.GRAY));
        }
    }

    // ---------- helper ----------

    private Store.Listing listing(Player p, String[] args) {
        if (args.length < 2) {
            err(p, "Sebutin ID listing-nya. Contoh: /ah " + args[0] + " 3");
            return null;
                                        }
