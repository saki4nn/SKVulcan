package saki4.skvulcan;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

public class VulcanCommand implements CommandExecutor {
    private final SKVulcan plugin;
    private final VolcanoManager manager;

    public VulcanCommand(SKVulcan plugin, VolcanoManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;

        if (!p.hasPermission("skvulcan.admin")) {
            p.sendMessage(plugin.getMsg("no_perms"));
            return true;
        }

        if (args.length == 0) {
            openGui(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start":
                manager.spawnVolcano();
                break;
            case "stop":
                manager.stopVolcano();
                break;
            case "tp":
                if (manager.isActive() && manager.getLoc() != null) {
                    p.teleport(manager.getLoc().clone().add(0, 15, 0));
                    p.sendMessage(plugin.getMsg("tp_success"));
                } else p.sendMessage(plugin.getMsg("tp_fail"));
                break;
            case "delay":
                // Если просто /skvulcan delay
                if (args.length == 1) {
                    if (manager.isActive() && manager.getLoc() != null) {
                        Location l = manager.getLoc();
                        String msg = plugin.getMsg("delay_active")
                                .replace("{x}", String.valueOf(l.getBlockX()))
                                .replace("{y}", String.valueOf(l.getBlockY()))
                                .replace("{z}", String.valueOf(l.getBlockZ()));
                        p.sendMessage(msg);
                    } else {
                        int seconds = manager.timeUntilEruption;
                        if (seconds == -1) seconds = plugin.getConfig().getInt("times.delay", 7200);
                        p.sendMessage(plugin.getMsg("delay_info").replace("{time}", formatTime(seconds)));
                    }
                    break;
                }
                // Установка времени: /skvulcan delay <секунды>
                try {
                    int seconds = Integer.parseInt(args[1]);
                    manager.timeUntilEruption = Math.max(0, seconds);
                    p.sendMessage(plugin.getMsg("delay_set").replace("{time}", String.valueOf(seconds)));
                } catch (NumberFormatException ex) {
                    p.sendMessage(plugin.getMsg("error_number"));
                }
                break;
        }
        return true;
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        if (m > 0) return m + "м " + s + "с";
        return s + "с";
    }

    public void openGui(Player p) {
        String title = plugin.color(plugin.getConfig().getString("messages.gui_title", "&0Настройка лута SKVulcan"));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        if (plugin.getLootConfig().getConfigurationSection("items") != null) {
            for (String key : plugin.getLootConfig().getConfigurationSection("items").getKeys(false)) {
                ItemStack is = plugin.getLootConfig().getItemStack("items." + key + ".item");
                if (is == null) continue;
                is = is.clone();
                double ch = plugin.getLootConfig().getDouble("items." + key + ".chance");
                int rep = plugin.getLootConfig().getInt("items." + key + ".repeats", 2);
                ItemMeta m = is.getItemMeta();
                if (m != null) {
                    m.setLore(Arrays.asList(
                            "§7Шанс: §a" + ch + "%",
                            "§7Повторов при дропе: §e" + rep,
                            "§bЛКМ: +1% | §6ПКМ: -1% (Shift: 10%)",
                            "§eКолесико: Кол-во в стаке",
                            "§aF: Повторы дропа",
                            "§cQ: Удалить"
                    ));
                    is.setItemMeta(m);
                }
                inv.addItem(is);
            }
        }
        p.openInventory(inv);
    }
}