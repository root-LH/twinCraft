package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.TextDisplay;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager implements MqttCallback {
    private final TwinCraft plugin;
    private MqttClient client;
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
            client.subscribe("sensor/#"); // 예시로 추가
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
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        plugin.getLogger().warning("MQTT 연결 끊김!");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        DeviceConfig device = plugin.getRegistryManager().getDeviceByTopic(topic);
        
        if (device != null){
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location loc = device.toBukkitLocation();
                if (loc == null) return;

                String action = device.getAction();
                boolean isOn = payload.equalsIgnoreCase("ON") || payload.equalsIgnoreCase("TRUE");

                if ("CIRCUIT".equalsIgnoreCase(action)) {
                    Block block = loc.getBlock();
                    BlockData data = block.getBlockData();
                    if (data instanceof Lightable lightable) {
                        lightable.setLit(isOn);
                        block.setBlockData(lightable);
                    } else if (data instanceof Powerable powerable) {
                        powerable.setPowered(isOn);
                        block.setBlockData(powerable);
                    }
                } else if ("BLOCK_CHANGE".equalsIgnoreCase(action)) {
                    String matName = isOn ? device.getActionData().get("on_material") : device.getActionData().get("off_material");
                    if (matName != null) {
                        Material mat = Material.matchMaterial(matName);
                        if (mat != null) {
                            loc.getBlock().setType(mat);
                        }
                    }
                } else if ("TEXT_DISPLAY".equalsIgnoreCase(action)) {
                    loc.getWorld().getNearbyEntities(loc, 2, 2, 2).stream()
                            .filter(e -> e instanceof TextDisplay)
                            .map(e -> (TextDisplay) e)
                            .forEach(td -> td.setText(payload));
                }
            });
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {}
}
