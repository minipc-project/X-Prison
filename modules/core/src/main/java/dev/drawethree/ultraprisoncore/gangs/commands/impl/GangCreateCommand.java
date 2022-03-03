package dev.drawethree.ultraprisoncore.gangs.commands.impl;

import com.google.common.collect.ImmutableList;
import dev.drawethree.ultraprisoncore.gangs.UltraPrisonGangs;
import dev.drawethree.ultraprisoncore.gangs.commands.GangCommand;
import dev.drawethree.ultraprisoncore.gangs.enums.GangCreateResult;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GangCreateCommand extends GangCommand {

	public GangCreateCommand(UltraPrisonGangs plugin) {
		super(plugin, "create", "new");
	}

	@Override
	public String getUsage() {
		return ChatColor.RED + "/gang create [gang]";
	}

	@Override
	public boolean execute(CommandSender sender, ImmutableList<String> args) {
		if (sender instanceof Player && args.size() == 1) {
			return this.plugin.getGangsManager().createGang(args.get(0), (Player) sender) == GangCreateResult.SUCCESS;
		}
		return false;
	}

	@Override
	public boolean canExecute(CommandSender sender) {
		return sender.hasPermission(UltraPrisonGangs.GANGS_CREATE_PERM);
	}
}