package ru.mirtime.contracts;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeContracts extends JavaPlugin implements CommandExecutor, Listener {

    private static final String GUI_TITLE = "&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8| &d&lКонтракт Обмена (10/10)";
    private static final int[] INPUT_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private NamespacedKey mtItemKey;

    public enum Tier {
        COMMON("&#00FF66Обычный", 10),
        RARE("&#00E5FFРедкий", 25),
        EPIC("&dЭпический", 60),
        LEGENDARY("&#FFD700&lЛегендарный", 120);

        final String title;
        final int weight;
        Tier(String title, int weight) { this.title = title; this.weight = weight; }
    }

    public record RewardEntry(String cmd, String display, boolean broadcast) {}

    @Override
    public void onEnable() {
        mtItemKey = new NamespacedKey("mirtimeitems", "mt_item_id");
        if (getCommand("contracts") != null) getCommand("contracts").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MirTimeContracts v2.2.0 (Шансы на каждый предмет + ванильные и кастомные призы) запущен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) openContractMenu(p);
        return true;
    }

    public void openContractMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, color(GUI_TITLE));

        ItemStack corner = makeItem(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff&l▪", null);
        ItemStack border = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        ItemStack dark = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        for (int s : new int[]{0, 8, 45, 53}) inv.setItem(s, corner);
        for (int s : new int[]{1, 2, 3, 5, 6, 7, 9, 17, 18, 26, 27, 35, 36, 44, 46, 47, 51, 52}) inv.setItem(s, border);
        for (int s : INPUT_SLOTS) inv.setItem(s, null);

        inv.setItem(4, makeItem(Material.SMITHING_TABLE, "&#00E5FF&l📜 КОНТРАКТ ОБМЕНА (КАК В CS2)", List.of(
                "&7Положите &e10 обычных предметов &7из Minecraft,",
                "&7чтобы обменять их на &bартефакт &7или &eценный лут&7!",
                "",
                " &f• Шанс каждого конкретного приза виден",
                "   &fв реальном времени в книге снизу.",
                " &c• Кастомные артефакты класть нельзя!"
        )));

        inv.setItem(42, makeItem(Material.BARRIER, "&c&l✖ Закрыть контракт", List.of("&7Все предметы вернутся в инвентарь.")));
        updateLivePreview(inv);
        p.openInventory(inv);
    }
    private boolean isInputSlot(int rawSlot) {
        for (int s : INPUT_SLOTS) if (s == rawSlot) return true;
        return false;
    }

    private boolean isCustomArtifact(ItemStack is) {
        if (is == null || !is.hasItemMeta()) return false;
        return is.getItemMeta().getPersistentDataContainer().has(mtItemKey, PersistentDataType.STRING);
    }

    private Tier getVanillaTier(ItemStack is) {
        Material m = is.getType();
        if (m == Material.NETHERITE_INGOT || m == Material.NETHERITE_BLOCK || m == Material.NETHER_STAR || m == Material.BEACON || m == Material.ELYTRA || m == Material.DRAGON_EGG || m == Material.HEAVY_CORE) {
            return Tier.LEGENDARY;
        }
        if (m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK || m == Material.ENCHANTED_GOLDEN_APPLE || m == Material.TOTEM_OF_UNDYING || m == Material.NETHERITE_SCRAP || m == Material.END_CRYSTAL || m == Material.ANCIENT_DEBRIS) {
            return Tier.EPIC;
        }
        if (m == Material.DIAMOND || m == Material.EMERALD || m == Material.GOLDEN_APPLE || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.ENDER_PEARL || m == Material.BLAZE_ROD || m == Material.GHAST_TEAR || m == Material.GOLD_BLOCK) {
            return Tier.RARE;
        }
        return Tier.COMMON;
    }

    private List<RewardEntry> getPoolByTier(Tier tier) {
        return switch (tier) {
            case LEGENDARY -> List.of(
                    new RewardEntry("mtitems give %player% god_sword 1", "&#FFD700&l⚔ Меч «БОГА» XV", true),
                    new RewardEntry("mtitems give %player% titan_chest 1", "&#0055ff&l🛡 Нагрудник Титана", true),
                    new RewardEntry("mtitems give %player% dragon_elytra 1", "&d&l🐉 Элитры Дракона", true),
                    new RewardEntry("mtitems give %player% phoenix_totem 1", "&#FF8800&l🔥 Перо Феникса", true),
                    new RewardEntry("minecraft:give %player% netherite_ingot 4", "&#FFD700Незеритовый слиток (x4)", false),
                    new RewardEntry("minecraft:give %player% enchanted_golden_apple 8", "&#FFD700Зачарованное яблоко (x8)", false)
            );
            case EPIC -> List.of(
                    new RewardEntry("mtitems give %player% black_hole 1", "&5&l🌌 Чёрная Дыра", true),
                    new RewardEntry("mtitems give %player% meteor_staff 1", "&#FF5500&l☄ Посох Метеорита", true),
                    new RewardEntry("mtitems give %player% vampire_blade 1", "&c&l🩸 Клинок Вампира", true),
                    new RewardEntry("mtitems give %player% thor_axe 1", "&#00E5FF&l⚡ Молот Тора", false),
                    new RewardEntry("minecraft:give %player% totem_of_undying 3", "&eТотем бессмертия (x3)", false),
                    new RewardEntry("minecraft:give %player% diamond_block 12", "&bАлмазный блок (x12)", false)
            );
            case RARE -> List.of(
                    new RewardEntry("mtitems give %player% mega_trap 1", "&#00E5FF&l💠 Мега-Трапка 7×7", false),
                    new RewardEntry("mtitems give %player% disorient 1", "&#9400d3&l🌀 Сфера Дезориентации", false),
                    new RewardEntry("mtitems give %player% frost_staff 1", "&b&l❄ Ледяной Скипетр", false),
                    new RewardEntry("minecraft:give %player% golden_apple 16", "&eЗолотое яблоко (x16)", false),
                    new RewardEntry("minecraft:give %player% ender_pearl 16", "&3Эндер-жемчуг (x16)", false),
                    new RewardEntry("minecraft:give %player% obsidian 64", "&5Обсидиан (x64)", false)
            );
            case COMMON -> List.of(
                    new RewardEntry("mtitems give %player% trapka 1", "&#0055ff&l⬛ Обсидиановая Трапка", false),
                    new RewardEntry("mtitems give %player% flash_pearl 1", "&#00E5FF&l👁 Око Пустоты", false),
                    new RewardEntry("mtitems give %player% drill_pickaxe 1", "&#FF2A55&l⛏ Бур 3×3", false),
                    new RewardEntry("minecraft:give %player% diamond 12", "&bАлмазы (x12)", false),
                    new RewardEntry("minecraft:give %player% experience_bottle 64", "&aПузырёк опыта (x64)", false),
                    new RewardEntry("minecraft:give %player% golden_carrot 64", "&6Золотая морковь (x64)", false)
            );
        };
    }

    private void updateLivePreview(Inventory inv) {
        int filled = 0;
        Map<Tier, Integer> weights = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) weights.put(t, 0);

        int totalWeight = 0;
        for (int s : INPUT_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it != null && !it.getType().isAir()) {
                filled++;
                Tier t = getVanillaTier(it);
                int w = t.weight * Math.min(it.getAmount(), 16);
                weights.put(t, weights.get(t) + w);
                totalWeight += w;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Заполнено слотов: &#00E5FF" + filled + " &8/ &f10");
        lore.add("");

        if (totalWeight > 0) {
            lore.add("&fВаши точные шансы на выпадение:");
            for (Tier t : Tier.values()) {
                int tierW = weights.get(t);
                if (tierW <= 0) continue;
                List<RewardEntry> pool = getPoolByTier(t);
                double eachItemPct = ((tierW * 100.0) / totalWeight) / pool.size();
                String pctText = String.format(Locale.US, "%.1f%%", eachItemPct);
                for (RewardEntry entry : pool) {
                    lore.add(" &8▪ " + entry.display() + " &8— &#00FF66&l" + pctText);
                }
            }
        } else {
            lore.add("&7Положите любые предметы в 10 слотов,");
            lore.add("&7чтобы увидеть шанс на каждую награду.");
        }

        inv.setItem(31, makeItem(Material.ENCHANTED_BOOK, "&#00E5FF&l📊 ВАШИ ШАНСЫ НА ВЫПАДЕНИЕ", lore));
        inv.setItem(40, makeItem(
                filled == 10 ? Material.ANVIL : Material.GRAY_DYE,
                filled == 10 ? "&#00FF66&l⚡ ПОДПИСАТЬ КОНТРАКТ (10/10)" : "&c&l⏳ ЗАПОЛНИТЕ ВСЕ 10 СЛОТОВ (" + filled + "/10)",
                filled == 10 ? List.of("&7Все 10 ячеек заполнены!", "&#00FF66▶ Нажмите для запуска перековки!") : List.of("&7Для обмена как в CS2 должны быть", "&7заполнены все 10 ячеек сверху.")
        ));
    }
    @EventHandler
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(color(GUI_TITLE))) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isCustomArtifact(current) || isCustomArtifact(cursor)) {
            e.setCancelled(true);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cВ контракт можно класть только обычные предметы из Minecraft!"));
            return;
        }

        int raw = e.getRawSlot();
        if (raw >= 54 || isInputSlot(raw)) {
            Bukkit.getScheduler().runTask(this, () -> updateLivePreview(e.getView().getTopInventory()));
            return;
        }

        e.setCancelled(true);
        if (raw == 42) {
            p.closeInventory();
            return;
        }

        if (raw == 40) {
            Inventory top = e.getView().getTopInventory();
            int filled = 0;
            List<Tier> lotteryPool = new ArrayList<>();

            for (int s : INPUT_SLOTS) {
                ItemStack it = top.getItem(s);
                if (it != null && !it.getType().isAir()) {
                    filled++;
                    Tier t = getVanillaTier(it);
                    int tickets = t.weight * Math.min(it.getAmount(), 16);
                    for (int k = 0; k < tickets; k++) lotteryPool.add(t);
                }
            }

            if (filled < 10) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &cЗаполните все 10 ячеек контракта! Сейчас заполнено: &e" + filled + "/10"));
                return;
            }

            for (int s : INPUT_SLOTS) top.setItem(s, null);
            p.closeInventory();

            Random rnd = new Random();
            Tier wonTier = lotteryPool.get(rnd.nextInt(lotteryPool.size()));
            List<RewardEntry> pool = getPoolByTier(wonTier);
            RewardEntry reward = pool.get(rnd.nextInt(pool.size()));

            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 90, 0.7, 0.8, 0.7, 0.2);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.cmd().replace("%player%", p.getName()));
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#00FF66&lКОНТРАКТ ПОДПИСАН (" + wonTier.title + "&#00FF66&l)! &fВаш приз: " + reward.display()));

            if (reward.broadcast()) {
                Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lMIR-TIME &8» &#FFD700&lДЖЕКПОТ В КОНТРАКТЕ! &fИгрок &#00E5FF" + p.getName() + " &fвыбил из 10 предметов: " + reward.display() + "&f!"));
            }
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTitle().equals(color(GUI_TITLE))) {
            Bukkit.getScheduler().runTask(this, () -> updateLivePreview(e.getView().getTopInventory()));
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(color(GUI_TITLE))) return;

        Inventory top = e.getInventory();
        for (int s : INPUT_SLOTS) {
            ItemStack item = top.getItem(s);
            if (item != null && !item.getType().isAir()) {
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }

    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta m = is.getItemMeta();
        if (m != null) {
            m.setDisplayName(color(name));
            if (lore != null) {
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(color(s));
                m.setLore(l);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            is.setItemMeta(m);
        }
        return is;
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher m = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder rep = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) rep.append('§').append(c);
            m.appendReplacement(sb, rep.toString());
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
}
