package ru.mirtime.contracts;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MirTimeContracts extends JavaPlugin implements CommandExecutor, Listener {

    private static final String GUI_TITLE = "&#0055ff&l⚡ &#00E5FF&lКонтракты Обмена &8| &#00FF6610/10";
    private static final int[] INPUT_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private NamespacedKey mtItemKey;

    public static class ContractHolder implements InventoryHolder {
        private Inventory inv;
        public boolean animating = false;
        @Override public Inventory getInventory() { return inv; }
        public void setInventory(Inventory inv) { this.inv = inv; }
    }

    public enum Tier {
        TRASH("&7Мусорный (без артефактов)", 2),
        COMMON("&#00FF66Обычный", 10),
        RARE("&#00E5FFРедкий", 32),
        EPIC("&#0055ffЭпический", 75),
        LEGENDARY("&#FFD700&lЛегендарный", 160);

        final String title;
        final int weight;
        Tier(String title, int weight) { this.title = title; this.weight = weight; }
    }

    public record RewardEntry(String cmd, String display, Material icon, int weight, boolean broadcast) {}

    @Override
    public void onEnable() {
        mtItemKey = new NamespacedKey("mirtimeitems", "mt_item_id");
        if (getCommand("contracts") != null) getCommand("contracts").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("MirTimeContracts v2.6.0 (GUI-анимация перековки CS2 + 10 по 1 шт.) запущен!");
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

        inv.setItem(4, makeItem(Material.SMITHING_TABLE, "&#0055ff&l⚡ &#00E5FF&lКОНТРАКТ ОБМЕНА &8(&#00FF6610 по 1 шт.&8)", List.of(
                "&7Положите &#00FF6610 предметов по 1 штуке &7в ячейки",
                "&7по центру, чтобы запустить перековку!",
                "",
                " &#0055ff● &fВ каждую ячейку кладётся строго &#00FF661 шт.",
                " &#0055ff● &fИз земли, булыжника и дешёвых блоков",
                "   &cкастомные артефакты НЕ выпадают!",
                " &#0055ff● &fЧем ценнее ресурсы — тем круче лут."
        )));

        inv.setItem(38, makeItem(Material.NETHER_STAR, "&#0055ff[i] &#00E5FF&lЦенность предметов", List.of(
                " &#FFD700★ Легендарный: &fНезерит, Звезда Незера,",
                "   &fМаяк, Элитры, Тяжёлое ядро, Яйцо дракона",
                " &#0055ff★ Эпический: &fАлм/Изумр блоки, Чар. яблоко,",
                "   &fТотем, Обломки, Кристалл Энда, Сердце моря",
                " &#00E5FF★ Редкий: &fАлмазы, Изумруды, Зол. яблоко,",
                "   &fОбсидиан, Жемчуг, Стержни, Шалкеры, Зол. блоки",
                " &#00FF66★ Обычный: &fЖелезо, Золото, Медь, Уголь,",
                "   &fРедстоун, Лазурит, Кварц, Порох, Кожа",
                " &7★ Мусор: &fЗемля, камень, дерево, песок и др."
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
        String name = m.name();

        if (m == Material.NETHERITE_INGOT || m == Material.NETHERITE_BLOCK || m == Material.NETHER_STAR
                || m == Material.BEACON || m == Material.ELYTRA || m == Material.DRAGON_EGG
                || m == Material.HEAVY_CORE || name.startsWith("NETHERITE_")) {
            return Tier.LEGENDARY;
        }

        if (m == Material.DIAMOND_BLOCK || m == Material.EMERALD_BLOCK || m == Material.ENCHANTED_GOLDEN_APPLE
                || m == Material.TOTEM_OF_UNDYING || m == Material.NETHERITE_SCRAP || m == Material.END_CRYSTAL
                || m == Material.ANCIENT_DEBRIS || m == Material.HEART_OF_THE_SEA || m == Material.CONDUIT
                || m == Material.WITHER_SKELETON_SKULL) {
            return Tier.EPIC;
        }

        if (m == Material.DIAMOND || m == Material.EMERALD || m == Material.GOLDEN_APPLE
                || m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN || m == Material.ENDER_PEARL
                || m == Material.BLAZE_ROD || m == Material.GHAST_TEAR || m == Material.GOLD_BLOCK
                || m == Material.SHULKER_SHELL || name.endsWith("SHULKER_BOX") || m == Material.TRIDENT
                || m == Material.ENCHANTED_BOOK || m == Material.EXPERIENCE_BOTTLE || m == Material.PHANTOM_MEMBRANE
                || name.startsWith("DIAMOND_")) {
            return Tier.RARE;
        }

        if (m == Material.IRON_INGOT || m == Material.IRON_BLOCK || m == Material.GOLD_INGOT
                || m == Material.COPPER_INGOT || m == Material.COPPER_BLOCK || m == Material.COAL
                || m == Material.COAL_BLOCK || m == Material.REDSTONE || m == Material.REDSTONE_BLOCK
                || m == Material.LAPIS_LAZULI || m == Material.LAPIS_BLOCK || m == Material.QUARTZ
                || m == Material.QUARTZ_BLOCK || m == Material.AMETHYST_SHARD || m == Material.GUNPOWDER
                || m == Material.SLIME_BALL || m == Material.MAGMA_CREAM || m == Material.LEATHER
                || m == Material.ENDER_EYE || m == Material.GOLDEN_CARROT || m == Material.GLOWSTONE_DUST
                || m == Material.TNT || m == Material.BOOKSHELF || m == Material.ANVIL
                || name.startsWith("IRON_") || name.startsWith("GOLDEN_")) {
            return Tier.COMMON;
        }

        return Tier.TRASH;
    }

    private List<RewardEntry> getPoolByTier(Tier tier) {
        return switch (tier) {
            case LEGENDARY -> List.of(
                    new RewardEntry("mtitems give %player% god_sword 1", "&#FFD700&l⚔ Меч «БОГА» XV", Material.NETHERITE_SWORD, 3, true),
                    new RewardEntry("mtitems give %player% titan_chest 1", "&#0055ff&l🛡 Нагрудник Титана", Material.NETHERITE_CHESTPLATE, 5, true),
                    new RewardEntry("mtitems give %player% dragon_elytra 1", "&d&l🐉 Элитры Дракона", Material.ELYTRA, 5, true),
                    new RewardEntry("mtitems give %player% phoenix_totem 1", "&#FF8800&l🔥 Перо Феникса", Material.BLAZE_POWDER, 7, true),
                    new RewardEntry("minecraft:give %player% netherite_ingot 2", "&#FFD700Незеритовый слиток (x2)", Material.NETHERITE_INGOT, 35, false),
                    new RewardEntry("minecraft:give %player% enchanted_golden_apple 4", "&#FFD700Зачарованное яблоко (x4)", Material.ENCHANTED_GOLDEN_APPLE, 45, false)
            );
            case EPIC -> List.of(
                    new RewardEntry("mtitems give %player% black_hole 1", "&5&l🌌 Чёрная Дыра", Material.ENDER_EYE, 5, true),
                    new RewardEntry("mtitems give %player% meteor_staff 1", "&#FF5500&l☄ Посох Метеорита", Material.BLAZE_ROD, 6, true),
                    new RewardEntry("mtitems give %player% vampire_blade 1", "&c&l🩸 Клинок Вампира", Material.DIAMOND_SWORD, 7, true),
                    new RewardEntry("mtitems give %player% thor_axe 1", "&#00E5FF&l⚡ Молот Тора", Material.DIAMOND_AXE, 12, false),
                    new RewardEntry("minecraft:give %player% totem_of_undying 2", "&eТотем бессмертия (x2)", Material.TOTEM_OF_UNDYING, 30, false),
                    new RewardEntry("minecraft:give %player% diamond_block 6", "&bАлмазный блок (x6)", Material.DIAMOND_BLOCK, 40, false)
            );
            case RARE -> List.of(
                    new RewardEntry("mtitems give %player% mega_trap 1", "&#00E5FF&l💠 Мега-Трапка 7×7", Material.BEACON, 10, false),
                    new RewardEntry("mtitems give %player% disorient 1", "&#9400d3&l🌀 Сфера Дезориентации", Material.HEART_OF_THE_SEA, 12, false),
                    new RewardEntry("mtitems give %player% frost_staff 1", "&b&l❄ Ледяной Скипетр", Material.BREEZE_ROD, 13, false),
                    new RewardEntry("minecraft:give %player% golden_apple 10", "&eЗолотое яблоко (x10)", Material.GOLDEN_APPLE, 20, false),
                    new RewardEntry("minecraft:give %player% ender_pearl 16", "&3Эндер-жемчуг (x16)", Material.ENDER_PEARL, 20, false),
                    new RewardEntry("minecraft:give %player% obsidian 64", "&5Обсидиан (x64)", Material.OBSIDIAN, 25, false)
            );
            case COMMON -> List.of(
                    new RewardEntry("mtitems give %player% trapka 1", "&#0055ff&l⬛ Обсидиановая Трапка", Material.OBSIDIAN, 6, false),
                    new RewardEntry("mtitems give %player% flash_pearl 1", "&#00E5FF&l👁 Око Пустоты", Material.ENDER_PEARL, 8, false),
                    new RewardEntry("mtitems give %player% drill_pickaxe 1", "&#FF2A55&l⛏ Бур 3×3", Material.IRON_PICKAXE, 10, false),
                    new RewardEntry("minecraft:give %player% diamond 6", "&bАлмазы (x6)", Material.DIAMOND, 22, false),
                    new RewardEntry("minecraft:give %player% experience_bottle 48", "&aПузырёк опыта (x48)", Material.EXPERIENCE_BOTTLE, 27, false),
                    new RewardEntry("minecraft:give %player% golden_carrot 48", "&6Золотая морковь (x48)", Material.GOLDEN_CARROT, 27, false)
            );
            case TRASH -> List.of(
                    new RewardEntry("minecraft:give %player% iron_ingot 16", "&fЖелезный слиток (x16)", Material.IRON_INGOT, 25, false),
                    new RewardEntry("minecraft:give %player% gold_ingot 12", "&eЗолотой слиток (x12)", Material.GOLD_INGOT, 20, false),
                    new RewardEntry("minecraft:give %player% experience_bottle 16", "&aПузырёк опыта (x16)", Material.EXPERIENCE_BOTTLE, 25, false),
                    new RewardEntry("minecraft:give %player% cooked_beef 32", "&6Жареная говядина (x32)", Material.COOKED_BEEF, 30, false)
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
                tierWeights.put(t, tierWeights.get(t) + t.weight);
                totalTierWeight += t.weight;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("&#0055ff&m                              ");
        lore.add(" &#0055ff● &fЗаполнено ячеек: &#00FF66" + filled + " &8/ &f10 &7(по 1 шт.)");
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
            lore.add("&7Положите 10 предметов (по 1 шт.) сверху,");
            lore.add("&7чтобы увидеть шанс на каждую награду.");
        }

        inv.setItem(31, makeItem(Material.ENCHANTED_BOOK, "&#0055ff[📊] &#00E5FF&lШансы на награды", lore));
        inv.setItem(40, makeItem(
                filled == 10 ? Material.ANVIL : Material.GRAY_DYE,
                filled == 10 ? "&#0055ff&l⚡ &#00FF66&lПОДПИСАТЬ КОНТРАКТ (10/10)" : "&c&l[⏳] ЗАПОЛНИТЕ 10 ЯЧЕЕК (" + filled + "/10)",
                filled == 10
                        ? List.of("&7Все 10 ячеек готовы к перековке!", "", "&#00FF66➥ Нажмите&f, чтобы запустить анимацию!")
                        : List.of("&7Для запуска контракта нужно заполнить", "&7все &f10 &7центральных ячеек (по 1 шт.).")
        ));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof ContractHolder holder)) return;

        if (holder.animating) {
            e.setCancelled(true);
            return;
        }

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        if (isCustomArtifact(current) || isCustomArtifact(cursor)) {
            e.setCancelled(true);
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &cВ контракт можно класть только обычные ванильные предметы!"));
            return;
        }

        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();

        if (raw >= 54) {
            if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
                e.setCancelled(true);
                if (current == null || current.getType().isAir()) return;
                for (int s : INPUT_SLOTS) {
                    ItemStack inSlot = top.getItem(s);
                    if (inSlot == null || inSlot.getType().isAir()) {
                        ItemStack single = current.clone();
                        single.setAmount(1);
                        top.setItem(s, single);
                        current.setAmount(current.getAmount() - 1);
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.3f);
                        updateLivePreview(top);
                        return;
                    }
                }
            }
            return;
        }

        if (isInputSlot(raw)) {
            e.setCancelled(true);
            ItemStack existing = top.getItem(raw);

            if ((existing == null || existing.getType().isAir()) && cursor != null && !cursor.getType().isAir()) {
                ItemStack single = cursor.clone();
                single.setAmount(1);
                top.setItem(raw, single);
                cursor.setAmount(cursor.getAmount() - 1);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.3f);
                updateLivePreview(top);
                return;
            }

            if (existing != null && !existing.getType().isAir() && (cursor == null || cursor.getType().isAir())) {
                top.setItem(raw, null);
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(existing);
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 0.9f);
                updateLivePreview(top);
                return;
            }

            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.2f);
            p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &eВ каждую ячейку можно положить строго 1 предмет!"));
            return;
        }

        e.setCancelled(true);
        if (raw == 42) {
            p.closeInventory();
            return;
        }

        if (raw == 40) {
            int filled = 0;
            List<Tier> lotteryTiers = new ArrayList<>();

            for (int s : INPUT_SLOTS) {
                ItemStack it = top.getItem(s);
                if (it != null && !it.getType().isAir()) {
                    filled++;
                    Tier t = getVanillaTier(it);
                    for (int k = 0; k < t.weight; k++) lotteryTiers.add(t);
                }
            }

            if (filled < 10) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &cЗаполните все 10 ячеек контракта (по 1 шт.)! Сейчас: &e" + filled + "/10"));
                return;
            }

            holder.animating = true;

            Random rnd = new Random();
            Tier wonTier = lotteryTiers.get(rnd.nextInt(lotteryTiers.size()));
            List<RewardEntry> pool = getPoolByTier(wonTier);

            int totalPoolWeight = pool.stream().mapToInt(RewardEntry::weight).sum();
            int roll = rnd.nextInt(totalPoolWeight);
            int acc = 0;
            RewardEntry finalReward = pool.get(0);
            for (RewardEntry entry : pool) {
                acc += entry.weight();
                if (roll < acc) {
                    finalReward = entry;
                    break;
                }
            }

            startForgeAnimation(p, top, holder, wonTier, pool, finalReward);
        }
    }
    private void startForgeAnimation(Player p, Inventory top, ContractHolder holder, Tier wonTier, List<RewardEntry> pool, RewardEntry finalReward) {
        top.setItem(40, makeItem(Material.ORANGE_STAINED_GLASS_PANE, "&#00E5FF&l⟳ ИДЁТ ПЕРЕКОВКА КОНТРАКТА...", null));
        top.setItem(42, makeItem(Material.BLACK_STAINED_GLASS_PANE, " ", null));

        int[] collapseOrder = {11, 20, 15, 24, 12, 21, 14, 23, 13};
        ItemStack cyanGlow = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&#00E5FF&l✦", null);
        ItemStack limeGlow = makeItem(Material.LIME_STAINED_GLASS_PANE, "&#00FF66&l⚡", null);
        Random rnd = new Random();

        new BukkitRunnable() {
            int step = 0;
            boolean rewarded = false;

            @Override
            public void run() {
                if (!p.isOnline()) {
                    giveRewardNow();
                    cancel();
                    return;
                }

                if (step < collapseOrder.length) {
                    int s = collapseOrder[step];
                    top.setItem(s, (step % 2 == 0) ? cyanGlow : limeGlow);
                    top.setItem(22, makeItem(Material.NETHER_STAR, "&#0055ff&l⚡ &#00E5FF&lСлияние ресурсов... &8(&#00FF66" + ((step + 1) * 10) + "%&8)", null));
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f + (step * 0.12f));
                } else if (step < 21) {
                    RewardEntry fake = pool.get(rnd.nextInt(pool.size()));
                    top.setItem(22, makeItem(fake.icon(), fake.display(), List.of("&7Определение награды...")));
                    for (int s : collapseOrder) {
                        top.setItem(s, (step % 2 == 0) ? limeGlow : cyanGlow);
                    }
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.0f + ((step - 9) * 0.08f));
                } else if (step == 21) {
                    for (int s : collapseOrder) top.setItem(s, limeGlow);
                    top.setItem(22, makeItem(finalReward.icon(), finalReward.display(), List.of(
                            "",
                            "&#00FF66&l✔ ВАША НАГРАДА ПОЛУЧЕНА!"
                    )));
                    top.setItem(40, makeItem(Material.EMERALD_BLOCK, "&#00FF66&l✔ КОНТРАКТ ВЫПОЛНЕН!", null));
                    giveRewardNow();
                } else if (step >= 26) {
                    holder.animating = false;
                    if (p.getOpenInventory().getTopInventory().equals(top)) {
                        p.closeInventory();
                    }
                    cancel();
                }

                step++;
            }

            private void giveRewardNow() {
                if (rewarded) return;
                rewarded = true;
                for (int s : INPUT_SLOTS) top.setItem(s, null);

                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 90, 0.7, 0.8, 0.7, 0.2);

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalReward.cmd().replace("%player%", p.getName()));
                p.sendMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &#00FF66&lКОНТРАКТ ПОДПИСАН (" + wonTier.title + "&#00FF66&l)! &fВаш приз: " + finalReward.display()));

                if (finalReward.broadcast()) {
                    Bukkit.broadcastMessage(color("&#0055ff&l⚡ &#00E5FF&lКОНТРАКТЫ &8» &#FFD700&lДЖЕКПОТ! &fИгрок &#00E5FF" + p.getName() + " &fвыбил в контракте: " + finalReward.display() + "&f!"));
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof ContractHolder) {
            for (int raw : e.getRawSlots()) {
                if (raw < 54) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof ContractHolder holder)) return;
        if (holder.animating) return;

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
