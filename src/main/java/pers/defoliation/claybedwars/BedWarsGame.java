package pers.defoliation.claybedwars;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import pers.defoliation.minigame.config.ConfigChecker;
import pers.defoliation.minigame.conversation.request.Request;
import pers.defoliation.minigame.game.Game;
import pers.defoliation.minigame.game.GameState;
import pers.defoliation.minigame.group.TeamBalanceGroup;
import pers.defoliation.minigame.listener.MiniGameEventHandler;
import pers.defoliation.minigame.player.GamePlayer;
import pers.defoliation.minigame.player.PlayerState;
import pers.defoliation.minigame.state.ChangeIn;
import pers.defoliation.minigame.state.ChangeOut;
import pers.defoliation.minigame.state.State;
import pers.defoliation.minigame.util.Countdown;
import pers.defoliation.minigame.util.Title;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class BedWarsGame extends Game {

    private static final PlayerState joinState = PlayerState.getBuilder()
            .addTask(PlayerState.fullState())
            .addTask(PlayerState.clearInventory())
            .addTask(PlayerState.clearPotionEffect())
            .addTask(player -> {
                player.setGameMode(GameMode.SURVIVAL);
                player.setInvulnerable(false);
                player.setFlying(false);
            })
            .build();

    private static final PlayerState spectatorState = PlayerState.getBuilder()
            .addTask(player -> {
                joinState.apply(player);
                GamePlayer.getGamePlayer(player).setSpectator(true);
                BedWars.INSTANCE.getGameManager().getPlayingGame().getGroup().getPlayers().stream().map(GamePlayer::getPlayer)
                        .filter(player1 -> player1 != null && player1.isOnline())
                        .forEach(player1 -> player1.hidePlayer(BedWars.INSTANCE, player));
                player.setGameMode(GameMode.ADVENTURE);
                player.setInvulnerable(true);
                player.setAllowFlight(true);
            })
            .build();

    private static final PlayerState playerGameState = PlayerState.getBuilder()
            .addTask(player -> {
                joinState.apply(player);
                GamePlayer gamePlayer = GamePlayer.getGamePlayer(player);
                BedWarsGame bedWarsGame = (BedWarsGame) gamePlayer.playingGame();
                BedWarsTeam teamByPlayer = bedWarsGame.getGroup().getTeamByPlayer(player);
                Color color = teamByPlayer.getColor();
                ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
                LeatherArmorMeta itemMeta = (LeatherArmorMeta) helmet.getItemMeta();
                ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
                ItemStack legging = new ItemStack(Material.LEATHER_LEGGINGS);
                ItemStack boot = new ItemStack(Material.LEATHER_BOOTS);
                itemMeta.setColor(color);
                helmet.setItemMeta(itemMeta);
                chest.setItemMeta(itemMeta);
                legging.setItemMeta(itemMeta);
                boot.setItemMeta(itemMeta);
                PlayerInventory inventory = player.getInventory();
                inventory.setHelmet(helmet);
                inventory.setChestplate(chest);
                inventory.setLeggings(legging);
                inventory.setBoots(boot);
            }).build();

    private static final Countdown<BedWarsGame> lobbyCountdown = Countdown.<BedWarsGame>getBuilder(BedWars.INSTANCE)
            .whenEnd(game -> {
                game.gameState = GameState.RUNNING;
                game.getGroup().getAlivePlayers().forEach(player -> {
                    playerGameState.apply(player);
                    player.teleport(game.getGroup().getTeamByPlayer(player).getSpawnLocation());
                });
            })
            .addPerSecondTask((game, atomicInteger) -> {
                if (game.state != GameState.WAITING) {
                    atomicInteger.set(60);
                    return;
                }
                if (game.getGroup().getPlayers().size() < game.getGroup().getTeams().size()) {
                    atomicInteger.set(60);
                } else if (atomicInteger.get() > 5 && game.getGroup().getPlayers().size() == game.getGroup().getMaxPlayer()) {
                    atomicInteger.set(5);
                }
                game.getGroup().getAlivePlayers().forEach(player -> player.setLevel(atomicInteger.get()));
            })
            .setSecondTask((game, atomicInteger) -> game.getGroup().getAlivePlayers()
                            .forEach(player -> Title.as("等待复活", String.format("还有 %d 秒", atomicInteger.get())).send(player))
                    , 50, 40, 30, 20, 10, 5, 4, 3, 2, 1)
            .build();

    private static final Countdown<GamePlayer> playerRespawnCountdown = Countdown.<GamePlayer>getBuilder(BedWars.INSTANCE)
            .whenEnd(player -> {
                BedWarsTeam teamByPlayer = ((BedWarsGame) player.playingGame()).getGroup().getTeamByPlayer(player);
                player.getPlayer().teleport(teamByPlayer.getSpawnLocation());
                playerGameState.apply(player.getPlayer());
            })
            .addPerSecondTask((player, atomicInteger) -> {
                BedWarsTeam teamByPlayer = ((BedWarsGame) player.playingGame()).getGroup().getTeamByPlayer(player);
                if (teamByPlayer != null && teamByPlayer.existBed()) {
                    Title.as("等待复活", String.format("还有 %d 秒", atomicInteger.get())).send(player.getPlayer());
                } else {
                    BedWarsGame.playerRespawnCountdown.cancel(player);
                }
            })
            .build();

    private TeamBalanceGroup<BedWarsTeam> group = new TeamBalanceGroup();
    private GameState gameState = GameState.WAITING;
    private MiniGameEventHandler waitEventHandler;
    private MiniGameEventHandler runningEventHandler;
    private int teamSize;

    private void requestSendMessage(Request request, String message) {
        request.getConversation().getPlayer().sendMessage(message);
    }

    private ConfigChecker checker = new ConfigChecker().checkEmpty("team", "teamSize", "lobbyLocation", "spectatorLocation");

    @State
    public GameState state;

    public BedWarsGame(String gameName) {
        super(gameName);

        List<?> team = getGameConfig().getList("team");
        if (!team.isEmpty())
            for (Object o : team) {
                if (o instanceof BedWarsTeam)
                    group.addTeam((BedWarsTeam) o);
            }

        teamSize = getGameConfig().getInt("timeSize", 4);

        waitEventHandler = new MiniGameEventHandler(BedWars.INSTANCE);
        waitEventHandler.addHandle(EntityDamageEvent.class, MiniGameEventHandler.cancel(), this::waitIgnore)
                .addHandle(BlockBreakEvent.class, MiniGameEventHandler.cancel(), this::waitIgnore)
                .addHandle(BlockPlaceEvent.class, MiniGameEventHandler.cancel(), this::waitIgnore)
                .addHandle(PlayerInteractEvent.class, MiniGameEventHandler.cancel(), this::waitIgnore);

        runningEventHandler = new MiniGameEventHandler(BedWars.INSTANCE);
        runningEventHandler.addHandle(EntityDamageByEntityEvent.class, event -> {
            Entity entity = event.getEntity();
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (event.getFinalDamage() >= player.getHealth()) {
                    spectatorState.apply(player);
                    playerRespawnCountdown.start(10, GamePlayer.getGamePlayer(player));
                }
            }
        }, this::runningIgnore)
                .addHandle(PlayerMoveEvent.class, event -> {
                    if (event.getTo().getY() < 0) {
                        Player player = event.getPlayer();
                        GamePlayer gamePlayer = GamePlayer.getGamePlayer(player);
                        if (!gamePlayer.isSpectator()) {
                            spectatorState.apply(player);
                            playerRespawnCountdown.start(10, GamePlayer.getGamePlayer(player));
                        }
                        event.getPlayer().teleport((Location) getGameConfig().get("lobbyLocation"));
                    }
                }, this::runningIgnore)
        ;

        group.addJoinTask(player -> {
            joinState.apply(player);
            player.teleport((Location) getGameConfig().get("lobbyLocation"));
        });
    }

    private boolean waitIgnore(Event event) {
        return !isState(GameState.WAITING) || isCancelled(event) || !isGamePlayer(event);
    }

    private boolean isState(GameState state) {
        return this.state == state;
    }

    private boolean isCancelled(Event event) {
        return MiniGameEventHandler.ignoreCancel().apply(event);
    }

    private boolean isGamePlayer(Event event) {
        Player playerByEvent = MiniGameEventHandler.getPlayerByEvent(event);
        if (playerByEvent != null) {
            GamePlayer gamePlayer = GamePlayer.getGamePlayer(playerByEvent);
            return !gamePlayer.isSpectator() && group.getPlayers().contains(gamePlayer);
        }
        return false;
    }

    private boolean runningIgnore(Event event) {
        return !isState(GameState.RUNNING) || isCancelled(event) || !isGamePlayer(event);
    }

    @Override
    public File getDataFolder() {
        return BedWars.INSTANCE.getDataFolder();
    }

    public void addTeam(String teamName) {
        group.addTeam(new BedWarsTeam(this, teamName));
    }

    public int getTeamSize() {
        return teamSize;
    }

    @Override
    public GameState preparing(AtomicInteger atomicInteger) {
        if (checker.allComplete(getGameConfig()) && group.getTeams().size() > 1) {
            return GameState.WAITING;
        }
        if (atomicInteger.get() % (20 * 30) == 0) {
            Bukkit.getLogger().warning(getGameName() + " 配置缺失，无法开始");
        }
        return null;
    }

    @Override
    public GameState waiting(AtomicInteger atomicInteger) {
        return gameState;
    }

    @ChangeIn(GameState.WAITING)
    public void onChangeInWaiting() {
        lobbyCountdown.start(60, this);
    }

    @ChangeOut(GameState.WAITING)
    public void onChangeOutWaiting() {
        lobbyCountdown.cancel(this);
    }

    @Override
    public GameState running(AtomicInteger atomicInteger) {

        return null;
    }

    @Override
    public GameState ended(AtomicInteger atomicInteger) {
        return null;
    }

    @Override
    public boolean canJoin(Player player) {
        if (state == GameState.WAITING)
            return group.canJoin(player);
        return state == GameState.RUNNING;
    }

    public TeamBalanceGroup<BedWarsTeam> getGroup() {
        return group;
    }

    @Override
    public void join(Player player) {
        if (state == GameState.WAITING) {
            group.join(player);
            return;
        }
        spectatorState.apply(player);
        group.addSpectator(player);
        GamePlayer.getGamePlayer(player).setPlayingGame(this);
        player.teleport((Location) getGameConfig().get("spectatorLocation"));
    }

    @Override
    public void leave(Player player) {
        group.leave(player);
        GamePlayer.getGamePlayer(player).setPlayingGame(null);
    }

    private ItemStack[] getColorLeather(List<Color> colors) {
        List<ItemStack> itemStacks = new ArrayList<>();
        for (Color color : colors) {
            ItemStack itemStack = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) itemStack.getItemMeta();
            leatherArmorMeta.setColor(color);
            itemStack.setItemMeta(leatherArmorMeta);
            itemStacks.add(itemStack);
        }
        return itemStacks.toArray(new ItemStack[0]);
    }

    private List<Color> getCanChooseColor() {
        List<Color> colorList = new ArrayList<>();
        for (Field field : Color.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                try {
                    colorList.add((Color) field.get(null));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return colorList;
    }

}
