package de.codingair.tradesystem.trade;

import de.codingair.codingapi.API;
import de.codingair.codingapi.files.ConfigFile;
import de.codingair.codingapi.player.gui.anvil.AnvilGUI;
import de.codingair.codingapi.server.sounds.Sound;
import de.codingair.codingapi.server.sounds.SoundData;
import de.codingair.codingapi.tools.io.JSON.JSON;
import de.codingair.codingapi.tools.io.lib.JSONArray;
import de.codingair.tradesystem.TradeSystem;
import de.codingair.tradesystem.extras.bstats.MetricsManager;
import de.codingair.tradesystem.utils.Lang;
import de.codingair.tradesystem.utils.blacklist.BlockedItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.MalformedParametersException;
import java.util.*;

import static de.codingair.tradesystem.tradelog.TradeLogService.getTradeLog;

public class TradeManager {
    private final Set<Player> offline = new HashSet<>();
    private final Set<Trade> tradeList = new HashSet<>();
    private final List<BlockedItem> blacklist = new ArrayList<>();
    private final HashMap<String, Integer> moneyShortcuts = new HashMap<>();
    private int requestExpirationTime = 60;
    private int distance = 50;

    private int countdownRepetitions = 0;
    private int countdownInterval = 0;

    private boolean cancelOnDamage = true;
    private boolean requestOnShiftRightclick = false;
    private List<String> allowedGameModes = new ArrayList<>();
    private List<String> blockedWorlds;

    private boolean tradeBoth = true;
    private boolean dropItems = true;
    private boolean tradeMoney = true;

    private SoundData soundStarted = null;
    private SoundData soundFinish = null;
    private SoundData soundCancel = null;
    private SoundData soundBlocked = null;
    private SoundData soundRequest = null;
    private SoundData countdownTick = null;
    private SoundData countdownStop = null;

    public void load() {
        ConfigFile file = TradeSystem.getInstance().getFileManager().getFile("Config");
        FileConfiguration config = file.getConfig();

        boolean save = false;
        requestExpirationTime = config.getInt("TradeSystem.Trade_Request_Expiration_Time", 60);
        if (requestExpirationTime <= 10) {
            config.set("TradeSystem.Trade_Request_Expiration_Time", 10);
            save = true;
        }

        if (config.getBoolean("TradeSystem.Trade_Distance.enabled", true)) {
            this.distance = config.getInt("TradeSystem.Trade_Distance.distance_in_blocks", 50);
            if (this.distance < 1) {
                config.set("TradeSystem.Trade.distance_in_blocks", 1);
                save = true;
            }
        } else this.distance = -1;

        this.cancelOnDamage = config.getBoolean("TradeSystem.Action_To_Cancel.Player_get_damaged", true);
        this.requestOnShiftRightclick = config.getBoolean("TradeSystem.Action_To_Request.Shift_Rightclick", false);
        this.tradeBoth = config.getBoolean("TradeSystem.Trade_Both", true);
        this.dropItems = config.getBoolean("TradeSystem.Trade_Drop_Items", true);
        this.tradeMoney = config.getBoolean("TradeSystem.Trade_with_money", true);

        if (config.getBoolean("TradeSystem.Trade_Countdown.Enabled", true)) {
            countdownRepetitions = config.getInt("TradeSystem.Trade_Countdown.Repetitions");
            countdownInterval = config.getInt("TradeSystem.Trade_Countdown.Interval");
        } else {
            countdownRepetitions = (countdownInterval = 0);
        }

        moneyShortcuts.clear();
        if (config.getBoolean("TradeSystem.Easy_Money_Selection.Enabled", true)) {
            List<?> data = config.getList("TradeSystem.Easy_Money_Selection.Shortcuts");
            if (data != null) {
                for (Object s : data) {
                    if (s instanceof Map) {
                        try {
                            JSON json = new JSON((Map<?, ?>) s);

                            int value = json.getInteger("Value", -1);
                            if (value < 0) continue;

                            JSONArray a = json.getList("Keys");

                            if (a == null) continue;
                            for (Object o : a) {
                                String key = ((String) o).trim().toLowerCase();
                                moneyShortcuts.put(key, value);
                            }
                        } catch (Exception e) {
                            throw new MalformedParametersException("Malformed money selection for input: '" + s + "'");
                        }
                    }
                }
            }
        }

        TradeSystem.log("  > Loading sounds");

        this.soundStarted = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Trade_Started.Name", null)).ifPresent(s -> this.soundStarted = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Trade_Started.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Trade_Started.Pitch", 1.0)));
        } catch (Exception ex) {
        }
        if (this.soundStarted == null) TradeSystem.log("    > No start sound detected (maybe a spelling mistake?)");

        this.soundFinish = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Trade_Finished.Name", null)).ifPresent(s -> this.soundFinish = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Trade_Finished.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Trade_Finished.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.soundFinish == null) TradeSystem.log("    > No finish sound detected (maybe a spelling mistake?)");

        this.soundBlocked = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Trade_Blocked.Name", null)).ifPresent(s -> this.soundBlocked = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Trade_Blocked.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Trade_Blocked.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.soundBlocked == null) TradeSystem.log("    > No itemBlocked sound detected (maybe a spelling mistake?)");

        this.soundCancel = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Trade_Cancelled.Name", null)).ifPresent(s -> this.soundCancel = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Trade_Cancelled.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Trade_Cancelled.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.soundCancel == null) TradeSystem.log("    > No cancel sound detected (maybe a spelling mistake?)");

        this.soundRequest = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Trade_Request.Name", null)).ifPresent(s -> this.soundRequest = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Trade_Request.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Trade_Request.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.soundRequest == null) TradeSystem.log("    > No request sound detected (maybe a spelling mistake?)");

        this.countdownTick = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Countdown_Tick.Name", null)).ifPresent(s -> this.countdownTick = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Countdown_Tick.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Countdown_Tick.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.countdownTick == null) TradeSystem.log("    > No countdown tick sound detected (maybe a spelling mistake?)");

        this.countdownStop = null;
        try {
            Sound.matchXSound(config.getString("TradeSystem.Sounds.Countdown_Stop.Name", null)).ifPresent(s -> this.countdownStop = new SoundData(s, (float) config.getDouble("TradeSystem.Sounds.Countdown_Stop.Volume", 0.6), (float) config.getDouble("TradeSystem.Sounds.Countdown_Stop.Pitch", 1.0)));
        } catch (Exception ignored) {
        }
        if (this.countdownStop == null) TradeSystem.log("    > No countdown stop sound detected (maybe a spelling mistake?)");

        if (this.allowedGameModes != null) this.allowedGameModes.clear();
        this.allowedGameModes = config.getStringList("TradeSystem.Allowed_GameModes");
        if (this.allowedGameModes == null) this.allowedGameModes = new ArrayList<>();

        if (this.blockedWorlds != null) this.blockedWorlds.clear();
        this.blockedWorlds = config.getStringList("TradeSystem.Blocked_Worlds");
        if (this.blockedWorlds == null) this.blockedWorlds = new ArrayList<>();

        TradeSystem.log("  > Loading blacklist");
        List<String> l = config.getStringList("TradeSystem.Blacklist");
        if (l != null && !l.isEmpty()) {
            for (String s : l) {
                BlockedItem item = BlockedItem.fromString(s);
                if (item != null) this.blacklist.add(item);
                else {
                    TradeSystem.log("    ...found a wrong Material-Tag (here: \"" + s + "\"). Skipping...");
                }
            }
        }

        if (this.blacklist.isEmpty()) {
            this.blacklist.add(new BlockedItem(Material.AIR));
            this.blacklist.add(new BlockedItem("&cExample, which blocks all items with this strange name!"));
            saveBlackList();
        }

        TradeSystem.log("    ...got " + this.blacklist.size() + " blocked item(s)");

        if (save) file.saveConfig();
    }

    public void playRequestSound(Player player) {
        if (this.soundRequest != null) this.soundRequest.play(player);
    }

    public void playStartSound(Player player) {
        if (this.soundStarted != null) this.soundStarted.play(player);
    }

    public void playFinishSound(Player player) {
        if (this.soundFinish != null) this.soundFinish.play(player);
    }

    public void playBlockSound(Player player) {
        if (this.soundBlocked != null) this.soundBlocked.play(player);
    }

    public void playCancelSound(Player player) {
        if (this.soundCancel != null) this.soundCancel.play(player);
    }

    public void playCountdownTickSound(Player player) {
        if (this.countdownTick != null) this.countdownTick.play(player);
    }

    public void playCountdownStopSound(Player player) {
        if (this.countdownStop != null) this.countdownStop.play(player);
    }

    public void saveBlackList() {
        ConfigFile file = TradeSystem.getInstance().getFileManager().getFile("Config");
        FileConfiguration config = file.getConfig();
        List<String> l = new ArrayList<>();

        for (BlockedItem block : this.blacklist) {
            l.add(block.toString());
        }

        config.set("TradeSystem.Blacklist", l);
        file.saveConfig();
    }

    public void startTrade(Player player, Player other) {
        if (API.getRemovable(player, TradingGUI.class) != null || API.getRemovable(player, AnvilGUI.class) != null || API.getRemovable(other, TradingGUI.class) != null || API.getRemovable(other, AnvilGUI.class) != null) {
            player.sendMessage(Lang.getPrefix() + Lang.get("Other_is_already_trading", player));
            return;
        }

        getTradeLog().log(other, player, "Trade started");
        player.closeInventory();
        other.closeInventory();

        MetricsManager.TRADES++;

        Bukkit.getScheduler().runTaskLater(TradeSystem.getInstance(), () -> {
            Trade trade = new Trade(other, player);
            this.tradeList.add(trade);
            trade.start();
        }, 5L);
    }

    public void cancelAll() {
        List<Trade> tradeList = new ArrayList<>(this.tradeList);

        for (Trade trade : tradeList) {
            trade.cancel();
        }

        tradeList.clear();

        TradeSystem.getInstance().getTradeCMD().getInvites().clear();
    }

    public Set<Trade> getTradeList() {
        return tradeList;
    }

    public Trade getTrade(Player player) {
        for (Trade trade : this.tradeList) {
            if (trade.isParticipant(player)) return trade;
        }

        return null;
    }


    public boolean isTrading(Player player) {
        return getTrade(player) != null;
    }

    public int getRequestExpirationTime() {
        return requestExpirationTime;
    }

    public int getDistance() {
        return distance;
    }

    public boolean isRequestOnShiftRightclick() {
        return requestOnShiftRightclick;
    }

    public List<String> getAllowedGameModes() {
        return allowedGameModes;
    }

    public boolean isCancelOnDamage() {
        return cancelOnDamage;
    }

    public boolean isTradeBoth() {
        return tradeBoth;
    }

    public boolean isDropItems() {
        return dropItems;
    }

    public void setDropItems(boolean dropItems) {
        this.dropItems = dropItems;
    }

    public boolean isOffline(Player player) {
        return this.offline.contains(player);
    }

    public boolean toggle(Player player) {
        if (offline.remove(player)) return false;
        this.offline.add(player);
        return true;
    }

    public List<BlockedItem> getBlacklist() {
        return blacklist;
    }

    public boolean isBlocked(ItemStack item) {
        for (BlockedItem blocked : this.blacklist) {
            if (blocked.matches(item)) return true;
        }

        return false;
    }

    public boolean isTradeMoney() {
        return tradeMoney;
    }

    public List<String> getBlockedWorlds() {
        return blockedWorlds;
    }

    public boolean isBlockedWorld(World w) {
        if (w == null) return true;

        for (String world : this.blockedWorlds) {
            if (w.getName().equalsIgnoreCase(world)) return true;
        }

        return false;
    }

    public Integer getMoneyShortcutFactor(String s) {
        String key = s.replaceAll("[^a-z]", "");
        return moneyShortcuts.get(key);
    }

    public String makeMoneyFancy(int money) {
        StringBuilder s = new StringBuilder();

        //1,000,000
        char[] c = (money + "").toCharArray();
        for (int i = c.length - 1; i >= 0; i--) {
            if ((s.length() + 1) % 4 == 0) s.insert(0, ",");
            s.insert(0, c[i]);
        }

        return s.toString();
    }

    public int getCountdownRepetitions() {
        return countdownRepetitions;
    }

    public int getCountdownInterval() {
        return countdownInterval;
    }
}
