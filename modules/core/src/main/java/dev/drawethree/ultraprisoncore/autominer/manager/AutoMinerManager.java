package dev.drawethree.ultraprisoncore.autominer.manager;

import dev.drawethree.ultraprisoncore.autominer.UltraPrisonAutoMiner;
import dev.drawethree.ultraprisoncore.autominer.api.events.PlayerAutoMinerTimeReceiveEvent;
import dev.drawethree.ultraprisoncore.autominer.api.events.PlayerAutomineEvent;
import dev.drawethree.ultraprisoncore.autominer.model.AutoMinerRegion;
import dev.drawethree.ultraprisoncore.utils.player.PlayerUtils;
import me.lucko.helper.Events;
import me.lucko.helper.Schedulers;
import me.lucko.helper.utils.Players;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.codemc.worldguardwrapper.WorldGuardWrapper;
import org.codemc.worldguardwrapper.region.IWrappedRegion;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class AutoMinerManager {

	private final UltraPrisonAutoMiner plugin;

	private final Map<UUID, Integer> autoMinerTimes;

	private List<AutoMinerRegion> autoMinerRegions;

	public AutoMinerManager(UltraPrisonAutoMiner plugin) {
		this.plugin = plugin;
		this.autoMinerTimes = new HashMap<>();
	}

	private void loadAllPlayersAutoMinerData() {
		Players.all().forEach(this::loadPlayerAutoMinerData);
	}

	public void loadPlayerAutoMinerData(Player p) {
		Schedulers.async().run(() -> {
			int timeLeft = this.plugin.getCore().getPluginDatabase().getPlayerAutoMinerTime(p);
			this.autoMinerTimes.put(p.getUniqueId(), timeLeft);
			this.plugin.getCore().getLogger().info(String.format("Loaded %s's AutoMiner Time.", p.getName()));
		});
	}

	public void savePlayerAutoMinerData(Player p, boolean async) {

		int timeLeft = autoMinerTimes.getOrDefault(p.getUniqueId(), 0);

		if (async) {
			Schedulers.async().run(() -> savePlayerAutominerData(p, timeLeft));
		} else {
			savePlayerAutominerData(p, timeLeft);
		}
	}

	private void savePlayerAutominerData(Player p, int timeLeft) {
		this.plugin.getCore().getPluginDatabase().saveAutoMiner(p, timeLeft);
		this.autoMinerTimes.remove(p.getUniqueId());
		this.plugin.getCore().getLogger().info(String.format("Saved %s's AutoMiner time.", p.getName()));
	}

	public void givePlayerAutoMinerTime(CommandSender sender, Player p, long time, TimeUnit unit) {

		if (p == null || !p.isOnline()) {
			PlayerUtils.sendMessage(sender, "&cPlayer is not online!");
			return;
		}

		int currentTime = autoMinerTimes.getOrDefault(p.getUniqueId(), 0);
		currentTime += unit.toSeconds(time);

		autoMinerTimes.put(p.getUniqueId(), currentTime);

		this.callAutoMinerTimeReceiveEvent(p, time, unit);

		PlayerUtils.sendMessage(sender, this.plugin.getAutoMinerConfig().getMessage("auto_miner_time_add").replace("%time%", String.valueOf(time)).replace("%timeunit%", unit.name()).replace("%player%", p.getName()));
	}

	private PlayerAutoMinerTimeReceiveEvent callAutoMinerTimeReceiveEvent(Player p, long time, TimeUnit unit) {
		PlayerAutoMinerTimeReceiveEvent event = new PlayerAutoMinerTimeReceiveEvent(p, unit, time);
		Events.callSync(event);
		return event;
	}

	public boolean hasAutoMinerTime(Player p) {
		return autoMinerTimes.getOrDefault(p.getUniqueId(), 0) > 0;
	}

	public void decrementPlayerAutominerTime(Player p) {
		int newAmount = autoMinerTimes.get(p.getUniqueId()) - 1;
		autoMinerTimes.put(p.getUniqueId(), newAmount);
	}

	public int getAutoMinerTime(Player player) {
		return this.autoMinerTimes.getOrDefault(player.getUniqueId(), 0);
	}

	public boolean isInAutoMinerRegion(Player player) {
		for (AutoMinerRegion region : this.autoMinerRegions) {
			if (region.getRegion().contains(player.getLocation())) {
				return true;
			}
		}
		return false;
	}

	public PlayerAutomineEvent callAutoMineEvent(Player p) {
		PlayerAutomineEvent event = new PlayerAutomineEvent(p, this.getAutoMinerTime(p));
		Events.callSync(event);
		return event;
	}

	public void saveAllPlayerAutoMinerData(boolean async) {
		Players.all().forEach(p -> savePlayerAutoMinerData(p, async));
	}

	private void loadAutoMinerRegions() {
		this.autoMinerRegions = new ArrayList<>();

		YamlConfiguration configuration = this.plugin.getAutoMinerConfig().getYamlConfig();

		Set<String> regionNames = configuration.getConfigurationSection("auto-miner-regions").getKeys(false);

		for (String regionName : regionNames) {
			String worldName = configuration.getString("auto-miner-regions." + regionName + ".world");
			World world = Bukkit.getWorld(worldName);

			if (world == null) {
				plugin.getCore().getLogger().warning(String.format("Unable to get world with name %s!  Disabling AutoMiner region.", worldName));
				return;
			}

			int rewardPeriod = configuration.getInt("auto-miner-regions." + regionName + ".reward-period");

			if (rewardPeriod <= 0) {
				plugin.getCore().getLogger().warning("reward-period in autominer.yml for region " + regionName + " needs to be greater than 0!");
				return;
			}

			Optional<IWrappedRegion> optRegion = WorldGuardWrapper.getInstance().getRegion(world, regionName);

			if (!optRegion.isPresent()) {
				plugin.getCore().getLogger().warning(String.format("There is no such region named %s in world %s!", regionName, world.getName()));
				return;
			}

			List<String> rewards = configuration.getStringList("auto-miner-regions." + regionName + ".rewards");

			if (rewards.isEmpty()) {
				plugin.getCore().getLogger().warning("rewards in autominer.yml for region " + regionName + " are empty!");
				return;
			}

			int blocksBroken = configuration.getInt("auto-miner-regions." + regionName + ".blocks-broken");

			if (blocksBroken <= 0) {
				this.plugin.getCore().getLogger().warning("blocks-broken in autominer.yml for region " + regionName + " needs to be greater than 0!");
				return;
			}

			AutoMinerRegion region = new AutoMinerRegion(this.plugin, world, optRegion.get(), rewards, rewardPeriod, blocksBroken);
			region.startAutoMinerTask();

			this.plugin.getCore().getLogger().info("AutoMiner region " + regionName + " loaded successfully!");
			this.autoMinerRegions.add(region);
		}
	}

	public void load() {
		this.removeExpiredAutoMiners();
		this.loadAllPlayersAutoMinerData();
		this.loadAutoMinerRegions();
	}

	private void removeExpiredAutoMiners() {
		Schedulers.async().run(() -> {
			this.plugin.getCore().getPluginDatabase().removeExpiredAutoMiners();
			this.plugin.getCore().getLogger().info("Removed expired AutoMiners from database");
		});
	}

	public void reload() {
		this.stopAutoMinerRegions();
		this.loadAutoMinerRegions();
	}

	public void disable() {
		this.stopAutoMinerRegions();
		this.saveAllPlayerAutoMinerData(false);
	}

	private void stopAutoMinerRegions() {
		for (AutoMinerRegion region : this.autoMinerRegions) {
			region.stopAutoMinerTask();
		}
	}
}