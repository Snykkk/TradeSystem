package de.codingair.codingapi.player.data;

import de.codingair.codingapi.API;
import de.codingair.codingapi.server.reflections.IReflection;
import de.codingair.codingapi.server.reflections.PacketUtils;
import de.codingair.codingapi.utils.Removable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public abstract class PacketReader implements Removable {
	private final UUID uniqueId = UUID.randomUUID();
	private final Player player;
	private Channel channel;
	private final String name;
	private final JavaPlugin plugin;
	
	public PacketReader(Player player, String name, JavaPlugin plugin) {
		this.player = player;
		this.name = name;
		this.plugin = plugin;
	}

	@Override
	public JavaPlugin getPlugin() {
		return plugin;
	}

	@Override
	public UUID getUniqueId() {
		return uniqueId;
	}

	@Override
	public void destroy() {
		unInject();
	}
	
	public boolean inject() {
		IReflection.FieldAccessor<?> getPlayerConnection = IReflection.getField(PacketUtils.EntityPlayerClass, "playerConnection");
		IReflection.FieldAccessor<?> getNetworkManager = IReflection.getField(PacketUtils.PlayerConnectionClass, "networkManager");
		IReflection.FieldAccessor<?> getChannel = IReflection.getField(PacketUtils.NetworkManagerClass, "channel");

		Object ep = PacketUtils.getEntityPlayer(player);
		if(ep == null) return false;
		Object playerCon = getPlayerConnection.get(ep);
		if(playerCon == null) return false;
		Object networkMan = getNetworkManager.get(playerCon);
		if(networkMan == null) return false;
		channel = (Channel) getChannel.get(networkMan);
		if(channel == null) return false;

		ChannelDuplexHandler handler = new ChannelDuplexHandler() {
			@Override
			public void channelRead(ChannelHandlerContext ctx, Object o) throws Exception {
				try {
					if(!readPacket(o)) super.channelRead(ctx, o);
				} catch(Exception ex) {
					super.channelRead(ctx, o);
				}
			}

			@Override
			public void write(ChannelHandlerContext ctx, Object o, ChannelPromise promise) throws Exception {
				try {
					if(!writePacket(o)) super.write(ctx, o, promise);
				} catch(Exception ex) {
					super.write(ctx, o, promise);
				}
			}
		};

		if(channel.pipeline().get(name) != null) channel.pipeline().remove(name);
		if(channel.pipeline().get("packet_handler") != null) channel.pipeline().addBefore("packet_handler", name, handler);
		else channel.pipeline().addFirst(name, handler);
		
		API.addRemovable(this);
		return true;
	}
	
	public void unInject() {
		if(player.isOnline() && channel.pipeline().get(name) != null) {
			try {
				channel.pipeline().remove(name);
			} catch(Throwable ignored) {
			}
		}

		API.removeRemovable(this);
	}
	
	public void refresh() {
		unInject();
		inject();
	}
	
	@Override
	public Player getPlayer() {
		return player;
	}
	
	public String getName() {
		return name;
	}

	/**
	 * @param packet Object
	 * @return intercept
	 */
	public abstract boolean readPacket(Object packet);

	/**
	 * @param packet Object
	 * @return intercept
	 */
	public abstract boolean writePacket(Object packet);
}
