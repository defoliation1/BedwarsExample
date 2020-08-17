package pers.defoliation.claybedwars;

import org.bukkit.Location;
import pers.defoliation.minigame.config.Config;

public class TeamData {

    public String name;

    public TeamData(String name) {
        this.name = name;
    }

    @Config
    public String displayName;
    @Config
    public int playerNum;
    @Config
    public Location spawnLocation;
    @Config
    public Location bedLocation;

}
