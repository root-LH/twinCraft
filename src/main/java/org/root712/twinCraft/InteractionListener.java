package org.root712.twinCraft;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class InteractionListener implements Listener {
    private final TwinCraft plugin;

    public InteractionListener(TwinCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getPlaybackManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getPlaybackManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (plugin.getPlaybackManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (plugin.getPlaybackManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        if (event.getClickedBlock() != null && plugin.getPlaybackManager().isPlaying(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        DeviceConfig sensor = plugin.getRegistryManager().getDeviceByLocation(clickedBlock.getLocation());
        if (sensor == null
                || !"SENSOR".equalsIgnoreCase(sensor.getType())
                || !"TOGGLE".equalsIgnoreCase(sensor.getAction())) {
            return;
        }

        event.setCancelled(true);
        plugin.getMqttManager().triggerVirtualToggle(sensor);
    }
}
