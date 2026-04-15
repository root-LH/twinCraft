package org.root712.twinCraft;

import org.bukkit.plugin.java.JavaPlugin;

public final class TwinCraft extends JavaPlugin {
    private RegistryManager registryManager;
    private MqttManager mqttManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // RegistryManager를 임시로 JSON으로 구현. 이후에 사용할 데이터베이스에 따라 수정 예정
        if (!new java.io.File(getDataFolder(), "mapping.json").exists()){
            saveResource("mapping.json", false);
        }
        this.registryManager = new RegistryManager(this);
        this.mqttManager = new MqttManager(this);

        // RegistryManager를 임시로 JSON으로 구현. 이후에 사용할 데이터베이스에 따라 수정 예정
        this.registryManager.setProvider(new JsonProvider(getDataFolder()));

        this.registryManager.reload();

        this.mqttManager.connect();

        getCommand("tw").setExecutor(new TwinCraftCommand(this));

        getServer().getPluginManager().registerEvents(new InteractionListener(this), this);
        getLogger().info("TwinCraft가 성공적으로 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        getLogger().info("TwinCraft 플러그인이 비활성화되었습니다.");
    }

    public MqttManager getMqttManager(){
        return mqttManager;
    }

    public RegistryManager getRegistryManager() {
        return registryManager;
    }
}
