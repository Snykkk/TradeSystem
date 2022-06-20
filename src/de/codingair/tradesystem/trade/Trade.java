package de.codingair.tradesystem.trade;

import de.codingair.codingapi.API;
import de.codingair.codingapi.player.gui.anvil.AnvilGUI;
import de.codingair.codingapi.player.gui.inventory.PlayerInventory;
import de.codingair.codingapi.tools.items.ItemBuilder;
import de.codingair.codingapi.tools.items.XMaterial;
import de.codingair.tradesystem.TradeSystem;
import de.codingair.tradesystem.event.TradeItemEvent;
import de.codingair.tradesystem.trade.layout.Function;
import de.codingair.tradesystem.trade.layout.Item;
import de.codingair.tradesystem.trade.layout.utils.Pattern;
import de.codingair.tradesystem.utils.Lang;
import de.codingair.tradesystem.utils.Profile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static de.codingair.tradesystem.tradelog.TradeLogService.getTradeLog;

public class Trade {
    private final Player[] players = new Player[2];
    private final TradingGUI[] guis = new TradingGUI[2];
    private final int[] moneyBackup = new int[] {0, 0};
    private final int[] money = new int[] {0, 0};
    private final boolean[] ready = new boolean[] {false, false};
    private final boolean[] cursor = new boolean[] {false, false};
    private final boolean[] waitForPickup = new boolean[] {false, false};
    private final List<Integer> slots = new ArrayList<>();
    private final List<Integer> otherSlots = new ArrayList<>();
    private BukkitRunnable countdown = null;
    private int countdownTicks = 0;
    private Listener listener;

    Trade(Player p0, Player p1) {
        this.players[0] = p0;
        this.players[1] = p1;

        Pattern layout = TradeSystem.getInstance().getLayoutManager().getActive();

        for (Item item : layout.getItems()) {
            if (item == null || item.getFunction() == null) continue;
            if (item.getFunction().equals(Function.EMPTY_FIRST_TRADER)) slots.add(item.getSlot());
            else if (item.getFunction().equals(Function.EMPTY_SECOND_TRADER)) otherSlots.add(item.getSlot());
        }
    }

    boolean[] getReady() {
        return ready;
    }

    int[] getMoney() {
        return money;
    }

    void update() {
        if (guis[0] != null && guis[1] != null) {
            for (int i = 0; i < slots.size(); i++) {
                if ((guis[0].getItem(slots.get(i)) == null && guis[1].getItem(otherSlots.get(i)) != null) || (guis[0].getItem(slots.get(i)) != null && guis[1].getItem(otherSlots.get(i)) == null) ||
                        (guis[0].getItem(slots.get(i)) != null && guis[1].getItem(otherSlots.get(i)) != null && !guis[0].getItem(slots.get(i)).equals(guis[1].getItem(otherSlots.get(i))))) {
                    guis[1].setItem(otherSlots.get(i), guis[0].getItem(slots.get(i)));

                    ready[0] = false;
                    ready[1] = false;
                }

                if ((guis[1].getItem(slots.get(i)) == null && guis[0].getItem(otherSlots.get(i)) != null) || (guis[1].getItem(slots.get(i)) != null && guis[0].getItem(otherSlots.get(i)) == null) ||
                        (guis[1].getItem(slots.get(i)) != null && guis[0].getItem(otherSlots.get(i)) != null && !guis[1].getItem(slots.get(i)).equals(guis[0].getItem(otherSlots.get(i))))) {
                    guis[0].setItem(otherSlots.get(i), guis[1].getItem(slots.get(i)));

                    ready[1] = false;
                    ready[0] = false;
                }
            }

            if (money[0] != moneyBackup[0] || money[1] != moneyBackup[1]) {
                moneyBackup[0] = money[0];
                moneyBackup[1] = money[1];

                ready[1] = false;
                ready[0] = false;
            }

            guis[0].initialize(this.players[0]);
            guis[1].initialize(this.players[1]);
        }

        if (this.ready[0] && this.ready[1]) finish();
        else if (countdown != null) {
            TradeSystem.man().playCountdownStopSound(players[0]);
            TradeSystem.man().playCountdownStopSound(players[1]);
            countdown.cancel();
            countdownTicks = 0;
            countdown = null;
            guis[0].synchronizeTitle();
            guis[1].synchronizeTitle();
        }
    }

    void start() {
        startListeners();
        this.guis[0] = new TradingGUI(this.players[0], 0, this);
        this.guis[1] = new TradingGUI(this.players[1], 1, this);

        this.guis[0].open();
        this.guis[1].open();

        TradeSystem.man().playStartSound(this.players[0]);
        TradeSystem.man().playStartSound(this.players[1]);
    }

    public void cancel() {
        cancel(null);
    }

    public void cancel(String message) {
        stopListeners();
        if (this.guis[0] == null || this.guis[1] == null) return;

        boolean[] droppedItems = new boolean[] {false, false};
        for (Integer slot : this.slots) {
            if (this.guis[0].getItem(slot) != null && this.guis[0].getItem(slot).getType() != Material.AIR) {
                ItemStack item = this.guis[0].getItem(slot);
                int i = fit(this.players[0], item);

                if (item.getAmount() > i) {
                    item.setAmount(item.getAmount() - i);
                    this.players[0].getInventory().addItem(item);
                }
                if (i > 0) {
                    item.setAmount(i);
                    droppedItems[0] |= dropItem(players[0], item);
                }
            }
            if (this.guis[1].getItem(slot) != null && this.guis[1].getItem(slot).getType() != Material.AIR) {
                ItemStack item = this.guis[1].getItem(slot);
                int i = fit(this.players[1], item);

                if (item.getAmount() > i) {
                    item.setAmount(item.getAmount() - i);
                    this.players[1].getInventory().addItem(item);
                }
                if (i > 0) {
                    item.setAmount(i);
                    droppedItems[1] |= dropItem(players[1], item);
                }
            }
        }

        for (int i = 0; i < 2; i++) {
            ItemStack item = this.players[i].getOpenInventory().getCursor();
            if (item != null && item.getType() != Material.AIR) {
                int fit = fit(this.players[i], item.clone());

                if (item.getAmount() > fit) {
                    item.setAmount(item.getAmount() - fit);
                    this.players[i].getInventory().addItem(item);
                }
                if (fit > 0) {
                    item.setAmount(fit);
                    droppedItems[i] |= dropItem(players[i], item);
                }

                this.players[i].getOpenInventory().setCursor(null);
            }
        }

        this.guis[0] = null;
        this.guis[1] = null;

        if (message != null) {
            getTradeLog().log(players[0], players[1], "Trade Cancelled: " + message);
            this.players[0].sendMessage(message);
            this.players[1].sendMessage(message);
        } else {
            getTradeLog().log(players[0], players[1], "Trade Cancelled");
            this.players[0].sendMessage(Lang.getPrefix() + Lang.get("Trade_Was_Cancelled", this.players[0]));
            this.players[1].sendMessage(Lang.getPrefix() + Lang.get("Trade_Was_Cancelled", this.players[1]));
        }

        for (int i = 0; i < droppedItems.length; i++) {
            if (droppedItems[i]) {
                this.players[i].sendMessage(Lang.getPrefix() + Lang.get("Items_Dropped", this.players[i]));
            }
        }

        TradeSystem.man().playCancelSound(this.players[0]);
        TradeSystem.man().playCancelSound(this.players[1]);

        this.players[0].closeInventory();
        this.players[1].closeInventory();

        this.players[0].updateInventory();
        this.players[1].updateInventory();

        TradeSystem.man().getTradeList().remove(this);
    }

    private void startListeners() {
        Bukkit.getPluginManager().registerEvents(this.listener = new Listener() {
            @EventHandler
            public void onPickup(PlayerPickupItemEvent e) {
                if (e.getPlayer() == players[0]) {
                    if (!canPickup(e.getPlayer(), e.getItem().getItemStack()) || waitForPickup[0]) e.setCancelled(true);
                    else Bukkit.getScheduler().runTaskLater(TradeSystem.getInstance(), () -> cancelOverflow(players[1]), 1);
                } else if (e.getPlayer() == players[1]) {
                    if (!canPickup(e.getPlayer(), e.getItem().getItemStack()) || waitForPickup[1]) e.setCancelled(true);
                    else Bukkit.getScheduler().runTaskLater(TradeSystem.getInstance(), () -> cancelOverflow(players[0]), 1);
                }
            }
        }, TradeSystem.getInstance());
    }

    private boolean dropItem(Player player, ItemStack itemStack) {
        if (player == null || itemStack == null || itemStack.getType() == Material.AIR) return false;
        player.getWorld().dropItem(player.getLocation(), itemStack);
        return true;
    }

    private boolean canPickup(Player player, ItemStack item) {
        PlayerInventory inv = new PlayerInventory(player);

        for (Integer slot : this.slots) {
            ItemStack back = guis[getId(player)].getItem(slot);
            if (back != null && back.getType() != Material.AIR) {
                inv.addItem(back);
            }
        }

        //placeholder
        if (this.cursor[getId(player)]) {
            ItemStack cursor = new ItemBuilder(XMaterial.BEDROCK).setName("PLACEHOLDER_CURSOR").getItem();
            if (!inv.addItem(cursor, false)) return false;
        }

        return inv.addItem(item);
    }

    private void stopListeners() {
        if (this.listener != null) HandlerList.unregisterAll(this.listener);
    }

    /**
     * Returns the amount, which doesn't fit
     */
    private int fit(Player player, ItemStack item) {
        int amount = item.getAmount();

        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = player.getInventory().getContents()[i];

            if (itemStack == null || itemStack.getType().equals(Material.AIR)) return 0;
            if (itemStack.isSimilar(item) && itemStack.getAmount() < itemStack.getMaxStackSize()) {
                amount -= itemStack.getMaxStackSize() - itemStack.getAmount();
            }

            if (amount <= 0) return 0;
        }

        return amount;
    }

    private void finish() {
        if (this.countdown != null) return;

        if (this.guis[0] == null || this.guis[1] == null) return;
        if (this.guis[0].pause && this.guis[1].pause) return;

        // code to avoid some weird money dupe
        final Player player1 = players[0];
        final Player player2 = players[1];

        Profile p0 = TradeSystem.getProfile(player1);
        Profile p1 = TradeSystem.getProfile(player2);

        if (p0.getMoney() < money[0] || p1.getMoney() < money[1]) {
            cancel(Lang.getPrefix() + Lang.get("Economy_Error"));
            return;
        }

        Runnable runnable = () -> {
            TradeSystem.man().getTradeList().remove(Trade.this);

            for (Player player : players) {
                AnvilGUI gui = API.getRemovable(player, AnvilGUI.class);
                if (gui != null) {
                    gui.clearInventory();
                    player.closeInventory();
                }
            }

            guis[0].pause = true;
            guis[1].pause = true;

            boolean[] droppedItems = new boolean[] {false, false};
            for (Integer slot : slots) {
                //using original one to prevent dupe glitches!!!
                ItemStack i0 = guis[1].getItem(slot);
                ItemStack i1 = guis[0].getItem(slot);

                guis[1].setItem(slot, null);
                guis[0].setItem(slot, null);

                if (i0 != null && i0.getType() != Material.AIR) {
                    int rest = fit(player1, i0);

                    if (rest <= 0) {
                        callTradeEvent(player1, player2, i0);
                        player1.getInventory().addItem(i0);
                    } else {
                        ItemStack toDrop = i0.clone();
                        toDrop.setAmount(rest);

                        i0.setAmount(i0.getAmount() - rest);
                        if (i0.getAmount() > 0) player1.getInventory().addItem(i0);

                        droppedItems[0] |= dropItem(player1, toDrop);
                    }
                    getTradeLog().log(player1, player2, player1.getName() + " received " + i0.getAmount() + "x " + i0.getType());
                }

                if (i1 != null && i1.getType() != Material.AIR) {
                    int rest = fit(player2, i1);

                    if (rest <= 0) {
                        callTradeEvent(player2, player1, i0);
                        player2.getInventory().addItem(i1);
                    } else {
                        ItemStack toDrop = i1.clone();
                        toDrop.setAmount(rest);

                        i1.setAmount(i1.getAmount() - rest);
                        if (i1.getAmount() > 0) player2.getInventory().addItem(i1);

                        droppedItems[1] |= dropItem(player2, toDrop);
                    }
                    getTradeLog().log(player1, player2, player2.getName() + " received " + i1.getAmount() + "x " + i1.getType());
                }
            }

            guis[0].clear();
            guis[1].clear();
            guis[0].close();
            guis[1].close();

            double diff = -money[0] + money[1];
            if (diff < 0) {
                p0.withdraw(-diff);
                getTradeLog().log(player1, player2, player1 + " payed money: " + diff);
            } else if (diff > 0) {
                p0.deposit(diff);
                getTradeLog().log(player1, player2, player1 + " received money: " + diff);
            }

            diff = -money[1] + money[0];
            if (diff < 0) {
                p1.withdraw(-diff);
                getTradeLog().log(player1, player2, player2 + " payed money: " + diff);
            } else if (diff > 0) {
                p1.deposit(diff);
                getTradeLog().log(player1, player2, player2 + " received money: " + diff);
            }

            player1.sendMessage(Lang.getPrefix() + Lang.get("Trade_Was_Finished", player1));
            player2.sendMessage(Lang.getPrefix() + Lang.get("Trade_Was_Finished", player2));

            for (int i = 0; i < droppedItems.length; i++) {
                if (droppedItems[i]) {
                    this.players[i].sendMessage(Lang.getPrefix() + Lang.get("Items_Dropped", this.players[i]));
                }
            }
            getTradeLog().log(player1, player2, "Trade Finished");

            TradeSystem.man().playFinishSound(player1);
            TradeSystem.man().playFinishSound(player2);
        };

        int interval = TradeSystem.man().getCountdownInterval();
        int repetitions = TradeSystem.man().getCountdownRepetitions();

        if (interval == 0 || repetitions == 0) runnable.run();
        else {
            this.countdown = new BukkitRunnable() {
                @Override
                public void run() {
                    if (guis[0] == null || guis[1] == null) {
                        this.cancel();
                        countdownTicks = 0;
                        countdown = null;
                        return;
                    }

                    if (!ready[0] || !ready[1]) {
                        this.cancel();
                        TradeSystem.man().playCountdownStopSound(player1);
                        TradeSystem.man().playCountdownStopSound(player2);
                        countdownTicks = 0;
                        countdown = null;
                        guis[0].synchronizeTitle();
                        guis[1].synchronizeTitle();
                        return;
                    }

                    if (countdownTicks == repetitions) {
                        this.cancel();
                        runnable.run();
                        countdownTicks = 0;
                        countdown = null;
                        return;
                    } else {
                        guis[0].synchronizeTitle();
                        guis[1].synchronizeTitle();
                        TradeSystem.man().playCountdownTickSound(player1);
                        TradeSystem.man().playCountdownTickSound(player2);
                    }

                    countdownTicks++;
                }
            };

            this.countdown.runTaskTimer(TradeSystem.getInstance(), 0, interval);
        }
    }

    private void callTradeEvent(Player receiver, Player sender, ItemStack item) {
        if (item == null) return;
        TradeItemEvent event = new TradeItemEvent(receiver, sender, item);
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.callEvent(event);
    }

    public boolean isFinished() {
        return !TradeSystem.man().getTradeList().contains(this);
    }

    public Player getOther(Player p) {
        if (this.players[0] == null || this.players[1] == null) return null;

        if (this.players[0].equals(p)) return this.players[1];
        else return this.players[0];
    }

    int getOtherId(int id) {
        if (id == 1) return 0;
        else return 1;
    }

    int getId(Player player) {
        if (this.players[0].equals(player)) return 0;
        else if (this.players[1].equals(player)) return 1;
        else return -999;
    }

    List<Integer> getSlots() {
        return slots;
    }

    List<Integer> getOtherSlots() {
        return otherSlots;
    }

    public boolean isParticipant(Player player) {
        return this.players != null && (this.players[0] == player || this.players[1] == player);
    }

    boolean noItemsAdded() {
        if (guis[0] != null && guis[1] != null) {
            for (int i = 0; i < slots.size(); i++) {
                if (guis[0].getItem(slots.get(i)) != null && guis[0].getItem(slots.get(i)).getType() != Material.AIR) return false;
                if (guis[1].getItem(slots.get(i)) != null && guis[1].getItem(slots.get(i)).getType() != Material.AIR) return false;
            }
        }

        return true;
    }

    boolean noMoneyAdded() {
        return this.money[0] == 0 && this.money[1] == 0;
    }

    boolean emptyTrades() {
        return noMoneyAdded() && noItemsAdded();
    }

    public boolean cancelBlockedItems(Player player) {
        List<Integer> blocked = new ArrayList<>();

        for (Integer slot : this.slots) {
            ItemStack item = this.guis[getId(player)].getItem(slot);

            if (item != null && item.getType() != Material.AIR) {
                if (TradeSystem.man().isBlocked(item)) blocked.add(slot);
            }
        }

        for (Integer slot : blocked) {
            ItemStack transport = this.guis[getId(player)].getItem(slot).clone();
            this.guis[getId(player)].setItem(slot, new ItemStack(Material.AIR));

            player.getInventory().addItem(transport);
        }

        player.updateInventory();

        boolean found = !blocked.isEmpty();
        blocked.clear();

        return found;
    }

    public void cancelOverflow(Player player) {
        HashMap<Integer, ItemStack> items = new HashMap<>();
        for (Integer slot : this.slots) {
            ItemStack item = this.guis[getId(player)].getItem(slot);

            if (item != null && item.getType() != Material.AIR) {
                items.put(slot, item);
            }
        }

        if (items.isEmpty()) return;

        HashMap<Integer, ItemStack> sorted = new HashMap<>();
        int size = items.size();
        for (int i = 0; i < size; i++) {
            int slot = 0;
            ItemStack item = null;

            for (int nextSlot : items.keySet()) {
                ItemStack next = items.get(nextSlot);

                if (item == null || item.getAmount() > next.getAmount()) {
                    item = next;
                    slot = nextSlot;
                }
            }

            if (item != null) {
                sorted.put(slot, item);
                items.remove(slot);
            }
        }

        items.clear();
        items.putAll(sorted);
        sorted.clear();

        PlayerInventory inv = new PlayerInventory(getOther(player));
        HashMap<Integer, Integer> toRemove = new HashMap<>();

        items.forEach((slot, item) -> {
            int amount = inv.addUntilPossible(item, true);
            if (amount > 0) toRemove.put(slot, amount);
        });

        items.clear();

        TradingGUI gui = guis[getId(player)];
        for (Integer slot : toRemove.keySet()) {
            ItemStack item = gui.getItem(slot).clone();
            item.setAmount(item.getAmount() - toRemove.get(slot));

            ItemStack transport = gui.getItem(slot).clone();
            transport.setAmount(toRemove.get(slot));

            player.getInventory().addItem(transport);
            gui.setItem(slot, item.getAmount() <= 0 ? new ItemStack(Material.AIR) : item);
        }

        update();
    }

    public boolean fitsTrade(Player from, ItemStack... add) {
        return fitsTrade(from, new ArrayList<>(), add);
    }

    public boolean fitsTrade(Player from, List<Integer> remove, ItemStack... add) {
        List<ItemStack> items = new ArrayList<>(Arrays.asList(add));
        for (Integer slot : this.slots) {
            if (remove.contains(slot)) continue;

            ItemStack item = this.guis[getId(from)].getItem(slot);
            if (item != null && item.getType() != Material.AIR) items.add(item);
        }

        PlayerInventory inv = new PlayerInventory(getOther(from));
        boolean fits = true;

        for (ItemStack item : items) {
            if (!inv.addItem(item)) {
                fits = false;
                break;
            }
        }

        items.clear();
        remove.clear();
        return fits;
    }

    public boolean[] getCursor() {
        return cursor;
    }

    public boolean[] getWaitForPickup() {
        return waitForPickup;
    }

    public BukkitRunnable getCountdown() {
        return countdown;
    }

    public int getCountdownTicks() {
        return countdownTicks;
    }
}
