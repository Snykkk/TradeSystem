package de.codingair.tradesystem.tradelog.repository.adapters;

import de.codingair.tradesystem.tradelog.TradeLog;
import de.codingair.tradesystem.tradelog.repository.TradeLogRepository;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class LoggingTradeLogRepository implements TradeLogRepository {

    @Override
    public void log(Player player1, Player player2, String message) {
        Bukkit.getLogger().info("TRADELOG [" + player1.getName() + " , " + player2.getName() + "] " + message);
    }

    @Override
    public List<TradeLog> getLogMessages(String player) {
        return Collections.emptyList();
    }
}
