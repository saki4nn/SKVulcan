package saki4.skvulcan;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public class GuiListener implements Listener {
    private final SKVulcan plugin;
    private final Map<UUID, String> awaitingAmount = new HashMap<>();
    private final Map<UUID, String> awaitingRepeats = new HashMap<>();

    public GuiListener(SKVulcan plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = plugin.color(plugin.getConfig().getString("messages.gui_title", "&0Настройка лута SKVulcan"));
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();

        // Добавление предмета из инвентаря игрока
        if (e.getClickedInventory() == e.getView().getBottomInventory()) {
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                saveItem(e.getCurrentItem().clone(), 50.0);
                p.performCommand("skvulcan");
            }
            return;
        }

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        String key = item.getType().name();
        double chance = plugin.getLootConfig().getDouble("items." + key + ".chance", 50.0);

        // УДАЛЕНИЕ (Клавиша Q)
        if (e.getClick() == ClickType.DROP) {
            plugin.getLootConfig().set("items." + key, null);
            plugin.saveLootConfig();
            p.performCommand("skvulcan");
            return;
        }

        // Изменение шанса (ЛКМ / ПКМ)
        if (e.getClick() == ClickType.LEFT) {
            chance += e.isShiftClick() ? 10.0 : 1.0;
        } else if (e.getClick() == ClickType.RIGHT) {
            chance -= e.isShiftClick() ? 10.0 : 1.0;
        }
        // Кол-во в стаке (Колесико / Middle)
        else if (e.getClick() == ClickType.MIDDLE) {
            p.closeInventory();
            p.sendMessage(plugin.getMsg("ask_amount"));
            awaitingAmount.put(p.getUniqueId(), key);
            return;
        }
        // Повторы вылета (Клавиша F)
        else if (e.getClick() == ClickType.SWAP_OFFHAND) {
            p.closeInventory();
            p.sendMessage(plugin.getMsg("ask_repeats"));
            awaitingRepeats.put(p.getUniqueId(), key);
            return;
        }

        // Сохранение изменений шанса
        plugin.getLootConfig().set("items." + key + ".chance", Math.max(0.1, Math.min(100.0, chance)));
        plugin.saveLootConfig();
        p.performCommand("skvulcan");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        if (awaitingAmount.containsKey(uuid) || awaitingRepeats.containsKey(uuid)) {
            e.setCancelled(true);
            try {
                int val = Integer.parseInt(e.getMessage());
                if (awaitingAmount.containsKey(uuid)) {
                    String key = awaitingAmount.remove(uuid);
                    ItemStack is = plugin.getLootConfig().getItemStack("items." + key + ".item");
                    if (is != null) {
                        is.setAmount(Math.max(1, Math.min(64, val)));
                        plugin.getLootConfig().set("items." + key + ".item", is);
                    }
                } else {
                    String key = awaitingRepeats.remove(uuid);
                    plugin.getLootConfig().set("items." + key + ".repeats", Math.max(1, val));
                }
                plugin.saveLootConfig();
                p.sendMessage(plugin.getMsg("saved"));
            } catch (Exception ex) {
                p.sendMessage(plugin.getMsg("error_number"));
                awaitingAmount.remove(uuid);
                awaitingRepeats.remove(uuid);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> p.performCommand("skvulcan"));
        }
    }

    private void saveItem(ItemStack is, double chance) {
        String key = is.getType().name();
        plugin.getLootConfig().set("items." + key + ".item", is);
        plugin.getLootConfig().set("items." + key + ".chance", chance);
        plugin.getLootConfig().set("items." + key + ".repeats", 2);
        plugin.saveLootConfig();
    }
}