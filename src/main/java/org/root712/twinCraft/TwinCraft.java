package org.root712.twinCraft;

import org.bukkit.plugin.java.JavaPlugin;

public final class TwinCraft extends JavaPlugin {
    private RegistryManager registryManager;
    private MqttManager mqttManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        if (!new java.io.File(getDataFolder(), "mapping.json").exists()){
            saveResource("mapping.json", false);
        }
        
        // 4주차 목표: 매핑 테이블 관리 시스템 활성화
        this.registryManager = new RegistryManager(this);
        this.registryManager.setProvider(new JsonProvider(getDataFolder()));
        this.registryManager.reload();

        // [5~16주차 구현 로직들의 기초 초기화]
        this.mqttManager = new MqttManager(this);
        this.mqttManager.connect(); // MQTT 연결 시작
        this.mqttManager.startTestScheduler();

        getCommand("tw").setExecutor(new TwinCraftCommand(this));
        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);

        getLogger().info("TwinCraft 매핑 시스템 및 MQTT 연결이 완료되었습니다.");
    }

    @Override
    public void onDisable() {
        if (mqttManager != null) {
            mqttManager.disconnect(); // 연결 해제
        }
        getLogger().info("TwinCraft 플러그인이 비활성화되었습니다.");
    }

    public MqttManager getMqttManager(){
        return mqttManager;
    }

    public RegistryManager getRegistryManager() {
        return registryManager;
    }
}