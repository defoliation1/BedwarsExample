package pers.defoliation.claybedwars;

import org.bukkit.plugin.java.JavaPlugin;
import pers.defoliation.minigame.game.Game;
import pers.defoliation.minigame.game.GameManager;
import pers.defoliation.minigame.game.GameState;

public class BedWarsGameManager extends GameManager {

    public BedWarsGameManager(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    protected GameState getGameState(Game game) {
        return ((BedWarsGame)game).state;
    }
}
