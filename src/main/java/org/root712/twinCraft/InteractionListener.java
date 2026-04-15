package org.root712.twinCraft;

import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.Lightable;

public class InteractionListener implements Listener {
    private final TwinCraft plugin;

    public InteractionListener(TwinCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        DeviceConfig device = plugin.getRegistryManager().getDeviceByLocation(event.getClickedBlock().getLocation());

        if (device == null) return;

        BlockData blockData = event.getClickedBlock().getBlockData();

        String status = "";
        if (blockData instanceof Powerable powerable){
            status = !powerable.isPowered() ? "ON" : "OFF";
        } else if (blockData instanceof Lightable lightable) {
            status = !lightable.isLit() ? "ON" : "OFF";
        }

        if (!status.isEmpty() && plugin.getMqttManager() != null && device.getTopic() != null){
            plugin.getMqttManager().publish(device.getTopic(), status);
            event.getPlayer().sendMessage("§b[TwinCraft] §f기기로 신호를 전송했습니다: §e" + status + " §7(" + device.getTopic() + ")");
        }
    }
}
