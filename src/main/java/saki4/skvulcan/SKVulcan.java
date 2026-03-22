package saki4.skvulcan;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;

public class SKVulcan extends JavaPlugin {
    private VolcanoManager volcanoManager;
    private File lootFile;
    private FileConfiguration lootConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        setupLootConfig();
        this.volcanoManager = new VolcanoManager(this);
        getCommand("skvulcan").setExecutor(new VulcanCommand(this, volcanoManager));
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        volcanoManager.startGlobalTimer();
        getLogger().info("SKVulcan готов к извержению!");
    }

    private void setupLootConfig() {
        lootFile = new File(getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            try { lootFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
    }

    public FileConfiguration getLootConfig() { return lootConfig; }
    public void saveLootConfig() {
        try { lootConfig.save(lootFile); } catch (IOException e) { e.printStackTrace(); }
    }
    public String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
    public String getMsg(String path) {
        String prefix = getConfig().getString("messages.prefix", "&6[SKVulcan] ");
        String msg = getConfig().getString("messages." + path, "");
        return color(prefix + msg);
    }
}