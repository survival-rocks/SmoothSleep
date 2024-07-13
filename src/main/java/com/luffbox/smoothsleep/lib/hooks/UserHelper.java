package com.luffbox.smoothsleep.lib.hooks;

import org.bukkit.entity.Player;

/**
 * Abstraction layer so I can optionally use Essentials methods
 */
public interface UserHelper {
	String getNickname(Player p);
	boolean isVanished(Player p);
}
