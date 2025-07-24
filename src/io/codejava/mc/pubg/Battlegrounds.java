package io.codejava.mc.pubg;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.ChatColor;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

public class Battlegrounds extends JavaPlugin implements Listener {

    private BossBar farmBar;
    private BukkitRunnable farmTimerTask;
    private long farmEndTimeMillis;
    private int farmTimeSeconds;
    private List<Player> activePlayers = new ArrayList<>();
    private Set<Player> frozenPlayers = new HashSet<>();
    private Random random = new Random();
    private boolean gameActive = false;
    private int alivePlayers = 0;
    private BossBar stormBar;
    private BukkitRunnable stormTask;
    private WorldBorder activeBorder;
    private Player winner;

    private boolean keepInventory = true;
    private Set<String> blockedResourcePacks = Set.of(
        "xray", "x-ray", "xray.zip", "x-ray.zip", "Xray_Ultimate"
    );

    @Override
    public void onEnable() {
        // Register the command executor for "startbattle"
        this.getCommand("startbattle").setExecutor(new StartBattleCommand());
        // Register event listeners
        Bukkit.getPluginManager().registerEvents(this, this);
        // Set keepInventory true at startup
        for (World w : Bukkit.getWorlds()) {
            w.setGameRuleValue("keepInventory", "true");
        }
    }

    // Prevent entering Nether/End
    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        if (!world.getEnvironment().equals(World.Environment.NORMAL)) {
            event.getPlayer().teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            event.getPlayer().sendMessage(ChatColor.RED + "네더와 엔드로 이동할 수 없습니다.");
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        World.Environment env = event.getTo().getWorld().getEnvironment();
        if (!env.equals(World.Environment.NORMAL)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "네더와 엔드로 이동할 수 없습니다.");
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        World.Environment env = event.getTo().getWorld().getEnvironment();
        if (!env.equals(World.Environment.NORMAL)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "네더와 엔드로 이동할 수 없습니다.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (farmBar != null) farmBar.addPlayer(event.getPlayer());
    }

    // Allow respawn during farming time
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (farmBar != null) farmBar.addPlayer(event.getPlayer());
        if (keepInventory) {
            event.getPlayer().setGameMode(GameMode.SURVIVAL);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setTo(from);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (gameActive) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "게임 중에는 명령어를 사용할 수 없습니다."); // Also send chat messages visible to everyone
            // saying: "<commandattempteduser>이(가) 명령어 사용을 시도하였습니다." and this too?
            Bukkit.broadcastMessage(ChatColor.RED + event.getPlayer().getName() + "이(가) 명령어 사용을 시도하였습니다.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (gameActive && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "게임 중에는 게임모드를 변경할 수 없습니다."); // Also send chat messages visible to everyone
            // saying: "<gamemodechangeattempteduser>이(가) 게임모드 변경을 시도하였습니다." can add this?
            Bukkit.broadcastMessage(ChatColor.RED + event.getPlayer().getName() + "이(가) 게임모드 변경을 시도하였습니다.");
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameActive && keepInventory) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            return;
        }
        if (!gameActive) return;
        event.setKeepInventory(false);
        event.setKeepLevel(false);
        Player dead = event.getEntity();
        Player killer = dead.getKiller();
        alivePlayers--;
        // Set dead player to spectator
        Bukkit.getScheduler().runTaskLater(this, () -> {
            dead.setGameMode(GameMode.SPECTATOR);
        }, 1L);

        String msg = ChatColor.YELLOW + "[+] ";
        if (killer != null) {
            msg += ChatColor.YELLOW + killer.getName() + "이(가) " + dead.getName() + "을(를) 죽였습니다. ";
        } else {
            msg += ChatColor.YELLOW + dead.getName() + "이(가) 사망했습니다. ";
        }
        msg += "남은 생존자 수: " + alivePlayers;
        Bukkit.broadcastMessage(msg);

        updateAliveHotbar();

        // Check for winner
        if (alivePlayers == 1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR) {
                    winner = p;
                    freezeWinner(winner);
                    break;
                }
            }
            showWinner();
        }
    }

    private void updateAliveHotbar() {
        String hotbarMsg = ChatColor.YELLOW + "" + ChatColor.BOLD + "> 남은 생존자 수: " + alivePlayers + "명 <";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) {
                p.sendActionBar(hotbarMsg);
            }
        }
    }

    private void freezeWinner(Player p) {
        frozenPlayers.add(p);
        p.setWalkSpeed(0f);
        p.setFlySpeed(0f);
    }

    private void showWinner() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("", ChatColor.GOLD + "" + ChatColor.BOLD + "WINNER WINNER CHICKEN DINNER", 0, 60, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f); // successful hit
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (winner != null) {
                    p.sendTitle("", ChatColor.YELLOW + "" + ChatColor.BOLD + winner.getName() + "!", 0, 60, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f); // XP level up
                }
            }
            // Send game result chat message
            if (winner != null) {
                String resultMsg = ChatColor.GREEN + "" + ChatColor.BOLD + "< 게임 결과 >\n\n"
                        + ChatColor.YELLOW + "" + ChatColor.BOLD + winner.getName() + " 승리!";
                Bukkit.broadcastMessage(resultMsg);
            }
            // End game: remove borders, boss bars, clear action bars
            gameActive = false;
            if (stormBar != null) stormBar.removeAll();
            if (stormTask != null) stormTask.cancel();
            if (activeBorder != null) activeBorder.setSize(2000); // Reset border
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendActionBar(""); // Clear action bar
            }
            // Restore keepInventory for farming
            for (World w : Bukkit.getWorlds()) {
                w.setGameRuleValue("keepInventory", "true");
            }
            keepInventory = true;
        }, 60L); // 3 seconds after subtitle
    }

    // Command executor class for "startbattle"
    private class StartBattleCommand implements CommandExecutor {
        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            // Only allow players to start
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "플레이어만 명령어를 사용할 수 있습니다.");
                return true;
            }
            // Parse farm time (minutes)
            int farmMinutes = 10;
            if (args.length > 0) {
                try {
                    farmMinutes = Math.max(1, Integer.parseInt(args[0]));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "파밍 시간은 숫자로 입력하세요."); // this farming time is in minutes right?
                    return true;
                }
            }
            farmTimeSeconds = farmMinutes * 60;
            farmEndTimeMillis = System.currentTimeMillis() + farmTimeSeconds * 1000L;

            // Set world border
            World world = ((Player)sender).getWorld();
            WorldBorder border = world.getWorldBorder();
            border.setCenter(0, 0);
            border.setSize(2000);

            // Setup boss bar
            if (farmBar != null) farmBar.removeAll();
            farmBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
            for (Player p : world.getPlayers()) farmBar.addPlayer(p);

            // Clear inventories and teleport all players to (0, 100, 0)
            for (Player p : world.getPlayers()) {
                p.getInventory().clear();
                p.teleport(new Location(world, 0, 100, 0));
                p.setGameMode(GameMode.SURVIVAL);
                p.sendTitle(ChatColor.BOLD + "" + ChatColor.WHITE + "파밍 시간이 시작되었습니다: " + farmMinutes + "분", "", 10, 60, 10);
            }

            // Start timer
            if (farmTimerTask != null) farmTimerTask.cancel();
            farmTimerTask = new BukkitRunnable() {
                boolean farmingEnded = false;
                boolean gameCountdownStarted = false;
                int countdown = 15;
                BukkitRunnable countdownTask = null;

                @Override
                public void run() {
                    long now = System.currentTimeMillis();
                    long leftMillis = farmEndTimeMillis - now;
                    if (!farmingEnded && leftMillis <= 0) {
                        farmingEnded = true;
                        farmBar.setTitle(ChatColor.BOLD + "" + ChatColor.WHITE + "파밍 시간이 종료되었습니다!");
                        farmBar.setProgress(0);
                        for (Player p : world.getPlayers()) {
                            p.sendTitle(ChatColor.BOLD + "" + ChatColor.WHITE + "파밍 시간이 종료되었습니다!", "", 10, 100, 10);
                        }
                        // After 5 seconds, start game countdown
                        Bukkit.getScheduler().runTaskLater(Battlegrounds.this, () -> {
                            farmBar.removeAll();
                            startGameCountdown(world);
                        }, 100L); // 5 seconds
                        this.cancel();
                        return;
                    }
                    if (!farmingEnded) {
                        double leftSec = leftMillis / 1000.0;
                        int minutes = (int)(leftSec / 60);
                        double seconds = leftSec % 60;
                        String title;
                        if (minutes >= 1) {
                            title = ChatColor.BOLD + "" + ChatColor.WHITE + "남은 파밍 시간: "
                                    + ChatColor.BOLD + "" + ChatColor.GREEN + minutes + "분 "
                                    + ChatColor.BOLD + "" + ChatColor.WHITE
                                    + ChatColor.BOLD + "" + ChatColor.GREEN + (int)seconds + "초";
                        } else {
                            title = ChatColor.BOLD + "" + ChatColor.WHITE + "남은 파밍 시간: "
                                    + ChatColor.BOLD + "" + ChatColor.GREEN + String.format("%.1f", seconds) + "초";
                        }
                        farmBar.setTitle(title);
                        farmBar.setProgress(leftSec / (farmTimeSeconds));
                    }
                }

                private void startGameCountdown(World world) {
                    for (Player p : world.getPlayers()) {
                        // Teleport to random position above ground
                        double x = random.nextInt(2000) - 1000;
                        double z = random.nextInt(2000) - 1000;
                        double y = 100;
                        p.teleport(new Location(world, x, y, z));
                        // Give slow falling effect for 30 seconds
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 30, 1, false, false, false));
                        // Freeze player
                        frozenPlayers.add(p);
                        p.setWalkSpeed(0f);
                        p.setFlySpeed(0f);
                        p.setGameMode(GameMode.SURVIVAL);
                        p.sendTitle(ChatColor.BOLD + "" + ChatColor.WHITE + "게임이 잠시 후 시작됩니다", ChatColor.BOLD + "" + ChatColor.WHITE + "시작까지 15초", 10, 60, 10);
                    }
                    countdownTask = new BukkitRunnable() {
                        int count = 15;
                        @Override
                        public void run() {
                            for (Player p : world.getPlayers()) {
                                if (count <= 3) {
                                    String title = "";
                                    ChatColor color = ChatColor.GREEN;
                                    Sound sound = Sound.BLOCK_NOTE_BLOCK_BIT;
                                    float pitch = 1.0f;
                                    if (count == 3) {
                                        color = ChatColor.RED;
                                    } else if (count == 2) {
                                        color = ChatColor.GOLD;
                                    }
                                    title = color + "" + ChatColor.BOLD + count;
                                    p.sendTitle(title, "", 0, 20, 0);
                                    p.playSound(p.getLocation(), sound, 1.0f, pitch);
                                } else if (count == 1) {
                                    p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "1", "", 0, 20, 0);
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 1.0f);
                                }
                            }
                            if (count == 1) {
                                // Unfreeze players
                                for (Player p : world.getPlayers()) {
                                    frozenPlayers.remove(p);
                                    p.setWalkSpeed(0.2f);
                                    p.setFlySpeed(0.1f);
                                }
                                // Show "- 시작! -" and play highest pitch bit note block
                                for (Player p : world.getPlayers()) {
                                    p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "- 시작! -", "", 0, 40, 10);
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, 2.0f);
                                }
                                // Start in-game logic
                                startStorm(world);
                                this.cancel();
                            }
                            count--;
                        }
                    };
                    countdownTask.runTaskTimer(Battlegrounds.this, 0L, 20L); // every second
                }

                private void startStorm(World world) {
                    gameActive = true;
                    alivePlayers = 0;
                    for (Player p : world.getPlayers()) {
                        if (p.getGameMode() != GameMode.SPECTATOR) alivePlayers++;
                    }
                    updateAliveHotbar();

                    // Disable keepInventory for battle
                    for (World w : Bukkit.getWorlds()) {
                        w.setGameRuleValue("keepInventory", "false");
                    }
                    keepInventory = false;

                    // Setup storm boss bar
                    if (stormBar != null) stormBar.removeAll();
                    stormBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
                    for (Player p : world.getPlayers()) {
                        if (p.getGameMode() != GameMode.SPECTATOR) stormBar.addPlayer(p);
                    }

                    // Shrink world border smoothly over 50 seconds to 5x5
                    activeBorder = world.getWorldBorder();
                    double initialSize = 2000;
                    double finalSize = 5;
                    int shrinkSeconds = 50;
                    long shrinkStart = System.currentTimeMillis();
                    long shrinkEnd = shrinkStart + shrinkSeconds * 1000L;

                    if (stormTask != null) stormTask.cancel();
                    stormTask = new BukkitRunnable() {
                        @Override
                        public void run() {
                            long now = System.currentTimeMillis();
                            long leftMillis = shrinkEnd - now;
                            if (leftMillis <= 0) {
                                activeBorder.setSize(finalSize);
                                stormBar.setTitle(ChatColor.RED + "" + ChatColor.BOLD + "[!] 자기장 축소까지 0초 [!]");
                                stormBar.setProgress(0);
                                this.cancel();
                                return;
                            }
                            double leftSec = leftMillis / 1000.0;
                            int seconds = (int)leftSec;
                            stormBar.setTitle(ChatColor.RED + "" + ChatColor.BOLD + "[!] 자기장 축소 중 [!]");
                            stormBar.setProgress(leftSec / shrinkSeconds);
                            // Shrink border smoothly
                            double newSize = initialSize - ((initialSize - finalSize) * (1 - leftSec / shrinkSeconds));
                            activeBorder.setSize(newSize);
                        }
                    };
                    stormTask.runTaskTimer(Battlegrounds.this, 0L, 2L); // update every 2 ticks
                }
            };
            farmTimerTask.runTaskTimer(Battlegrounds.this, 0L, 2L); // update every 2 ticks
            return true;
        }
    }
}