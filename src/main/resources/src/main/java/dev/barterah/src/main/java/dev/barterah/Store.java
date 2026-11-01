package dev.barterah;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public final class Store {

    public static final class Offer {
        public final UUID offerer;
        public final String name;
        public final ItemStack item;
        public final long created;

        public Offer(UUID offerer, String name, ItemStack item, long created) {
            this.offerer = offerer;
            this.name = name;
            this.item = item;
            this.created = created;
        }
    }

    public static final class Listing {
        public final int id;
        public final UUID seller;
        public final String sellerName;
        public final ItemStack item;
        public final String wants;
        public final long created;
        public final Map<UUID, Offer> offers = new LinkedHashMap<>();

        public Listing(int id, UUID seller, String sellerName, ItemStack item, String wants, long created) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.wants = wants;
            this.created = created;
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Integer, Listing> listings = new TreeMap<>();
    private final Map<UUID, List<ItemStack>> claims = new HashMap<>();
    private int nextId = 1;

    public Store(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    private static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    private static ItemStack decode(String s) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(s));
    }

    // ---------- listing ----------

    public Collection<Listing> all() {
        return listings.values();
    }

    public Listing get(int id) {
        return listings.get(id);
    }

    public Listing createListing(UUID seller, String sellerName, ItemStack item, String wants) {
        Listing l = new Listing(nextId++, seller, sellerName, item, wants, System.currentTimeMillis());
        listings.put(l.id, l);
        return l;
    }

    public void remove(int id) {
        listings.remove(id);
    }

    // ---------- klaim / pengiriman item ----------

    /** Masukkan item ke antrean klaim pemain. Panggil save() lalu flush() setelahnya. */
    public void give(UUID uuid, ItemStack item) {
        claims.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item);
    }

    /** Coba kirim semua item klaim ke inventory pemain (kalau online). Return jumlah item yang masih tertinggal. */
    public int flush(UUID uuid) {
        List<ItemStack> list = claims.get(uuid);
        if (list == null || list.isEmpty()) {
            return 0;
        }
        Player p = Bukkit.getPlayer(uuid);
        if (p == null) {
            return list.size();
        }
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack item : list) {
            left.addAll(p.getInventory().addItem(item).values());
        }
        if (left.isEmpty()) {
            claims.remove(uuid);
        } else {
            claims.put(uuid, left);
        }
        save();
        return left.size();
    }

    // ---------- load / save ----------

    public void load() {
        listings.clear();
        claims.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        nextId = yml.getInt("next-id", 1);

        ConfigurationSection ls = yml.getConfigurationSection("listings");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                ConfigurationSection s = ls.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                try {
                    Listing l = new Listing(
                            Integer.parseInt(key),
                            UUID.fromString(s.getString("seller", "")),
                            s.getString("seller-name", "?"),
                            decode(s.getString("item", "")),
                            s.getString("wants", ""),
                            s.getLong("created"));
                    ConfigurationSection os = s.getConfigurationSection("offers");
                    if (os != null) {
                        for (String ok : os.getKeys(false)) {
                            ConfigurationSection o = os.getConfigurationSection(ok);
                            if (o == null) {
                                continue;
                            }
                            UUID u = UUID.fromString(ok);
                            l.offers.put(u, new Offer(u, o.getString("name", "?"),
                                    decode(o.getString("item", "")), o.getLong("created")));
                        }
                    }
                    listings.put(l.id, l);
                } catch (Exception e) {
                    plugin.getLogger().warning("Gagal memuat listing " + key + ": " + e.getMessage());
                }
            }
        }

        ConfigurationSection cs = yml.getConfigurationSection("claims");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                try {
                    UUID u = UUID.fromString(key);
                    List<ItemStack> items = new ArrayList<>();
                    for (String b : cs.getStringList(key)) {
                        items.add(decode(b));
                    }
                    if (!items.isEmpty()) {
                        claims.put(u, items);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Gagal memuat klaim " + key + ": " + e.getMessage());
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("next-id", nextId);
        for (Listing l : listings.values()) {
            String p = "listings." + l.id;
            yml.set(p + ".seller", l.seller.toString());
            yml.set(p + ".seller-name", l.sellerName);
            yml.set(p + ".item", encode(l.item));
            yml.set(p + ".wants", l.wants);
            yml.set(p + ".created", l.created);
            for (Offer o : l.offers.values()) {
                String op = p + ".offers." + o.offerer;
                yml.set(op + ".name", o.name);
                yml.set(op + ".item", encode(o.item));
                yml.set(op + ".created", o.created);
            }
        }
        for (Map.Entry<UUID, List<ItemStack>> e : claims.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : e.getValue()) {
                encoded.add(encode(item));
            }
            yml.set("claims." + e.getKey(), encoded);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Gagal menyimpan data.yml: " + e.getMessage());
        }
    }
}
