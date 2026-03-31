package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager implements MqttCallback {
    private final TwinCraft plugin;
    private MqttClient client;
    // 이후에 팀원 C의 DB 주소로 업데이트
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

            // 이 부분 이후에 변경해야함
            client.subscribe("dt/+/control/#");
            plugin.getLogger().info("MQTT 브로커에 연결되었습니다.");
        } catch (MqttException e) {
            plugin.getLogger().severe("MQTT 연결 실패: " + e.getMessage());
        }
    }

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

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());

        SyncGroup group = plugin.getRegistryManager().getGroupByTopic(topic);
        if (group != null){
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 리스트에서 id가 "light"인 컴포넌트 찾기
                SyncGroup.Component lightComp = group.components.stream()
                        .filter(c -> "light".equals(c.id))
                        .findFirst()
                        .orElse(null);

                if (lightComp != null && lightComp.toBukkitLocation() != null){
                    Block block = lightComp.toBukkitLocation().getBlock();
                    BlockData data = block.getBlockData();
                    boolean isOn = payload.equalsIgnoreCase("ON");

                    // Material 대신 BlockData의 상태(Lightable, Powerable 등)를 변경하는 방식 사용
                    if (data instanceof Lightable lightable) {
                        lightable.setLit(isOn);
                        block.setBlockData(lightable);
                    } else if (data instanceof Powerable powerable) {
                        powerable.setPowered(isOn);
                        block.setBlockData(powerable);
                    }
                }
            });
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        int postId = iMqttDeliveryToken.getMessageId();
        plugin.getLogger().info("Publish 완료, id: " + postId);
    }
}
