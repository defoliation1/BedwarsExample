package pers.defoliation.claybedwars;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import pers.defoliation.minigame.group.Team;

import java.util.HashMap;
import java.util.Map;

public class BedWarsTeam extends Team implements ConfigurationSerializable {

    private BedWarsGame game;

    private String displayName;
    private Location spawnLocation;
    private Location bedLocation;
    private Color color;

    public BedWarsTeam(BedWarsGame game, String name) {
        super(name, game.getTeamSize());
        this.game = game;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Location getSpawnLocation() {
        return spawnLocation;
    }

    public void setSpawnLocation(Location spawnLocation) {
        this.spawnLocation = spawnLocation;
    }

    public Location getBedLocation() {
        return bedLocation;
    }

    public void setBedLocation(Location bedLocation) {
        this.bedLocation = bedLocation;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean existBed() {
        return getBedLocation().getBlock().getType() == Material.BED_BLOCK;
    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String,Object> map = new HashMap<>();
        map.put("name",getTeamName());
        map.put("displayName",getDisplayName());
        map.put("spawnLocation",spawnLocation);
        map.put("bedLocation",bedLocation);
        map.put("color",color);
        return map;
    }
}
