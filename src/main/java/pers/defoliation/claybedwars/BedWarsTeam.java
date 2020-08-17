package pers.defoliation.claybedwars;

import org.bukkit.Location;
import pers.defoliation.minigame.group.Team;

public class BedWarsTeam extends Team {

    private TeamData teamData;

    public BedWarsTeam(TeamData teamData) {
        super(teamData.name, teamData.playerNum);
        this.teamData = teamData;
    }

    public Location getSpawnLocation() {
        return teamData.spawnLocation;
    }

    public String getDisplayName() {
        return teamData.displayName;
    }

    @Override
    public int getMaxPlayer() {
        return teamData.playerNum;
    }

    public TeamData getTeamData() {
        return teamData;
    }
}
