package org.root712.twinCraft;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.data.Powerable;

public class InteractionListener implements Listener {
    private final TwinCraft plugin;

    public InteractionListener(TwinCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        SyncGroup group = plugin.getRegistryManager().getGroupByLocation(event.getClickedBlock().getLocation());

        if (group == null) return;

        BlockData blockData = event.getClickedBlock().getBlockData();

        if (blockData instanceof Powerable powerable){

            String status = !powerable.isPowered() ? "ON" : "OFF";

            if (plugin.getMqttManager() != null){
                plugin.getMqttManager().publish(group.mqttTopic, status);

                event.getPlayer().sendMessage("§b[TwinCraft] §f현실 기기로 신호를 전송했습니다: §e" + status);
            }
        }
    }
}
