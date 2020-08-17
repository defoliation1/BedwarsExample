package pers.defoliation.claybedwars;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import pers.defoliation.minigame.config.AnnotationConfig;
import pers.defoliation.minigame.conversation.Conversation;
import pers.defoliation.minigame.conversation.request.RequestBlock;
import pers.defoliation.minigame.conversation.request.RequestInteger;
import pers.defoliation.minigame.conversation.request.RequestString;
import pers.defoliation.minigame.game.Game;
import pers.defoliation.minigame.game.GameState;
import pers.defoliation.minigame.group.GamePlayerGroup;
import pers.defoliation.minigame.group.TeamBalanceGroup;
import pers.defoliation.minigame.listener.MiniGameEventHandler;
import pers.defoliation.minigame.location.LocationManager;
import pers.defoliation.minigame.player.GamePlayer;
import pers.defoliation.minigame.player.PlayerState;
import pers.defoliation.minigame.state.State;
import pers.defoliation.minigame.util.Countdown;
import pers.defoliation.minigame.util.Title;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class BedWarsGame extends Game {

    private static PlayerState joinState = PlayerState.getBuilder()
            .addTask(PlayerState.fullState())
            .addTask(PlayerState.clearInventory())
            .addTask(PlayerState.clearPotionEffect())
            .addTask(player -> player.setGameMode(GameMode.SURVIVAL))
            .addTask(player -> player.setInvulnerable(false))
            .build();

    private static PlayerState spectatorState = PlayerState.getBuilder()
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

    private TeamBalanceGroup group = new TeamBalanceGroup();
    private GameState gameState = GameState.PREPARING;
    private Countdown startTime;
    private MiniGameEventHandler waitEventHandler;
    private MiniGameEventHandler runningEventHandler;
    private LocationManager locationManager = new LocationManager();

    @State
    public GameState state;

    public BedWarsGame(String gameName) {
        super(gameName);

        ConfigurationSection team = getGameConfig().getConfigurationSection("team");
        for (String key : team.getKeys(false)) {
            AnnotationConfig.load(new TeamData(key), team.getConfigurationSection(key));
        }

        ConfigurationSection location = getGameConfig().getConfigurationSection("location");
        locationManager.load(location);

        setStartTime();
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

                }
            }
        }, this::runningIgnore);

        group.addJoinTask(player -> {
            joinState.apply(player);
            player.teleport(locationManager.getLocation("lobby"));
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
            return group.getPlayers().contains(GamePlayer.getGamePlayer(playerByEvent));
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

    private void setStartTime() {
        startTime = Countdown.speedUpWhenFull(60, 5, group, group.getTeams().size())
                .setSecondTask(0, () -> {
                    gameState = GameState.RUNNING;
                    BedWars.INSTANCE.getGameManager().setPlayingGame(this);
                    startTime.resetCountdown();
                })
                .addPerSecondTask(Countdown.setLevel(() -> group.getAlivePlayers()))
                .setSecondTask(Countdown.sendTitle(() -> group.getAlivePlayers(),
                        (player, integer) -> Title.as("§e游戏即将开始", String.format("还有 %d 秒!", integer))),
                        50, 40, 30, 20, 10, 5, 4, 3, 2, 1);
    }

    public void addTeam(TeamData teamData) {
        group.addTeam(new BedWarsTeam(teamData));
        AnnotationConfig.save(teamData, getGameConfig().createSection("team." + teamData.name));
        setStartTime();
    }

    @Override
    public GameState preparing(AtomicInteger atomicInteger) {
        if (locationManager.allLocationIsReady() && group.getTeams().size() > 1) {
            return GameState.WAITING;
        }
        return null;
    }

    @Override
    public GameState waiting(AtomicInteger atomicInteger) {
        startTime.tick();
        return gameState;
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
    public GamePlayerGroup getGroup() {
        return group;
    }

    @Override
    public void setGame(Player player, String[] strings) {
        if (strings == null || strings.length == 0) {
            Conversation conversation = Conversation.newConversation(BedWars.INSTANCE);
            if (locationManager.getUnsetLocation().contains("lobby")) {
                conversation.addRequest(RequestBlock.newRequestBlock()
                        .setOnStart(request -> player.sendMessage("请点击方块确定等待大厅的位置"))
                        .setTimeout(120)
                        .setTimeoutMessage("设置时间已过期")
                        .setOnComplete(blockRequest -> {
                            Block block = blockRequest.getResult().get();
                            locationManager.putLocation("lobby", block.getLocation().add(0, 1, 0));
                            player.sendMessage("设置成功，玩家重生将被设置为钻石3秒");
                            block.setType(Material.DIAMOND_BLOCK);
                            Bukkit.getScheduler().runTaskLater(BedWars.INSTANCE, () -> locationManager.getLocation("lobby").getBlock().setType(Material.AIR), 20 * 3);
                        })
                );
            }
            if (group.getTeams().size() < 2) {
                for (int i = group.getTeams().size(); i < 2; i++) {
                    int finalI = i;
                    conversation.addRequest(RequestString.newRequestString()
                            .setOnStart(request -> {
                                player.sendMessage("现在开始设置队伍 " + finalI + 1);
                                player.sendMessage("请输入队伍名");
                            })
                            .setTimeout(120)
                            .setTimeoutMessage("设置时间已过期")
                            .setOnComplete(stringRequest -> {
                                String s = stringRequest.getResult().get();
                                TeamData teamData = new TeamData(s);
                                conversation.insertRequest(
                                        RequestString.newRequestString()
                                                .setOnStart(request -> player.sendMessage("请输入队伍的显示名称"))
                                                .setTimeout(120)
                                                .setTimeoutMessage("设置时间已过期")
                                                .setOnComplete(displayName -> teamData.displayName = displayName.getResult().get())
                                        , RequestInteger.newRequestInteger()
                                                .setOnStart(request -> player.sendMessage("请输入队伍的玩家数量"))
                                                .setTimeout(120)
                                                .setTimeoutMessage("设置时间已过期")
                                                .setOnComplete(playerNum -> teamData.playerNum = playerNum.getResult().get())
                                        , RequestBlock.newRequestBlock()
                                                .setOnStart(request -> player.sendMessage("请点击方块确定队伍位置"))
                                                .setTimeout(120)
                                                .setTimeoutMessage("设置时间已过期")
                                                .setOnComplete(blockRequest -> {
                                                    Block block = blockRequest.getResult().get();
                                                    Location location = block.getLocation().add(0, 1, 0);
                                                    teamData.spawnLocation = location;
                                                    player.sendMessage("设置成功，玩家重生的位置将被设置为钻石3秒");
                                                    location.getBlock().setType(Material.DIAMOND_BLOCK);
                                                    Bukkit.getScheduler().runTaskLater(BedWars.INSTANCE, () -> location.getBlock().setType(Material.AIR), 20 * 3);
                                                })
                                );
                                if (teamData.spawnLocation != null && teamData.playerNum != 0 && teamData.displayName != null) {
                                    player.sendMessage("队伍添加成功");
                                    group.addTeam(new BedWarsTeam(teamData));
                                } else {
                                    player.sendMessage("队伍添加失败");
                                }
                            })
                    );
                }
            }
        }
    }

}
