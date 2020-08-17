package pers.defoliation.claybedwars;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import pers.defoliation.minigame.listener.MiniGameEventHandler;
import pers.defoliation.minigame.player.GamePlayer;

import java.util.function.Function;

public class BedWars extends JavaPlugin {

    public static BedWars INSTANCE;

    private MiniGameEventHandler spectateListener;

    private BedWarsGameManager bedWarsGameManager;

    public BedWars() {
        INSTANCE = this;
    }

    public BedWarsGameManager getGameManager() {
        return bedWarsGameManager;
    }

    @Override
    public void onEnable() {
        spectateListener = new MiniGameEventHandler(this);
        spectateListener.addHandle(PlayerPickupItemEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(PlayerPickupArrowEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(PlayerDropItemEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(InventoryClickEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(InventoryOpenEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(BlockBreakEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(BlockPlaceEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(PlayerInteractEvent.class, MiniGameEventHandler.cancel(), ignoreWhenGameEnd())
                .addHandle(PlayerTeleportEvent.class, event -> {
                    if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN)
                        event.setCancelled(true);
                }, ignoreWhenGameEnd());
        bedWarsGameManager = new BedWarsGameManager(this);
    }

    public static <T extends Event> Function<T, Boolean> ignoreWhenGameEnd() {
        return event -> {
            Player playerByEvent = MiniGameEventHandler.getPlayerByEvent(event);
            if (playerByEvent == null)
                return true;
            return !GamePlayer.getGamePlayer(playerByEvent).isSpectator() || MiniGameEventHandler.ignoreCancel().apply(event) || INSTANCE.bedWarsGameManager.getPlayingGame() == null;
        };
    }

}
