package org.root712.twinCraft;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager implements MqttCallback {
    private final TwinCraft plugin;
    private MqttClient client;
    private final String topicGlobal = "/twin/#";

    public MqttManager(TwinCraft plugin){
        this.plugin = plugin;
    }

    /**
     * [5~6주차 구현] MQTT 브로커 연결 로직
     */
    public void connect(){
        String broker = plugin.getConfig().getString("mqtt.broker", "");
        String clientId = plugin.getConfig().getString("mqtt.client_id", "TwinCraftPlugin");
        String username = plugin.getConfig().getString("mqtt.username", "");
        String password = plugin.getConfig().getString("mqtt.password", "");

        if (broker.isEmpty()) {
            plugin.getLogger().warning("MQTT 브로커 URL이 설정되지 않았습니다. config.yml을 확인해주세요.");
            return;
        }

        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            if (!username.isEmpty()) options.setUserName(username);
            if (!password.isEmpty()) options.setPassword(password.toCharArray());

            client.setCallback(this);
            client.connect(options);
            
            plugin.getLogger().info("MQTT 브로커에 연결되었습니다: " + broker);
            
            client.subscribe(topicGlobal, 1);

        } catch (MqttException e) {
            plugin.getLogger().severe("MQTT 연결 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 연결 해제 로직
     */
    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                plugin.getLogger().info("MQTT 브로커 연결을 해제했습니다.");
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * [9~10주차 구현 예정] MQTT 메시지 발행
     */
    public void publish(String topic, String message){
        if (client == null || !client.isConnected()) return;
        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1); // 메시지 전달 보장 수준 설정
            client.publish(topic, mqttMessage);
            plugin.getLogger().info("[MQTT 송신] 토픽: " + topic + " | 값: " + message);
        } catch (MqttException e) {
            plugin.getLogger().severe("메시지 송신 실패: " + e.getMessage());
        }
    }
   
    @Override
    public void connectionLost(Throwable throwable) {
        plugin.getLogger().warning("MQTT 연결이 유실되었습니다.");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        
        // [7~8주차 구현 예정] 메시지 수신 시 블록 업데이트 로직
        String value = new String(message.getPayload());
        DeviceConfig config = plugin.getRegistryManager().getDeviceByTopic(topic);
        if (config == null){
            return;
        }
        // 비 SENSOR 타입의 처리 (return)
        if (!"SENSOR".equals(config.getType())){
            return;
        }

        String action = config.getAction();
        switch (action) {
            case "CIRCUIT":
                
                break;
            
            case "BLOCK_CHANGE":

                break;
                
            default:
                break;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        // 메시지 전송 완료 시 처리
    }
}
