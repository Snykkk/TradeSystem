package de.codingair.codingapi.player.gui;

import de.codingair.codingapi.API;
import de.codingair.codingapi.player.gui.hovereditems.ItemGUI;
import de.codingair.codingapi.player.gui.inventory.gui.GUI;
import de.codingair.codingapi.player.gui.inventory.gui.Interface;
import de.codingair.codingapi.player.gui.inventory.gui.InterfaceListener;
import de.codingair.codingapi.player.gui.inventory.gui.itembutton.ItemButton;
import de.codingair.codingapi.server.events.PlayerWalkEvent;
import de.codingair.codingapi.server.sounds.SoundData;
import de.codingair.codingapi.tools.Callback;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Removing of this disclaimer is forbidden.
 *
 * @author CodingAir
 * @verions: 1.0.0
 **/

public class GUIListener implements Listener {
    private static GUIListener instance = null;

    private GUIListener() {
        if(instance != null) HandlerList.unregisterAll(instance);
        instance = this;
    }

    public static void register(Plugin plugin) {
        if(isRegistered()) return;
        Bukkit.getPluginManager().registerEvents(new GUIListener(), plugin);
    }

    public static boolean isRegistered() {
        return instance != null;
    }

    /*
     * PlayerItem
     */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEvent(PlayerInteractEvent e) {
        if(!PlayerItem.isUsing(e.getPlayer())) return;

        List<PlayerItem> items = API.getRemovables(e.getPlayer(), PlayerItem.class);
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInHand();

        for(PlayerItem pItem : items) {
            if(pItem.equals(item)) pItem.trigger(e);
        }
        items.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if(!PlayerItem.isUsing((Player) e.getWhoClicked())) return;

        List<PlayerItem> items = API.getRemovables((Player) e.getWhoClicked(), PlayerItem.class);
        ItemStack current = e.getCurrentItem();

        for(PlayerItem pItem : items) {
            if(pItem.equals(current) && pItem.isFreezed()) e.setCancelled(true);
        }
        items.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if(!PlayerItem.isUsing(e.getPlayer())) return;

        List<PlayerItem> items = API.getRemovables(e.getPlayer(), PlayerItem.class);
        ItemStack current = e.getItemDrop().getItemStack();

        for(PlayerItem pItem : items) {
            if(pItem.equals(current) && pItem.isFreezed()) e.setCancelled(true);
        }
        items.clear();
    }

    @EventHandler
    public void onSwitch(PlayerItemHeldEvent e) {
        if(!PlayerItem.isUsing(e.getPlayer())) return;
        List<PlayerItem> items = API.getRemovables(e.getPlayer(), PlayerItem.class);

        ItemStack old = e.getPlayer().getInventory().getItem(e.getPreviousSlot());
        ItemStack current = e.getPlayer().getInventory().getItem(e.getNewSlot());

        PlayerItem prev = null, next = null;

        for(PlayerItem pItem : items) {
            if(pItem.equals(old) && pItem.isFreezed()) prev = pItem;
            if(pItem.equals(current) && pItem.isFreezed()) next = pItem;
        }
        items.clear();

        if(prev != null) prev.onUnhover(e);
        if(next != null) next.onHover(e);
    }

    /*
     * Interface
     */

    @EventHandler
    public void onSwap(InventoryClickEvent e) {
        if(e.getClick().name().startsWith("SWAP_OFFHAND") && e.getWhoClicked() instanceof Player) {
            Player player = (Player) e.getWhoClicked();
            if(API.getRemovable(player, GUI.class) != null || API.getRemovable(player, de.codingair.codingapi.player.gui.inventory.v2.GUI.class) != null) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDropWhileInInventory(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if(!GUI.usesGUI(p) && !GUI.usesOldGUI(p)) return;
        Interface inv = GUI.usesGUI(p) ? GUI.getGUI(p) : GUI.getOldGUI(p);

        if(inv instanceof GUI) e.setCancelled(!((GUI) inv).isCanDropItems());
    }

    @EventHandler
    public void onInvClickEvent(InventoryClickEvent e) {
        if(e.getInventory() == null || (!GUI.usesGUI((Player) e.getWhoClicked()) && !GUI.usesOldGUI((Player) e.getWhoClicked())) || e.getInventory().getType() != InventoryType.CHEST) return;
        Player p = (Player) e.getWhoClicked();

        Interface inv = GUI.usesGUI(p) ? GUI.getGUI(p) : GUI.getOldGUI(p);

        if(e.getClickedInventory() == null) {
            if(inv instanceof GUI) e.setCancelled(!((GUI) inv).isCanDropItems());
            if(!e.isCancelled()) {
                for(InterfaceListener l : inv.getListener()) {
                    l.onDropItem(e);
                }
            }
            return;
        }

        if(e.getClickedInventory() == e.getView().getBottomInventory()) {
            for(InterfaceListener l : inv.getListener()) {
                l.onClickBottomInventory(e);
            }
        }

        e.setCancelled(false);

        if(e.getClickedInventory() == e.getView().getTopInventory()) {
            for(InterfaceListener l : inv.getListener()) {
                l.onInvClickEvent(e);
            }

            if(e.isCancelled()) return;
            e.setCancelled(!inv.isEditableItems());

            if(inv instanceof GUI) {
                GUI gui = (GUI) inv;
                if(gui.isMovable(e.getSlot())) {
                    switch(e.getAction()) {
                        case COLLECT_TO_CURSOR:
                            ItemStack cursor = e.getCursor();
                            int startSize = cursor.getAmount();

                            if(inv.isEditableItems()) {
                                e.setCancelled(false);
                            } else {
                                e.setCancelled(true);
                                e.setResult(Event.Result.DENY);

                                List<Integer> movedFrom = new ArrayList<>();
                                for(Integer slot : gui.getMovableSlots()) {
                                    ItemStack other = gui.getItem(slot);

                                    if(other != null && cursor.isSimilar(other)) {
                                        int amount = cursor.getAmount();

                                        if(amount < cursor.getMaxStackSize()) {
                                            int a = cursor.getMaxStackSize() - amount;

                                            if(other.getAmount() > a) {
                                                other.setAmount(other.getAmount() - a);
                                                cursor.setAmount(cursor.getMaxStackSize());
                                                movedFrom.add(slot);
                                            } else {
                                                cursor.setAmount(cursor.getAmount() + other.getAmount());
                                                gui.setItem(slot, new ItemStack(Material.AIR));
                                                movedFrom.add(slot);
                                            }
                                        } else break;
                                    }
                                }

                                if(gui.isMoveOwnItems() && e.getView().getBottomInventory() != null) {
                                    Inventory bottom = e.getView().getBottomInventory();

                                    for(int slot = 0; slot < bottom.getSize(); slot++) {
                                        ItemStack other = bottom.getItem(slot);

                                        if(other == null) {
                                            continue;
                                        } else if(cursor.isSimilar(other)) {
                                            int amount = cursor.getAmount();

                                            if(amount < cursor.getMaxStackSize()) {
                                                int a = cursor.getMaxStackSize() - amount;

                                                if(other.getAmount() > a) {
                                                    other.setAmount(other.getAmount() - a);
                                                    cursor.setAmount(cursor.getMaxStackSize());
                                                    movedFrom.add(slot + gui.getSize());
                                                } else {
                                                    cursor.setAmount(cursor.getAmount() + other.getAmount());
                                                    bottom.setItem(slot, new ItemStack(Material.AIR));
                                                    movedFrom.add(slot + gui.getSize());
                                                }
                                            } else break;
                                        }
                                    }
                                }

                                if(cursor.getAmount() > startSize) {
                                    gui.getGUIListeners().forEach(l -> l.onCollectToCursor(e.getCursor(), movedFrom, e.getRawSlot()));
                                    break;
                                }
                            }
                            break;
                        default:
                            e.setCancelled(false);
                    }
                }
            }

            ItemStack item = e.getCurrentItem();
            int slot = e.getSlot();

            if(item == null || slot == -1) return;

            ItemButton button = inv.getButtonAt(slot);

            if(button == null) return;

            e.setCancelled(!button.isMovable());

            if(button.canClick(e.getClick()) &&
                    (button.isOnlyLeftClick() && e.isLeftClick()
                            || (button.isOnlyRightClick() && e.isRightClick())
                            || (!button.isOnlyRightClick() && !button.isOnlyLeftClick())
                            || (button.getOption().isNumberKey() && e.getClick().equals(ClickType.NUMBER_KEY)))) {
                if((e.getClick() == ClickType.DOUBLE_CLICK) != button.getOption().isDoubleClick()) return;

                button.playSound(e.getClick(), (Player) e.getWhoClicked());
                button.onClick(e);

                if(button.isCloseOnClick()) {
                    if(inv instanceof GUI) {
                        GUI g = (GUI) inv;
                        g.setClosingByButton(true);
                        if(g.useFallbackGUI() && g.getFallbackGUI() != null) {
                            g.fallBack();
                        } else e.getWhoClicked().closeInventory();
                    } else e.getWhoClicked().closeInventory();
                }
            }
        } else {
            if(inv instanceof GUI) {
                GUI gui = (GUI) inv;

                e.setCancelled(!gui.isMoveOwnItems());
                if(gui.isMoveOwnItems() && e.getClickedInventory().equals(e.getView().getBottomInventory())) {
                    ItemStack current = e.getCurrentItem();
                    switch(e.getAction()) {
                        case MOVE_TO_OTHER_INVENTORY:
                            if(inv.isEditableItems()) {
                                e.setCancelled(false);
                            } else {
                                e.setCancelled(true);

                                List<Integer> movedTo = new ArrayList<>();
                                int empty = -999;

                                int cAmount = current.getAmount();
                                for(Integer slot : gui.getMovableSlots()) {
                                    ItemStack other = gui.getItem(slot);

                                    if(other == null) {
                                        if(empty == -999) empty = slot;
                                    } else if(other.isSimilar(current)) {
                                        int amount = other.getAmount();

                                        if(amount < other.getMaxStackSize()) {
                                            int a = other.getMaxStackSize() - amount;

                                            if(current.getAmount() > a) {
                                                cAmount -= a;
                                                movedTo.add(slot);
                                            } else {
                                                cAmount = 0;
                                                movedTo.add(slot);
                                            }
                                        }
                                    }

                                    if(cAmount == 0) break;
                                }

                                if(cAmount > 0 && empty != -999) movedTo.add(empty);

                                boolean cancelled = false;
                                for(de.codingair.codingapi.player.gui.inventory.gui.GUIListener l : gui.getGUIListeners()) {
                                    if(l.onMoveToTopInventory(e.getRawSlot(), movedTo, current.clone())) cancelled = true;
                                    l.onMoveToTopInventory(current.clone(), e.getRawSlot(), movedTo);
                                }

                                if(cancelled) break;

                                for(Integer slot : movedTo) {
                                    ItemStack other = gui.getItem(slot);

                                    if(other == null) {
                                        gui.setItem(slot, current.clone());
                                        current.setAmount(0);
                                    } else {
                                        int amount = other.getAmount();

                                        if(amount < other.getMaxStackSize()) {
                                            int a = other.getMaxStackSize() - amount;

                                            if(current.getAmount() > a) {
                                                current.setAmount(current.getAmount() - a);
                                                other.setAmount(other.getMaxStackSize());
                                            } else {
                                                other.setAmount(other.getAmount() + current.getAmount());
                                                current.setAmount(0);
                                            }
                                        }
                                    }
                                }

                                if(current.getAmount() == 0) e.getClickedInventory().setItem(e.getSlot(), new ItemStack(Material.AIR));
                            }

                            break;
                        case COLLECT_TO_CURSOR:
                            //Im top-inv werden auch aus nicht editable slots die items genommen !! kommt nicht aus diesem case (slot nicht in movedFrom drin!)
                            ItemStack cursor = e.getCursor();
                            int startSize = cursor.getAmount();

                            if(inv.isEditableItems()) {
                                e.setCancelled(false);
                            } else {
                                e.setCancelled(true);
                                e.setResult(Event.Result.DENY);

                                List<Integer> movedFrom = new ArrayList<>();
                                for(Integer slot : gui.getMovableSlots()) {
                                    ItemStack other = gui.getItem(slot);

                                    if(other != null && cursor.isSimilar(other)) {
                                        int amount = cursor.getAmount();

                                        if(amount < cursor.getMaxStackSize()) {
                                            int a = cursor.getMaxStackSize() - amount;

                                            if(other.getAmount() > a) {
                                                other.setAmount(other.getAmount() - a);
                                                cursor.setAmount(cursor.getMaxStackSize());
                                                movedFrom.add(slot);
                                            } else {
                                                cursor.setAmount(cursor.getAmount() + other.getAmount());
                                                gui.setItem(slot, new ItemStack(Material.AIR));
                                                movedFrom.add(slot);
                                            }
                                        } else break;
                                    }
                                }

                                if(gui.isMoveOwnItems() && e.getView().getBottomInventory() != null) {
                                    Inventory bottom = e.getView().getBottomInventory();

                                    for(int slot = 0; slot < bottom.getSize(); slot++) {
                                        ItemStack other = bottom.getItem(slot);

                                        if(other == null) {
                                        } else if(cursor.isSimilar(other)) {
                                            int amount = cursor.getAmount();

                                            if(amount < cursor.getMaxStackSize()) {
                                                int a = cursor.getMaxStackSize() - amount;

                                                if(other.getAmount() > a) {
                                                    other.setAmount(other.getAmount() - a);
                                                    cursor.setAmount(cursor.getMaxStackSize());
                                                    movedFrom.add(slot + gui.getSize());
                                                } else {
                                                    cursor.setAmount(cursor.getAmount() + other.getAmount());
                                                    bottom.setItem(slot, new ItemStack(Material.AIR));
                                                    movedFrom.add(slot + gui.getSize());
                                                }
                                            } else break;
                                        }
                                    }
                                }

                                if(cursor.getAmount() > startSize) {
                                    gui.getGUIListeners().forEach(l -> l.onCollectToCursor(e.getCursor(), movedFrom, e.getRawSlot()));
                                    break;
                                }
                            }
                            break;
                        default:
                            e.setCancelled(false);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInvCloseEvent(InventoryCloseEvent e) {
        Callback<GUI> confirm = GUI.foreignConfirmations.remove(e.getPlayer());
        if(confirm != null) {
            confirm.accept(null);
            return;
        }

        if(e.getInventory() == null || (!GUI.usesGUI((Player) e.getPlayer()) && !GUI.usesOldGUI((Player) e.getPlayer())) || e.getInventory().getType() != InventoryType.CHEST)
            return;

        Player p = (Player) e.getPlayer();

        Interface inv = GUI.usesGUI(p) ? GUI.getGUI(p) : GUI.getOldGUI(p);

        if(inv instanceof GUI && !((GUI) inv).isClosingByButton()) {
            SoundData sound = ((GUI) inv).getCancelSound();
            if(sound != null && !((GUI) inv).isClosingForGUI()) sound.play(p);
        }

        inv.close((Player) e.getPlayer(), true);
        for(InterfaceListener l : inv.getListener()) {
            l.onInvCloseEvent(e);
        }

        if(inv instanceof GUI) ((GUI) inv).confirmClosing();
    }

    @EventHandler
    public void onInvOpenEvent(InventoryOpenEvent e) {
        if(e.getInventory() == null || (!GUI.usesGUI((Player) e.getPlayer()) && !GUI.usesOldGUI((Player) e.getPlayer())) || e.getInventory().getType() != InventoryType.CHEST)
            return;

        Player p = (Player) e.getPlayer();

        Interface inv = GUI.usesGUI(p) ? GUI.getGUI(p) : GUI.getOldGUI(p);

        for(InterfaceListener l : inv.getListener()) {
            l.onInvOpenEvent(e);
        }
    }

    @EventHandler
    public void onInvDragEvent(InventoryDragEvent e) {
        if(e.getInventory() == null || (!GUI.usesGUI((Player) e.getWhoClicked()) && !GUI.usesOldGUI((Player) e.getWhoClicked())) || e.getInventory().getType() != InventoryType.CHEST)
            return;
        Player p = (Player) e.getWhoClicked();

        Interface inv = GUI.usesGUI(p) ? GUI.getGUI(p) : GUI.getOldGUI(p);

        e.setCancelled(!inv.isEditableItems());

        if(inv instanceof GUI) {
            GUI gui = (GUI) inv;
            boolean movableSlots = true;

            for(Integer slot : e.getRawSlots()) {
                if(slot < gui.getSize()) {
                    if(!gui.isMovable(slot)) {
                        movableSlots = false;
                    }
                } else {
                    if(!gui.isMoveOwnItems()) movableSlots = false;
                }

                if(!movableSlots) break;
            }

            e.setCancelled(!movableSlots && !inv.isEditableItems());
        }

        for(InterfaceListener l : inv.getListener()) {
            l.onInvDragEvent(e);
        }

        if(inv instanceof GUI) {
            GUI gui = (GUI) inv;

            if(gui.isMoveOwnItems()) {
                boolean topInv = false;

                for(Integer slot : e.getRawSlots()) {
                    if(slot < gui.getSize()) topInv = true;
                }

                if(!topInv) e.setCancelled(false);
            }
        }
    }

    /*
     * ItemGUI
     */

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();

        if(!ItemGUI.usesGUI(p)) return;
        ItemGUI gui = ItemGUI.getGUI(p);

        if(gui.isVisibleOnSneak()) gui.setVisible(e.isSneaking());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if(!ItemGUI.usesGUI(p)) return;
        ItemGUI gui = ItemGUI.getGUI(p);
        if(gui.isCloseOnWalk()) return;

        Location from = e.getFrom().clone(), to = e.getTo().clone();
        double diffX = to.getX() - from.getX(), diffY = to.getY() - from.getY(), diffZ = to.getZ() - from.getZ();

        if(Math.abs(diffX) + Math.abs(diffY) + Math.abs(diffZ) == 0) return;

        gui.move(from, to);
    }

    @EventHandler
    public void onWalk(PlayerWalkEvent e) {
        Player p = e.getPlayer();

        if(!ItemGUI.usesGUI(p)) return;
        ItemGUI gui = ItemGUI.getGUI(p);
        if(gui.isVisible() && gui.isCloseOnWalk()) gui.setVisible(false);
    }
}
