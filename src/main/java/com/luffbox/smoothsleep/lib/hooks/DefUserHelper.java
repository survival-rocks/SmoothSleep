package com.luffbox.smoothsleep.lib.hooks;

import org.bukkit.entity.Player;

public class DefUserHelper implements UserHelper{
	@Override
	public String getNickname(Player p) { return p.getDisplayName(); }

	@Override
	public boolean isVanished(Player p) { return false; }
}
