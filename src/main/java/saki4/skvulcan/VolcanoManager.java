package saki4.skvulcan;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.*;

public class VolcanoManager {
    private final SKVulcan plugin;
    private boolean active = false;
    private Location loc = null;
    private BossBar bossBar;
    private Clipboard terrainBackup;
    private final String REG_NAME = "skvulcan_event";
    private final List<ItemStack> lootPool = new ArrayList<>();

    public int timeUntilEruption = -1;

    public VolcanoManager(SKVulcan plugin) { this.plugin = plugin; }

    public void startGlobalTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (timeUntilEruption == -1) {
                    timeUntilEruption = plugin.getConfig().getInt("times.delay", 7200);
                }

                if (!active) {
                    if (timeUntilEruption > 0) timeUntilEruption--;
                    else {
                        spawnVolcano();
                        timeUntilEruption = plugin.getConfig().getInt("times.delay", 7200);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void spawnVolcano() {
        if (active) return;
        active = true;

        World world = Bukkit.getWorld(plugin.getConfig().getString("spawn.world", "world"));
        if (plugin.getConfig().getBoolean("spawn.static", false)) {
            loc = new Location(world,
                    plugin.getConfig().getInt("spawn.x"),
                    plugin.getConfig().getInt("spawn.y"),
                    plugin.getConfig().getInt("spawn.z"));
        } else {
            Random r = new Random();
            int x = r.nextInt(2000) - 1000;
            int z = r.nextInt(2000) - 1000;
            loc = new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z);
        }

        backupAndProtect(loc);
        pasteSchematic(loc);

        String msg = plugin.getMsg("spawned")
                .replace("{x}", String.valueOf(loc.getBlockX()))
                .replace("{z}", String.valueOf(loc.getBlockZ()));
        Bukkit.broadcastMessage(msg);

        bossBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        int waitTime = plugin.getConfig().getInt("times.open", 120);
        new BukkitRunnable() {
            int timeLeft = waitTime;
            @Override
            public void run() {
                if (!active) { this.cancel(); return; }
                if (timeLeft <= 0) {
                    this.cancel();
                    startEruption();
                    return;
                }
                updateBossBar("bossbar.waiting", timeLeft, waitTime);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    private void startEruption() {
        int eruptTime = plugin.getConfig().getInt("times.eruption", 30);
        prepareLoot();
        new BukkitRunnable() {
            int ticksLeft = eruptTime * 2;
            @Override
            public void run() {
                if (!active) { this.cancel(); return; }
                if (ticksLeft <= 0) {
                    this.cancel();
                    scheduleRemoval();
                    return;
                }
                if (ticksLeft % 2 == 0) {
                    updateBossBar("bossbar.erupting", ticksLeft / 2, eruptTime);
                }
                loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 10, 0), 100, 1, 2, 1, 0.1);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                throwLootTick(ticksLeft);
                ticksLeft--;
            }
        }.runTaskTimer(plugin, 0, 10L);
    }

    private void prepareLoot() {
        lootPool.clear();
        if (plugin.getLootConfig().getConfigurationSection("items") == null) return;
        Random r = new Random();
        for (String key : plugin.getLootConfig().getConfigurationSection("items").getKeys(false)) {
            ItemStack item = plugin.getLootConfig().getItemStack("items." + key + ".item");
            if (item == null) continue;
            double chance = plugin.getLootConfig().getDouble("items." + key + ".chance");
            int repeats = plugin.getLootConfig().getInt("items." + key + ".repeats", 2);
            for (int i = 0; i < repeats; i++) {
                if (r.nextDouble() * 100 <= chance) lootPool.add(item.clone());
            }
        }
        Collections.shuffle(lootPool);
    }

    private void throwLootTick(int ticksLeft) {
        if (lootPool.isEmpty() || ticksLeft <= 0) return;
        double expectedDrops = (double) lootPool.size() / ticksLeft;
        int itemsToDropNow = (int) expectedDrops;
        if (new Random().nextDouble() < (expectedDrops - itemsToDropNow)) itemsToDropNow++;
        for (int i = 0; i < itemsToDropNow && !lootPool.isEmpty(); i++) {
            ItemStack item = lootPool.remove(0);
            Item d = loc.getWorld().dropItem(loc.clone().add(0, 12, 0), item);
            double angle = new Random().nextDouble() * 2 * Math.PI;
            d.setVelocity(new Vector(Math.cos(angle) * 0.5, 1.2, Math.sin(angle) * 0.5));
        }
    }

    private void scheduleRemoval() {
        int closeTime = plugin.getConfig().getInt("times.close", 60);
        new BukkitRunnable() {
            int timeLeft = closeTime;
            @Override
            public void run() {
                if (!active) { this.cancel(); return; }
                if (timeLeft <= 0) {
                    this.cancel();
                    stopVolcano();
                    return;
                }
                updateBossBar("bossbar.cooling", timeLeft, closeTime);
                timeLeft--;
            }
        }.runTaskTimer(plugin, 0, 20L);
    }

    public void stopVolcano() {
        if (!active) return;
        active = false;
        if (bossBar != null) bossBar.removeAll();
        if (terrainBackup != null && loc != null) {
            try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(loc.getWorld()))) {
                Operations.complete(new ClipboardHolder(terrainBackup).createPaste(es)
                        .to(BlockVector3.at(loc.getX() - 30, loc.getY() - 10, loc.getZ() - 30)).build());
            } catch (Exception e) { e.printStackTrace(); }
        }
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(loc.getWorld()));
        if (rm != null) rm.removeRegion(REG_NAME);
        loc = null;
    }

    private void backupAndProtect(Location l) {
        BlockVector3 min = BlockVector3.at(l.getBlockX() - 30, l.getBlockY() - 10, l.getBlockZ() - 30);
        BlockVector3 max = BlockVector3.at(l.getBlockX() + 30, l.getBlockY() + 40, l.getBlockZ() + 30);
        CuboidRegion region = new CuboidRegion(BukkitAdapter.adapt(l.getWorld()), min, max);
        terrainBackup = new BlockArrayClipboard(region);
        try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(l.getWorld()))) {
            ForwardExtentCopy copy = new ForwardExtentCopy(es, region, terrainBackup, min);
            Operations.complete(copy);
        } catch (Exception e) { e.printStackTrace(); }
        RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(l.getWorld()));
        if (rm != null) {
            ProtectedCuboidRegion wgReg = new ProtectedCuboidRegion(REG_NAME, min, max);
            wgReg.setFlag(Flags.BUILD, plugin.getConfig().getString("region.build", "deny").equalsIgnoreCase("allow") ? StateFlag.State.ALLOW : StateFlag.State.DENY);
            wgReg.setFlag(Flags.PVP, plugin.getConfig().getString("region.pvp", "allow").equalsIgnoreCase("allow") ? StateFlag.State.ALLOW : StateFlag.State.DENY);
            rm.addRegion(wgReg);
        }
    }

    private void pasteSchematic(Location l) {
        File file = new File(plugin.getConfig().getString("schematic", "plugins/WorldEdit/schematics/vulcan.schem"));
        try (ClipboardReader reader = ClipboardFormats.findByFile(file).getReader(new FileInputStream(file))) {
            Clipboard cb = reader.read();
            try (EditSession es = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(l.getWorld()))) {
                Operations.complete(new ClipboardHolder(cb).createPaste(es).to(BlockVector3.at(l.getX(), l.getY(), l.getZ())).build());
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateBossBar(String configPath, int timeLeft, int totalTime) {
        String title = plugin.getConfig().getString(configPath + ".title", "&f{time}");
        bossBar.setTitle(plugin.color(title.replace("{time}", formatTime(timeLeft))));
        try { bossBar.setColor(BarColor.valueOf(plugin.getConfig().getString(configPath + ".color", "WHITE").toUpperCase())); } catch (Exception ignored) {}
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) timeLeft / totalTime)));
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return m > 0 ? m + "м " + s + "с" : s + "с";
    }

    public Location getLoc() { return loc; }
    public boolean isActive() { return active; }
}