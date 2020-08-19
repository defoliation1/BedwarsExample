package pers.defoliation.claybedwars;

import org.bukkit.Color;
import org.bukkit.Location;
import pers.defoliation.minigame.config.Config;

import java.util.ArrayList;
import java.util.List;

public class TeamData {

    private List<Runnable> onChange = new ArrayList<>();

    public final String name;

    public TeamData(String name) {
        this.name = name;
    }

    public void addChangeTask(Runnable runnable) {
        this.onChange.add(runnable);
    }

    @Config
    private String displayName;
    @Config
    private int playerNum;
    @Config
    private Location spawnLocation;
    @Config
    private Location bedLocation;
    @Config
    private Color color;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        callChange();
    }

    public int getPlayerNum() {
        return playerNum;
    }

    public void setPlayerNum(int playerNum) {
        this.playerNum = playerNum;
        callChange();
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
        callChange();
    }

    public Location getBedLocation() {
        return bedLocation;
    }

    public void setBedLocation(Location bedLocation) {
        this.bedLocation = bedLocation;
        callChange();
    }

    private void callChange() {
        onChange.forEach(Runnable::run);
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }
}
