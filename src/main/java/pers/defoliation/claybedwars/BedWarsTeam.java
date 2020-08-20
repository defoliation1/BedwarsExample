package pers.defoliation.claybedwars;

import org.bukkit.Location;
import org.bukkit.Material;
import pers.defoliation.minigame.group.Team;

public class BedWarsTeam extends Team {

    private BedWarsGame game;
    private TeamData teamData;

    public BedWarsTeam(BedWarsGame game, TeamData teamData) {
        super(teamData.name, game.getTeamSize());
        this.teamData = teamData;
        teamData.addChangeTask(() -> setTeamName(teamData.name));
        this.game = game;
    }

    public Location getSpawnLocation() {
        return teamData.getSpawnLocation();
    }

    public String getDisplayName() {
        return teamData.getDisplayName();
    }

    public TeamData getTeamData() {
        return teamData;
    }

    public boolean existBed() {
        return teamData.getBedLocation().getBlock().getType() == Material.BED_BLOCK;
    }

}
