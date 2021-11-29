package me.gushel.delayedblocks;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class DelayedBlocks extends JavaPlugin implements Listener {

    HashMap<Location, BukkitTask> animationMap = new HashMap<>();
    HashMap<Location, Integer> entityIDMap = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(this, (Plugin)this);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public void createBlockBreakAnimation(Player player,Block block,int stage, int random){
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        PacketContainer blockBreakAnimPacket = protocolManager.createPacket(PacketType.Play.Server.BLOCK_BREAK_ANIMATION);
        blockBreakAnimPacket.getBlockPositionModifier().write(0, new BlockPosition(block.getX(), block.getY(), block.getZ()));
        blockBreakAnimPacket.getIntegers().write(0, random);
        blockBreakAnimPacket.getIntegers().write(1, stage);
        try {
            protocolManager.sendServerPacket(player, blockBreakAnimPacket);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("delayedblocks") && sender.hasPermission("delayedblocks.admin")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "DelayedBlocks has been reloaded successfully!");
            return true;
        }
        if (label.equalsIgnoreCase("delayedblocksdebug") && sender.hasPermission("delayedblocks.admin")) {
            System.out.println("Block Break Animation Map: "+animationMap.toString());
            System.out.println("Block Break Animation EntityID Map: "+entityIDMap.toString());
            return true;
        }
        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (getConfig().getBoolean("use-permission-to-bypass") && e.getPlayer().hasPermission("delayedblocks.bypass"))
            return;
        Block block = e.getBlock();
        List<Integer> list = getConfig().getIntegerList("block-list");
        if (list.contains(e.getBlock().getTypeId())) {
            int random = new Random().nextInt(2000);
            BukkitTask anim = new BukkitRunnable() {
                int stageCount = 0;

                public void run() {
                    if (stageCount == 0 && getConfig().getBoolean("debug"))
                        System.out.println("Created task " + getTaskId() + " because a block has been placed!");
                    if (stageCount > 9) {
                        if (getConfig().getBoolean("effects")) {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                createBlockBreakAnimation(player, block, stageCount, random);
                            }
                        }
                        if (getConfig().getBoolean("drop-block")) block.breakNaturally();
                        else block.breakNaturally(new ItemStack(Material.AIR));
                        if (getConfig().getBoolean("debug"))
                            System.out.println("Cancelled task " + getTaskId() + " because the animation has ended!");
                        animationMap.remove(e.getBlock().getLocation());
                        entityIDMap.remove(e.getBlock().getLocation());
                        cancel();
                    }
                    if (getConfig().getBoolean("effects")) {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            createBlockBreakAnimation(player, block, stageCount, random);
                        }
                    }
                    stageCount++;
                }
            }.runTaskTimer(this, 0L, (getConfig().getInt("remove-after") * 20L) / 9);
            animationMap.put(block.getLocation(), anim);
            entityIDMap.put(block.getLocation(), random);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e){
        if (animationMap.containsKey(e.getBlock().getLocation())){
            if (getConfig().getBoolean("debug")) System.out.println("Cancelled task "+animationMap.get(e.getBlock().getLocation()).getTaskId()+" because the block was broken!");
            animationMap.get(e.getBlock().getLocation()).cancel();
            if (getConfig().getBoolean("effects")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    createBlockBreakAnimation(player, e.getBlock(), 10, entityIDMap.get(e.getBlock().getLocation()));
                }
            }
            animationMap.remove(e.getBlock().getLocation());
            entityIDMap.remove(e.getBlock().getLocation());
        }
    }

}
