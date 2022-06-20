package de.codingair.codingapi.utils;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Removing of this disclaimer is forbidden.
 *
 * @author codingair
 * @verions: 1.0.0
 **/

public interface Removable {
	void destroy();
	
	Player getPlayer();

	UUID getUniqueId();

	JavaPlugin getPlugin();
	
	default boolean equals(Removable removable) {
		return getUniqueId().equals(removable.getUniqueId());
	}
}
