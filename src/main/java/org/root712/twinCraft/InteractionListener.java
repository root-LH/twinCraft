package org.root712.twinCraft;

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

        // 나중에 테스트해야 함 (값이 null이 아닌지, 정수값 리턴인지)
        RegistryManager.ComponentContext context = plugin.getRegistryManager().getContextByLocation(event.getClickedBlock().getLocation());

        if (context == null) return;

        BlockData blockData = event.getClickedBlock().getBlockData();

        if (blockData instanceof Powerable powerable){

            String status = !powerable.isPowered() ? "ON" : "OFF";

            if (plugin.getMqttManager() != null){
                // context.group.mqttTopic을 사용하여 해당 기기의 토픽으로 전송
                plugin.getMqttManager().publish(context.group.mqttTopic, status);

                event.getPlayer().sendMessage("§b[TwinCraft] §f[" + context.component.id + "] 기기로 신호를 전송했습니다: §e" + status);
            }
        }
    }
}
