package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager implements MqttCallback {
    private final TwinCraft plugin;
    private MqttClient client;
    // 이후 실제 주소로 변경. but 아마 그대로 사용할 듯
    private final String broker = "tcp://127.0.0.1:1883";

    public MqttManager(TwinCraft plugin){
        this.plugin = plugin;
    }

    public void connect(){
        try{
            client = new MqttClient(
                   broker, "MC_Plugin_" + System.currentTimeMillis(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.setCallback(this);
            client.connect(options);

            client.subscribe("dt/+/control/#");
            plugin.getLogger().info("MQTT 브로커에 연결되었습니다.");
        } catch (MqttException e) {
            plugin.getLogger().severe("MQTT 연결 실패: " + e.getMessage());
        }
    }

    // [발신] 마인크래프트 -> 현실
    public void publish(String topic, String message){
        try{
            if (client != null && client.isConnected()){
                client.publish(topic, new MqttMessage(message.getBytes()));
            }
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        plugin.getLogger().warning("MQTT 연결 끊김!");
    }

    // [수신] 현실 -> 마인크래프트
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());

        SyncGroup group = plugin.getRegistryManager().getGroupByTopic(topic);
        if (group != null){
            Bukkit.getScheduler().runTask(plugin, () -> {
                SyncGroup.Component light = group.components.get("light");
                if (light != null){
                    Material mat = payload.equalsIgnoreCase("ON") ? Material.GLOWSTONE : Material.BLACK_CONCRETE;
                    light.toBukkitLocation().getBlock().setType(mat);
                }
            });
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

    }
}
