package pers.defoliation.claybedwars;

import org.bukkit.Location;
import pers.defoliation.minigame.group.Team;

public class BedWarsTeam extends Team {

    private TeamData teamData;

    public BedWarsTeam(TeamData teamData) {
        super(teamData.name, teamData.getPlayerNum());
        this.teamData = teamData;
        teamData.addChangeTask(() -> {
            setMaxPlayer(teamData.getPlayerNum());
            setTeamName(teamData.name);
        });
    }

    public Location getSpawnLocation() {
        return teamData.getSpawnLocation();
    }

    public String getDisplayName() {
        return teamData.getDisplayName();
    }

    @Override
    public int getMaxPlayer() {
        return teamData.getPlayerNum();
    }

    public TeamData getTeamData() {
        return teamData;
    }
}
