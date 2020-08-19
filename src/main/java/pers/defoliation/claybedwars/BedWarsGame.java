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
import pers.defoliation.minigame.conversation.request.*;
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
import java.util.Optional;
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
                conversation.addRequest(setLobby(player));
            }
            if (group.getTeams().size() < 2) {
                for (int i = group.getTeams().size(); i < 2; i++) {
                    conversation.addRequest(addTeam(player));
                }
            }

            conversation.start(player);
        } else {
            if (strings.length == 1) {
                if ("lobby".equalsIgnoreCase(strings[0])) {
                    Conversation conversation = Conversation.newConversation(BedWars.INSTANCE);
                    conversation.addRequest(setLobby(player));
                    conversation.start(player);
                } else if ("addTeam".equalsIgnoreCase(strings[0])) {
                    Conversation conversation = Conversation.newConversation(BedWars.INSTANCE);
                    conversation.addRequest(addTeam(player));
                    conversation.start(player);
                }
            }
        }
    }

    private Request setLobby(Player player) {
        return getRequest(RequestBlock.newRequestBlock(), "请点击方块确定等待大厅的位置")
                .setOnComplete(blockRequest -> {
                    Block block = blockRequest.getResult().get();
                    locationManager.putLocation("lobby", block.getLocation().add(0, 1, 0));
                    player.sendMessage("设置成功，玩家重生将被设置为钻石3秒");
                    block.setType(Material.DIAMOND_BLOCK);
                    Bukkit.getScheduler().runTaskLater(BedWars.INSTANCE, () -> locationManager.getLocation("lobby").getBlock().setType(Material.AIR), 20 * 3);
                });
    }

    private Request addTeam(Player player) {
        return getRequest(RequestString.newRequestString(), "现在开始添加队伍").setOnComplete(stringRequest -> {
            String s = stringRequest.getResult().get();
            TeamData teamData = new TeamData(s);
            stringRequest.getConversation().insertRequest(
                    setTeamDisplayName(player, teamData)
                    , setTeamNum(player, teamData)
                    , setTeamRespawnLocation(player, teamData)
                    , setTeamBedLocation(player, teamData)
                    , new RequestBase<Object>() {
                        @Override
                        public void start() {
                            if (teamData.getSpawnLocation() != null && teamData.getPlayerNum() != 0 && teamData.getDisplayName() != null) {
                                player.sendMessage("队伍添加成功");
                                group.addTeam(new BedWarsTeam(teamData));
                            } else {
                                player.sendMessage("队伍添加失败");
                            }
                            setCompleted(true);
                        }

                        @Override
                        public void reset() {
                        }

                        @Override
                        public Optional<Object> getResult() {
                            return Optional.empty();
                        }
                    }
            );
        });
    }

    private Request setTeamDisplayName(Player player, TeamData teamData) {
        return getRequest(RequestString.newRequestString(), "请输入队伍的显示名称")
                .setOnComplete(displayName -> {
                    teamData.setDisplayName(displayName.getResult().get());
                    player.sendMessage("设置成功");
                });
    }

    private Request setTeamNum(Player player, TeamData teamData) {
        return getRequest(RequestInteger.newRequestInteger(), "请输入队伍的玩家数量")
                .setOnComplete(playerNum -> {
                    teamData.setPlayerNum(playerNum.getResult().get());
                    player.sendMessage("设置成功");
                });
    }

    private Request setTeamRespawnLocation(Player player, TeamData teamData) {
        return getRequest(RequestBlock.newRequestBlock(), "请选择一个方块确定队伍重生位置")
                .setOnComplete(request -> {
                    Block block = request.getResult().get();
                    Location location = block.getLocation().add(0, 1, 0);
                    teamData.setSpawnLocation(location);
                    player.sendMessage("设置成功，玩家重生的位置将被设置为钻石3秒");
                    location.getBlock().setType(Material.DIAMOND_BLOCK);
                    Bukkit.getScheduler().runTaskLater(BedWars.INSTANCE, () -> location.getBlock().setType(Material.AIR), 20 * 3);
                });
    }

    private Request setTeamBedLocation(Player player, TeamData teamData) {
        return getRequest(RequestBlock.newRequestBlock(), "请为这个队伍选择一个床")
                .setOnComplete(request -> {
                    Block block = request.getResult().get();
                    if (block.getType() != Material.BED_BLOCK) {
                        player.sendMessage("你选择的方块不是床，请重新选择");
                        request.getConversation().insertRequest(setTeamRespawnLocation(player, teamData));
                        return;
                    }
                    teamData.setBedLocation(block.getLocation());
                    player.sendMessage("设置成功");
                });
    }

    private static <T> Request<T> getRequest(Request<T> request, String startMessage) {
        return request.setTimeout(120)
                .setOnStart(request1 -> request1.getConversation().getPlayer().sendMessage(startMessage))
                .setTimeoutMessage("设置时间已过期");
    }

}
