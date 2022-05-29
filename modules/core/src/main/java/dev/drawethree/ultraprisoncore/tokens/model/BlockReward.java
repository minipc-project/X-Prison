package dev.drawethree.ultraprisoncore.tokens.model;

import dev.drawethree.ultraprisoncore.utils.player.PlayerUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.lucko.helper.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

@AllArgsConstructor
@Getter
public class BlockReward {

    private final long blocksRequired;
    private final List<String> commandsToRun;
    private final String message;

    public void giveTo(Player p) {

        if (!Bukkit.isPrimaryThread()) {
            Schedulers.sync().run(() -> {
                for (String s : this.commandsToRun) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s.replace("%player%", p.getName()));
                }
            });
        } else {
            for (String s : this.commandsToRun) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s.replace("%player%", p.getName()));
            }
        }
        PlayerUtils.sendMessage(p, this.message);
    }
}