package de.codingair.codingapi.player.gui.anvil;

import de.codingair.codingapi.API;
import de.codingair.codingapi.player.gui.anvil.depended.PrepareAnvilEventHelp;
import de.codingair.codingapi.server.reflections.IReflection;
import de.codingair.codingapi.server.reflections.PacketUtils;
import de.codingair.codingapi.server.specification.Version;
import de.codingair.codingapi.utils.Removable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Removing of this disclaimer is forbidden.
 *
 * @author codingair
 * @verion: 1.0.0
 **/

public class AnvilGUI implements Removable {
    private final UUID uniqueId = UUID.randomUUID();
    private final JavaPlugin plugin;
    private final Player player;
    private AnvilListener listener;
    private HashMap<AnvilSlot, ItemStack> items = new HashMap<>();

    private String submittedText = null;
    private boolean submitted = false;
    private boolean onlyWithChanges = true; //Triggers the AnvilClickEvent only if the output is filled
    private boolean keepSubmittedText = true;

    private AnvilCloseEvent closeEvent = null;
    private Listener bukkitListener;
    private PrepareAnvilEventHelp prepareListener;
    private Inventory inv;

    //Only for 1.14+
    private String title;

    public AnvilGUI(JavaPlugin plugin, Player player, AnvilListener listener, String title) {
        this.plugin = plugin;
        this.player = player;
        this.listener = listener;
        this.title = title == null ? "Repair & Name" : title;

        registerBukkitListener();
        if(Version.get().isBiggerThan(8)) Bukkit.getPluginManager().registerEvents(prepareListener = new PrepareAnvilEventHelp(), this.plugin);
    }

    public AnvilGUI(JavaPlugin plugin, Player player, AnvilListener listener) {
        this(plugin, player, listener, "Repair & Name");
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public void destroy() {
        if(remove()) {
            try {
                this.player.closeInventory();
            } catch(Throwable ex) {
                Bukkit.getScheduler().runTask(plugin, this.player::closeInventory);
            }
        }
    }

    private void registerBukkitListener() {
        this.bukkitListener = new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onInventoryClick(InventoryClickEvent e) {
                if(e.getWhoClicked() instanceof Player) {
                    Player p = (Player) e.getWhoClicked();

                    if(e.getInventory().equals(inv)) {
                        e.setCancelled(true);

                        ItemStack item = e.getCurrentItem();
                        int slot = e.getRawSlot();

                        if(AnvilSlot.bySlot(slot) == AnvilSlot.OUTPUT && (item == null || item.getType() == Material.AIR) && onlyWithChanges) return;

                        AnvilClickEvent clickEvent = new AnvilClickEvent(p, e.getClick(), AnvilSlot.bySlot(slot), item, AnvilGUI.this);

                        if(listener != null) listener.onClick(clickEvent);
                        Bukkit.getPluginManager().callEvent(clickEvent);

                        if(clickEvent.getSlot().equals(AnvilSlot.OUTPUT)) {
                            submitted = true;
                            submittedText = clickEvent.getSubmitted() == null ? clickEvent.getInput() : clickEvent.getSubmitted();
                        }

                        e.setCancelled(clickEvent.isCancelled());
                        e.setCancelled(true);

                        if(keepSubmittedText && item != null && item.hasItemMeta()) {
                            ItemMeta meta = item.getItemMeta();
                            meta.setDisplayName(submittedText);
                            item.setItemMeta(meta);
                            inv.setItem(AnvilSlot.INPUT_LEFT.getSlot(), item);
                            p.updateInventory();
                        }

                        if(clickEvent.getWillClose()) {
                            close(clickEvent.isKeepInventory());
                            if(clickEvent.isKeepInventory()) onInventoryClose(new InventoryCloseEvent(e.getView()));
                        }

                        if(clickEvent.getSlot() == AnvilSlot.OUTPUT && !clickEvent.isPayExp())
                            p.setLevel(player.getLevel());
                    }
                }
            }

            @EventHandler
            public void onInventoryClose(InventoryCloseEvent e) {
                if(e.getPlayer() instanceof Player) {
                    if(e.getInventory().equals(inv)) {
                        if(closeEvent == null) {
                            closeEvent = new AnvilCloseEvent(player, AnvilGUI.this);
                            Bukkit.getPluginManager().callEvent(closeEvent);
                            if(listener != null) listener.onClose(closeEvent);
                        }

                        inv.clear();
                        remove();

                        if(closeEvent.getPost() != null) {
                            Bukkit.getScheduler().runTask(plugin, closeEvent.getPost());
                        }
                    }
                }
            }
        };

        Bukkit.getPluginManager().registerEvents(this.bukkitListener, this.plugin);
    }

    public AnvilGUI open() {
        API.addRemovable(this);
        this.player.closeInventory();

        Class<?> containerAnvilClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "ContainerAnvil");
        Class<?> playerInventoryClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "PlayerInventory");
        Class<?> worldClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "World");
        Class<?> blockPositionClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "BlockPosition");
        Class<?> entityPlayerClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "EntityPlayer");
        Class<?> craftInventoryViewClass = IReflection.getClass(IReflection.ServerPacket.CRAFTBUKKIT_PACKAGE, "inventory.CraftInventoryView");
        Class<?> packetPlayOutOpenWindowClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "PacketPlayOutOpenWindow");
        Class<?> containerClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "Container");
        Class<?> chatMessageClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "ChatMessage");

        IReflection.ConstructorAccessor anvilContainerCon;
        if(Version.get().isBiggerThan(Version.v1_13)) {
            Class<?> containerAccessClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "ContainerAccess");
            anvilContainerCon = IReflection.getConstructor(containerAnvilClass, int.class, playerInventoryClass, containerAccessClass);
        } else {
            anvilContainerCon = IReflection.getConstructor(containerAnvilClass, playerInventoryClass, worldClass, blockPositionClass, entityPlayerClass);
        }
        IReflection.ConstructorAccessor blockPositionCon = IReflection.getConstructor(blockPositionClass, Integer.class, Integer.class, Integer.class);
        IReflection.ConstructorAccessor chatMessageCon = IReflection.getConstructor(chatMessageClass, String.class, Object[].class);

        IReflection.MethodAccessor getBukkitView = IReflection.getMethod(containerAnvilClass, "getBukkitView", craftInventoryViewClass, (Class<?>[]) null);
        IReflection.MethodAccessor getTopInventory = IReflection.getMethod(craftInventoryViewClass, "getTopInventory", Inventory.class, (Class<?>[]) null);
        IReflection.MethodAccessor nextContainerCounter = IReflection.getMethod(entityPlayerClass, "nextContainerCounter", int.class, (Class<?>[]) null);
        IReflection.MethodAccessor addSlotListener = IReflection.getMethod(containerClass, "addSlotListener", new Class[] {entityPlayerClass});

        IReflection.FieldAccessor<?> getInventory = IReflection.getField(entityPlayerClass, "inventory");
        IReflection.FieldAccessor<?> getWorld = IReflection.getField(entityPlayerClass, "world");
        IReflection.FieldAccessor<?> reachable = IReflection.getField(containerAnvilClass, "checkReachable");
        IReflection.FieldAccessor<?> activeContainer = IReflection.getField(entityPlayerClass, "activeContainer");
        IReflection.FieldAccessor<?> windowId = IReflection.getField(containerClass, "windowId");

        Object entityPlayer = PacketUtils.getEntityPlayer(this.player);
        Object inventory = getInventory.get(entityPlayer);
        Object world = getWorld.get(entityPlayer);
        Object blockPosition = blockPositionCon.newInstance(0, 0, 0);

        int c = (int) nextContainerCounter.invoke(entityPlayer);

        Object container;
        if(Version.get().isBiggerThan(Version.v1_13)) {
            Class<?> containerAccessClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "ContainerAccess");
            IReflection.MethodAccessor at = IReflection.getMethod(containerAccessClass, "at", containerAccessClass, new Class[] {worldClass, blockPositionClass});

            container = anvilContainerCon.newInstance(c, inventory, at.invoke(null, world, blockPosition));
            IReflection.FieldAccessor<?> title = IReflection.getField(containerClass, "title");
            title.set(container, PacketUtils.getIChatBaseComponent(this.title));
        } else {
            container = anvilContainerCon.newInstance(inventory, world, blockPosition, entityPlayer);
        }
        reachable.set(container, false);

        inv = (Inventory) getTopInventory.invoke(getBukkitView.invoke(container));
        if(prepareListener != null) prepareListener.setInv(inv);

        for(AnvilSlot slot : items.keySet()) {
            inv.setItem(slot.getSlot(), items.get(slot));
        }

        try {
            if(Version.get().isBiggerThan(Version.v1_13)) {
                Class<?> containersClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "Containers");
                IReflection.FieldAccessor<?> generic = IReflection.getField(containersClass, "ANVIL");
                IReflection.ConstructorAccessor packetPlayOutOpenWindowCon = IReflection.getConstructor(packetPlayOutOpenWindowClass, int.class, containersClass, PacketUtils.IChatBaseComponentClass);

                Object packet = packetPlayOutOpenWindowCon.newInstance(c, generic.get(null), PacketUtils.getChatMessage(title));
                PacketUtils.sendPacket(this.player, packet);
            } else {
                IReflection.ConstructorAccessor packetPlayOutOpenWindowCon = IReflection.getConstructor(packetPlayOutOpenWindowClass, Integer.class, String.class, chatMessageClass, int.class);
                PacketUtils.sendPacket(this.player, packetPlayOutOpenWindowCon.newInstance(c, "minecraft:anvil", chatMessageCon.newInstance("AnvilGUI", new Object[] {}), 0));
            }
        } catch(Exception e) {
            e.printStackTrace();
            plugin.getLogger().log(Level.SEVERE, "Error: Cannot open the AnvilGUI in " + Version.get().name() + "!");
        }


        activeContainer.set(entityPlayer, container);
        windowId.set(activeContainer.get(entityPlayer), c);
        addSlotListener.invoke(activeContainer.get(entityPlayer), entityPlayer);

        updateInventory();
        return this;
    }

    public void close() {
        close(false);
    }

    public void close(boolean keep) {
        closeEvent = new AnvilCloseEvent(player, AnvilGUI.this, submitted, submittedText);

        Bukkit.getPluginManager().callEvent(closeEvent);
        if(listener != null) listener.onClose(closeEvent);

        if(!closeEvent.isCancelled()) {
            inv.clear();
            if(!keep) getPlayer().closeInventory();
        }
    }

    public void clearInventory() {
        items = new HashMap<>();
        this.updateInventory();
    }

    public void updateInventory() {
        Class<?> entityPlayerClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "EntityPlayer");
        Class<?> containerAnvilClass = IReflection.getClass(IReflection.ServerPacket.MINECRAFT_PACKAGE, "ContainerAnvil");
        Class<?> craftInventoryViewClass = IReflection.getClass(IReflection.ServerPacket.CRAFTBUKKIT_PACKAGE, "inventory.CraftInventoryView");

        IReflection.FieldAccessor<?> activeContainer = IReflection.getField(entityPlayerClass, "activeContainer");

        IReflection.MethodAccessor getBukkitView = IReflection.getMethod(containerAnvilClass, "getBukkitView", craftInventoryViewClass, (Class<?>[]) null);
        IReflection.MethodAccessor getTopInventory = IReflection.getMethod(craftInventoryViewClass, "getTopInventory", Inventory.class, (Class<?>[]) null);

        Object entityPlayer = PacketUtils.getEntityPlayer(this.player);
        Object container = activeContainer.get(entityPlayer);

        if(!container.toString().toLowerCase().contains("anvil")) return;

        inv = (Inventory) getTopInventory.invoke(getBukkitView.invoke(container));
        if(prepareListener != null) prepareListener.setInv(inv);
        inv.clear();

        for(AnvilSlot slot : items.keySet()) {
            inv.setItem(slot.getSlot(), items.get(slot));
        }

        this.player.updateInventory();
    }

    public AnvilGUI setSlot(AnvilSlot slot, ItemStack item) {
        items.remove(slot);
        items.put(slot, item);
        return this;
    }

    public boolean remove() {
        if(listener == null) return false;
        this.clearInventory();
        this.listener = null;
        this.items = null;

        HandlerList.unregisterAll(this.bukkitListener);
        if(this.prepareListener != null) this.prepareListener.unregister();
        listener = null;

        API.removeRemovable(this);
        return true;
    }

    public static AnvilGUI openAnvil(JavaPlugin plugin, Player p, AnvilListener listener, ItemStack item) {
        return new AnvilGUI(plugin, p, listener).setSlot(AnvilSlot.INPUT_LEFT, item).open();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "Repair & Name" : title;
    }

    public boolean isKeepSubmittedText() {
        return keepSubmittedText;
    }

    public void setKeepSubmittedText(boolean keepSubmittedText) {
        this.keepSubmittedText = keepSubmittedText;
    }

    public boolean isOnlyWithChanges() {
        return onlyWithChanges;
    }

    public void setOnlyWithChanges(boolean onlyWithChanges) {
        this.onlyWithChanges = onlyWithChanges;
    }
}
