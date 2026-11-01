package dev.barterah;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class BarterAH extends JavaPlugin implements Listener {

    private Store store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new Store(this);
        store.load();

        AhCommand cmd = new AhCommand(this, store);
        PluginCommand pc = getCommand("ah");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BarterAH aktif. Listing dimuat: " + store.all().size());
    }

    @Override
    public void onDisable() {
        if (store != null) {
            store.save();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) {
                return;
            }
            int left = store.flush(p.getUniqueId());
            long offers = store.all().stream()
                    .filter(l -> l.seller.equals(p.getUniqueId()))
                    .mapToLong(l -> l.offers.size())
                    .sum();
            if (offers > 0) {
                p.sendMessage(Component.text("[AH] Ada " + offers
                        + " tawaran masuk di listing kamu. Cek pakai /ah my", NamedTextColor.YELLOW));
            }
            if (left > 0) {
                p.sendMessage(Component.text("[AH] Ada " + left
                        + " item menunggu diambil. Kosongkan inventory lalu ketik /ah claim",
                        NamedTextColor.YELLOW));
            }
        }, 40L);
    }
}
