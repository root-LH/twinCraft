package org.root712.twinCraft;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public class InteractionListener implements Listener {
    private final TwinCraft plugin;

    public InteractionListener(TwinCraft plugin) {
        this.plugin = plugin;
    }

    /**
     * [9~10주차 구현 예정] 플레이어가 블록(레버, 버튼) 우클릭 시 MQTT 전송 로직
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        // TODO: 클릭한 블록 좌표 확인 및 매핑 데이터 대조 로직 작성
    }
}