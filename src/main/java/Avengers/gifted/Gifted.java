package Avengers.gifted;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;



public final class Gifted extends JavaPlugin implements Listener, CommandExecutor {
    private final HashMap<UUID, String> playerAbilittes = new HashMap<>();

    //정멸
    private final HashMap<UUID, Integer> blinkStacks = new HashMap<>();
    private final HashMap<UUID, LinkedList<Location>> locationHistory = new HashMap<>();
    private final int MAX_BLINK_STACKS = 3;

    //토르
    private final HashMap<UUID, Long> thorCooldown = new HashMap<>();
    private final long THOR_COOLDOWN_MS = 10000;

    @Override
    public void onEnable() {
        startBlinkRecharger();
        startLocationTracker();

        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("AB") != null) {
            getCommand("AB").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("게임 안에서만 가용 가능한 명령어입니다.");
            return true;
        }

        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (args.length < 1) {
            player.sendMessage("§c[알림] 사용법: /AB [hermes/thor/blink/phoenix/reset]");
            return true;
        }

        String chosenAbility = args[0];

        if(chosenAbility.equals("reset")) {
            playerAbilittes.remove(uuid);
            player.sendMessage("§e[능력자] 능력이 초기화되었습니다.");
            return true;
        }

        if (chosenAbility.equals("hermes") || chosenAbility.equals("thor") || chosenAbility.equals("blink") || chosenAbility.equals("phoenix")) {

            playerAbilittes.put(uuid, chosenAbility);
            player.sendMessage("§a[능력자] 당신은 이제 §e" + chosenAbility + "§a 입니다!");
        } else {
            player.sendMessage("§c[알림] 알 수 없는 능력입니다. 정확히 입력해 주세요.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerinteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();
        ItemStack item = event.getItem();
        UUID uuid = player.getUniqueId();

        if (!playerAbilittes.containsKey(uuid))
            return;
        String ability = playerAbilittes.get(uuid);

        if (ability.equals("hermes") && item != null && item.getType() == Material.FEATHER) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (player.getAllowFlight())
                    return;

                player.setAllowFlight(true);
                player.setFlying(true);
                player.sendMessage("§b flight on");

                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (player.isOnline()) {
                        player.setFlying(false);
                        player.setAllowFlight(false);
                        player.sendMessage("§c flight off");
                    }
                }, 100L);
            }
        } else if (ability.equals("thor") && item != null && item.getType() == Material.GOLDEN_AXE) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                long currentTime = System.currentTimeMillis();
                if (thorCooldown.containsKey(uuid) && thorCooldown.get(uuid) > currentTime) {
                    long timeleft = (thorCooldown.get(uuid) - currentTime) / 1000;
                    player.sendMessage("§c[토르] 쿨타임 중 (남은 시간: " + timeleft + "초");
                    return;
                }

                Block targetBlock = player.getTargetBlockExact(30);
                if (targetBlock != null) {
                    player.getWorld().strikeLightning(targetBlock.getLocation());
                    thorCooldown.put(uuid, currentTime + THOR_COOLDOWN_MS);
                }
            }
        } else if (ability.equals("blink") && item != null && item.getType() == Material.CLOCK) {
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                int currentStack = blinkStacks.getOrDefault(uuid, MAX_BLINK_STACKS);
                if (currentStack <= 0) {
                    player.sendMessage("§c 스택 부족");
                    return;
                }

                Block targetBlock = player.getTargetBlockExact(5);
                Location targetLocation = (targetBlock != null && targetBlock.getType().isSolid()) ?
                        targetBlock.getLocation().setDirection(player.getLocation().getDirection()) :
                        player.getLocation().add(player.getLocation().getDirection().multiply(5));

                player.teleport(targetLocation);
                blinkStacks.put(uuid,currentStack - 1);
                player.sendMessage("§6[점멸] (" + (currentStack - 1) + "/" + MAX_BLINK_STACKS + ")");

            } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                LinkedList<Location> history = locationHistory.get(uuid);
                if (history == null || history.isEmpty())
                    return;

                player.teleport(history.getFirst());
                history.clear();
            }
        }
    }

    @EventHandler
    public void onEntittyDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!playerAbilittes.containsKey(uuid))
            return;
        String ability = playerAbilittes.get(uuid);

        if (ability.equals("hermes")) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
        }

        if (ability.equals("phoenix")) {
            if (player.getHealth() - event.getFinalDamage() <= 0) {
                event.setCancelled(true);

                double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(maxHealth);

                player.getWorld().strikeLightningEffect(player.getLocation());

                playerAbilittes.remove(uuid);
            }
        }
    }

    private void startLocationTracker() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (!"blink".equals(playerAbilittes))
                    continue;
                locationHistory.putIfAbsent(uuid, new LinkedList<>());
                LinkedList<Location> history = locationHistory.get(uuid);
                history.addLast(player.getLocation().clone());
                if (history.size() > 6) history.removeFirst();
            }
        }, 0L, 10L);
    }

    private void startBlinkRecharger() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if(!"blink".equals(playerAbilittes.get(uuid)))
                    continue;
                int currentStack = blinkStacks.getOrDefault(uuid, MAX_BLINK_STACKS);
                if (currentStack < MAX_BLINK_STACKS) {
                    blinkStacks.put(uuid, currentStack + 1);
                }
            }
        }, 0L, 60L);
    }
}