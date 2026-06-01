package org.root712.twinCraft;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class InteractionListener implements Listener {
    private final TwinCraft plugin;

    public InteractionListener(TwinCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
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
