package ru.mirtime.contracts;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeContracts extends JavaPlugin implements CommandExecutor, Listener {

    private static final String GUI_TITLE = "&#0055ff&l⚡ &#00E5FF&lКонтракты Обмена &8| &#00FF6610/10";
    private static final int[] INPUT_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private NamespacedKey mtItemKey;

    public static class ContractHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory inv) { this.inv = inv; }
    }

    public enum Tier {
        COMMON("&#00FF66Обычный", 10),
        RARE("&#00E5FFРедкий", 28),
        EPIC("&#0055ffЭпический", 65),
        LEGENDARY("&#FFD700&lЛегендарный", 140);

        final String title;
        final int weight;
        Tier(String title, int weight) { this.title = title; this.weight = weight; }
    }

    // Индивидуальный вес (weight) для каждого приза внутри тира!
    public record RewardEntry(String cmd, String display, int weight, boolean broadcast) {}

    @Override
    public void onEnable() {
        mtItemKey = new NamespacedKey("mirtimeitems", "mt_item_id");
        if (getCommand("contracts") != null) getCommand("contracts").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MirTimeContracts v2.3.0 (Сбалансированные шансы, ContractHolder и дизайн Mir-Time) запущен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player p) openContractMenu(p);
        return true;
    }

    public void openContractMenu(Player p) {
        ContractHolder holder = new ContractHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, color(GUI_TITLE));
        holder.setInventory(inv);

        ItemStack blue = makeItem(Material.BLUE_STAINED_GLASS_PANE, "&#0055ff▪", null);
        ItemStack cyan = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF▫", null);
        ItemStack dark = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);

        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        for (int s : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 52, 53}) inv.setItem(s, blue);
        for (int s : new int[]{2, 3, 5, 6, 47, 48, 50, 51}) inv.setItem(s, cyan);
        for (int s : INPUT_SLOTS) inv.setItem(s, null);

        inv.setItem(4, makeItem(Material.SMITHING_TABLE, "&#0055ff&l⚡ &#00E5FF&lКОНТРАКТ ОБМЕНА", List.of(
                "&7Положите &#00FF6610 ванильных предметов &7в пустые",
                "&7ячейки по центру, чтобы перековать их",
                "&7в &#00E5FFкастомный артефакт &7или &#00FF66ценный лут&7!",
                "",
                " &#0055ff● &fЧем реже ресурсы — тем выше шанс",
                "   &fна &#FFD700Легендарные &fартефакты.",
                " &c● Кастомные предметы класть нельзя!"
        )));

        inv.setItem(38, makeItem(Material.NETHER_STAR, "&#0055ff[i] &#00E5FF&lЦенность ресурсов", List.of(
                " &#FFD700★ Легендарный: &fНезерит, Звезда Незера,",
                "   &fМаяк, Элитры, Тяжёлое ядро, Яйцо дракона",
                " &#0055ff★ Эпический: &fАлм/Изумр блоки, Чар. яблоко,",
                "   &fТотем, Обломки, Кристалл Энда",
                " &#00E5FF★ Редкий: &fАлмазы, Изумруды, Зол. яблоко,",
                "   &fОбсидиан, Жемчуг, Стержни, Золотые блоки",
                " &#00FF66★ Обычный: &fОстальные ванильные предметы"
        )));

        inv.setItem(42, makeItem(Material.BARRIER, "&c&l[✖] Закрыть контракт", List.of(
                "&7Все положенные предметы",
                "&7автоматически вернутся в инвентарь."
        )));

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
        if (m == Material.NETHERITE_INGOT || m == Material.NETHERITE_BLOCK || m == Material.NETHER_STAR
                || m == Material.BEACON || m == Material.ELYTRA || m == Material.DRAGON_EGG || m == Material.HEAVY_CORE) {
            return Tier.LEGENDARY;
        }
        if (m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK || m == Material.ENCHANTED_GOLDEN_APPLE
                || m == Material.TOTEM_OF_UNDYING || m == Material.NETHERITE_SCRAP || m == Material.END_CRYSTAL || m == Material.ANCIENT_DEBRIS) {
            return Tier.EPIC;
        }
        if (m == Material.DIAMOND || m == Material.EMERALD || m == Material.GOLDEN_APPLE
                || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.ENDER_PEARL
                || m == Material.BLAZE_ROD || m == Material.GHAST_TEAR || m == Material.GOLD_BLOCK) {
            return Tier.RARE;
        }
        return Tier.COMMON;
    }

    private List<RewardEntry> getPoolByTier(Tier tier) {
        return switch (tier) {
            case LEGENDARY -> List.of(
                    new RewardEntry("mtitems give %player% god_sword 1", "&#FFD700&l⚔ Меч «БОГА» XV", 3, true),
                    new RewardEntry("mtitems give %player% titan_chest 1", "&#0055ff&l🛡 Нагрудник Титана", 5, true),
                    new RewardEntry("mtitems give %player% dragon_elytra 1", "&d&l🐉 Элитры Дракона", 5, true),
                    new RewardEntry("mtitems give %player% phoenix_totem 1", "&#FF8800&l🔥 Перо Феникса", 7, true),
                    new RewardEntry("minecraft:give %player% netherite_ingot 3", "&#FFD700Незеритовый слиток (x3)", 35, false),
                    new RewardEntry("minecraft:give %player% enchanted_golden_apple 6", "&#FFD700Зачарованное яблоко (x6)", 45, false)
            );
            case EPIC -> List.of(
                    new RewardEntry("mtitems give %player% black_hole 1", "&5&l🌌 Чёрная Дыра", 5, true),
                    new RewardEntry("mtitems give %player% meteor_staff 1", "&#FF5500&l☄ Посох Метеорита", 6, true),
                    new RewardEntry("mtitems give %player% vampire_blade 1", "&c&l🩸 Клинок Вампира", 7, true),
                    new RewardEntry("mtitems give %player% thor_axe 1", "&#00E5FF&l⚡ Молот Тора", 12, false),
                    new RewardEntry("minecraft:give %player% totem_of_undying 2", "&eТотем бессмертия (x2)", 30, false),
                    new RewardEntry("minecraft:give %player% diamond_block 8", "&bАлмазный блок (x8)", 40, false)
            );
            case RARE -> List.of(
                    new RewardEntry("mtitems give %player% mega_trap 1", "&#00E5FF&l💠 Мега-Трапка 7×7", 10, false),
                    new RewardEntry("mtitems give %player% disorient 1", "&#9400d3&l🌀 Сфера Дезориентации", 12, false),
                    new RewardEntry("mtitems give %player% frost_staff 1", "&b&l❄ Ледяной Скипетр", 13, false),
                    new RewardEntry("minecraft:give %player% golden_apple 12", "&eЗолотое яблоко (x12)", 20, false),
                    new RewardEntry("minecraft:give %player% ender_pearl 16", "&3Эндер-жемчуг (x16)", 20, false),
                    new RewardEntry("minecraft:give %player% obsidian 64", "&5Обсидиан (x64)", 25, false)
            );
            case COMMON -> List.of(
                    new RewardEntry("mtitems give %player% trapka 1", "&#0055ff&l⬛ Обсидиановая Трапка", 8, false),
                    new RewardEntry("mtitems give %player% flash_pearl 1", "&#00E5FF&l👁 Око Пустоты", 10, false),
                    new RewardEntry("mtitems give %player% drill_pickaxe 1", "&#FF2A55&l⛏ Бур 3×3", 12, false),
                    new RewardEntry("minecraft:give %player% diamond 8", "&bАлмазы (x8)", 20, false),
                    new RewardEntry("minecraft:give %player% experience_bottle 64", "&aПузырёк опыта (x64)", 25, false),
                    new RewardEntry("minecraft:give %player% golden_carrot 64", "&6Золотая морковь (x64)", 25, false)
            );
        };
    }
    private void updateLivePreview(Inventory inv) {
        int filled = 0;
        Map<Tier, Integer> tierWeights = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) tierWeights.put(t, 0);

        int totalTierWeight = 0;
        for (int s : INPUT_SLOTS) {
            ItemStack it = inv.getItem(s);
            if (it != null && !it.getType().isAir()) {
                filled++;
                Tier t = getVanillaTier(it);
                int w = t.weight * Math.min(it.getAmount(), 16);
                tierWeights.put(t, tierWeights.get(t) + w);
                totalTierWeight += w;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("&#0055ff&m                              ");
        lore.add(" &#0055ff● &fЗаполнено ячеек: &#00FF66" + filled + " &8/ &f10");
        lore.add("&#0055ff&m                              ");

        if (totalTierWeight > 0) {
            lore.add("");
            lore.add("&#00E5FF&lТочные шансы на выпадение:");
            for (Tier t : Tier.values()) {
                int tWeight = tierWeights.get(t);
                if (tWeight <= 0) continue;

                double tierChance = (tWeight * 100.0) / totalTierWeight;
                List<RewardEntry> pool = getPoolByTier(t);
                int poolWeightSum = pool.stream().mapToInt(RewardEntry::weight).sum();

                for (RewardEntry entry : pool) {
                    double exactPct = tierChance * ((double) entry.weight() / poolWeightSum);
                    String pctColor = exactPct < 8.0 ? "&#FFD700&l" : (exactPct < 20.0 ? "&#00E5FF" : "&#00FF66");
                    String pctText = String.format(Locale.US, "%.1f%%", exactPct);
                    lore.add(" &8▪ " + entry.display() + " &8— " + pctColor + pctText);
                }
            }
        } else {
            lore.add("");
            lore.add("&7Положите предметы в 10 ячеек сверху,");
            lore.add("&7чтобы рассчитать шанс на каждый приз.");
        }

        inv.setItem(31, makeItem(Material.ENCHANTED_BOOK, "&#0055ff[📊] &#00E5FF&lШансы на награды", lore));
        inv.setItem(40, makeItem(
                filled == 10 ? Material.ANVIL : Material.GRAY_DYE,
                filled == 10 ? "&#0055ff&l⚡ &#00FF66&lПОДПИСАТЬ КОНТРАКТ (10/10)" : "&c&l[⏳] ЗАПОЛНИТЕ 10 ЯЧЕЕК (" + filled + "/10)",
                filled == 10
                        ? List.of("&7Все 10 ячеек готовы к перековке!", "", "&#00FF66➥ Нажмите&f, чтобы получить награду!")
                        : List.of("&7Для запуска контракта нужно заполнить", "&7все &f10 &7центральных ячеек.")
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof ContractHolder)) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isCustomArtifact(current) || isCustomArtifact(cursor)) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &cВ контракт можно класть только обычные ванильные предметы!"));
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
            List<Tier> lotteryTiers = new ArrayList<>();

            for (int s : INPUT_SLOTS) {
                ItemStack it = top.getItem(s);
                if (it != null && !it.getType().isAir()) {
                    filled++;
                    Tier t = getVanillaTier(it);
                    int tickets = t.weight * Math.min(it.getAmount(), 16);
                    for (int k = 0; k < tickets; k++) lotteryTiers.add(t);
                }
            }

            if (filled < 10) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &cЗаполните все 10 ячеек контракта! Сейчас: &e" + filled + "/10"));
                return;
            }

            for (int s : INPUT_SLOTS) top.setItem(s, null);
            p.closeInventory();

            Random rnd = new Random();
            Tier wonTier = lotteryTiers.get(rnd.nextInt(lotteryTiers.size()));
            List<RewardEntry> pool = getPoolByTier(wonTier);

            int totalPoolWeight = pool.stream().mapToInt(RewardEntry::weight).sum();
            int roll = rnd.nextInt(totalPoolWeight);
            int acc = 0;
            RewardEntry reward = pool.get(0);
            for (RewardEntry entry : pool) {
                acc += entry.weight();
                if (roll < acc) {
                    reward = entry;
                    break;
                }
            }

            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.1f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 90, 0.7, 0.8, 0.7, 0.2);

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward.cmd().replace("%player%", p.getName()));
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &#00FF66&lКОНТРАКТ ПОДПИСАН (" + wonTier.title + "&#00FF66&l)! &fВаш приз: " + reward.display()));

            if (reward.broadcast()) {
                Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &#FFD700&lДЖЕКПОТ! &fИгрок &#00E5FF" + p.getName() + " &fвыбил в контракте: " + reward.display() + "&f!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof ContractHolder)) return;
        for (int raw : e.getRawSlots()) {
            if (raw < 54 && !isInputSlot(raw)) {
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(this, () -> updateLivePreview(e.getView().getTopInventory()));
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof ContractHolder)) return;

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
