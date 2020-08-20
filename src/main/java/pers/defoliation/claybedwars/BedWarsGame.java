package pers.defoliation.claybedwars;

import org.bukkit.*;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import pers.defoliation.minigame.config.AnnotationConfig;
import pers.defoliation.minigame.conversation.Conversation;
import pers.defoliation.minigame.conversation.request.*;
import pers.defoliation.minigame.game.Game;
import pers.defoliation.minigame.game.GameState;
import pers.defoliation.minigame.group.TeamBalanceGroup;
import pers.defoliation.minigame.listener.MiniGameEventHandler;
import pers.defoliation.minigame.location.LocationManager;
import pers.defoliation.minigame.player.GamePlayer;
import pers.defoliation.minigame.player.PlayerState;
import pers.defoliation.minigame.state.State;
import pers.defoliation.minigame.util.Countdown;
import pers.defoliation.minigame.util.IndexedItemStack;
import pers.defoliation.minigame.util.Title;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
                Color color = teamByPlayer.getTeamData().getColor();
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

    private TeamBalanceGroup<BedWarsTeam> group = new TeamBalanceGroup();
    private GameState gameState = GameState.WAITING;
    private Countdown startTime;
    private MiniGameEventHandler waitEventHandler;
    private MiniGameEventHandler runningEventHandler;
    private LocationManager locationManager = new LocationManager();
    private int teamSize;

    @State
    public GameState state;

    public BedWarsGame(String gameName) {
        super(gameName);

        ConfigurationSection teamConfig = getGameConfig().getConfigurationSection("team");
        for (String key : teamConfig.getKeys(false)) {
            AnnotationConfig.load(new TeamData(key), teamConfig.getConfigurationSection(key));
        }

        teamSize = getGameConfig().getInt("timeSize");

        locationManager.request("lobby", "spectatorLocation");

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
                GamePlayer gamePlayer = GamePlayer.getGamePlayer(player);
                if (!gamePlayer.isSpectator() && event.getFinalDamage() >= player.getHealth()) {
                    BedWarsTeam team = group.getTeamByPlayer(player);
                    //TODO
                    spectatorState.apply(player);
                    Countdown countdown = new Countdown(() -> 10, () -> team.existBed())
                            .addPerSecondTask(second -> Title.as("等待复活", String.format("还有 %d 秒")).send(player));
                    Bukkit.getScheduler().runTaskTimer(BedWars.INSTANCE, () -> countdown.second(), 20, 20);
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

    private void setStartTime() {
        startTime = Countdown.speedUpWhenFull(60, 5, group, group.getTeams().size())
                .setSecondTask(0, () -> {
                    gameState = GameState.RUNNING;
                    startTime.resetCountdown();
                    getGroup().getAlivePlayers().forEach(player -> playerGameState.apply(player));
                })
                .addPerSecondTask(Countdown.setLevel(() -> group.getAlivePlayers()))
                .setSecondTask(Countdown.sendTitle(() -> group.getAlivePlayers(),
                        (integer) -> Title.as("§e游戏即将开始", String.format("还有 %d 秒!", integer))),
                        50, 40, 30, 20, 10, 5, 4, 3, 2, 1);
    }

    public void addTeam(TeamData teamData) {
        group.addTeam(new BedWarsTeam(this, teamData));
        AnnotationConfig.save(teamData, getGameConfig().createSection("team." + teamData.name));
        setStartTime();
    }

    public int getTeamSize() {
        return teamSize;
    }

    @Override
    public GameState preparing(AtomicInteger atomicInteger) {
        if (locationManager.allLocationIsReady() && group.getTeams().size() > 1) {
            return GameState.WAITING;
        }
        if (atomicInteger.get() % (20 * 30) == 0) {
            Bukkit.getLogger().warning(getGameName() + " 配置缺失，无法开始");
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
        player.teleport(locationManager.getLocation("spectatorLocation"));
    }

    @Override
    public void leave(Player player) {
        group.leave(player);
        GamePlayer.getGamePlayer(player).setPlayingGame(null);
    }

    @Override
    public void setGame(Player player, String[] strings) {
        if (strings == null || strings.length == 0) {
            Conversation conversation = Conversation.newConversation(BedWars.INSTANCE);
            if (locationManager.getUnsetLocation().contains("lobby")) {
                conversation.addRequest(setLobby(player));
            }
            if (locationManager.getUnsetLocation().contains("spectatorLocation")) {
                conversation.addRequest(setSpectatorLocation(player));
            }
            if (this.teamSize == 0) {
                conversation.addRequest(setTeamNum(player));
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
        return locationRequest(player, "lobby", "请确认等待大厅位置", "已设置你的位置为等待大厅位置");
    }

    private Request setSpectatorLocation(Player player) {
        return locationRequest(player, "spectatorLocation", "请确认观察者位置", "已设置你的位置为观察者位置");
    }

    private Request locationRequest(Player player, String locationName, String startMessage, String successMessage) {
        return getRequest(RequestConfirm.newRequestConfirm(), startMessage)
                .setOnComplete(request -> {
                    locationManager.putLocation(locationName, player.getLocation());
                    player.sendMessage(successMessage);
                });
    }

    private Request addTeam(Player player) {
        return getRequest(RequestString.newRequestString(), "现在开始添加队伍").setOnComplete(stringRequest -> {
            String s = stringRequest.getResult().get();
            TeamData teamData = new TeamData(s);
            stringRequest.getConversation().insertRequest(
                    setTeamDisplayName(player, teamData)
                    , setTeamRespawnLocation(player, teamData)
                    , setTeamBedLocation(player, teamData)
                    , setTeamColor(player, teamData)
                    , new RequestBase<Object>() {
                        @Override
                        public void start() {
                            if (teamData.getSpawnLocation() != null
                                    && teamData.getDisplayName() != null
                                    && teamData.getBedLocation() != null
                                    && teamData.getColor() != null) {
                                player.sendMessage("队伍添加成功");
                                addTeam(teamData);
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

    private Request setTeamColor(Player player, TeamData teamData) {
        return getRequest(RequestChooseItemStack.newRequestChooseItemStack(18), "请选择队伍的颜色")
                .addItem(getColorLeather(getCanChooseColor()))
                .setOnComplete(request -> {
                    IndexedItemStack indexedItemStack = request.getResult().get();
                    ItemStack itemStack = indexedItemStack.getItemStack();
                    LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) itemStack.getItemMeta();
                    Color color = leatherArmorMeta.getColor();
                    teamData.setColor(color);
                    player.sendMessage("设置成功");
                })
                ;
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

    private Request setTeamDisplayName(Player player, TeamData teamData) {
        return getRequest(RequestString.newRequestString(), "请输入队伍的显示名称")
                .setOnComplete(displayName -> {
                    teamData.setDisplayName(displayName.getResult().get());
                    player.sendMessage("设置成功");
                });
    }

    private Request setTeamNum(Player player) {
        return getRequest(RequestInteger.newRequestInteger(), "请输入队伍的玩家数量")
                .setOnComplete(playerNum -> {
                    teamSize = playerNum.getResult().get();
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

    private static <T extends Request> T getRequest(T request, String startMessage) {
        return (T) request.setTimeout(120)
                .setOnStart(request1 -> ((Request) request1).getConversation().getPlayer().sendMessage(startMessage))
                .setTimeoutMessage("设置时间已过期");
    }

}
