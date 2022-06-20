package de.codingair.codingapi.server.sounds;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MusicData extends SoundData {
    private final int delay;
    private MusicData follower;

    public MusicData(Sound sound, float volume, float pitch, int delay) {
        super(sound, volume, pitch);
        this.delay = delay;
    }

    public MusicData(SoundData data, int delay) {
        super(data.getSound(), data.getVolume(), data.getPitch());
        this.delay = delay;
    }

    public void play(Player player, JavaPlugin plugin) {
        BukkitRunnable runnable = new BukkitRunnable() {
            int currentDelay = 0;
            MusicData musicPlayer = MusicData.this;

            @Override
            public void run() {
                if(currentDelay == musicPlayer.getDelay()) {
                    currentDelay = 0;

                    musicPlayer.getSound().playSound(player, musicPlayer.getVolume(), musicPlayer.getPitch());

                    if(musicPlayer.getFollower() == null) {
                        this.cancel();
                        return;
                    }

                    musicPlayer = musicPlayer.getFollower();
                } else currentDelay++;
            }
        };

        runnable.runTaskTimer(plugin, 0, 1);
    }

    public MusicData getFollower() {
        return follower;
    }

    public void setFollower(MusicData follower) {
        this.follower = follower;
    }

    public int getDelay() {
        return delay;
    }
}
