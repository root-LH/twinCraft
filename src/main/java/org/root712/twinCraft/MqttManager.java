package org.root712.twinCraft;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttManager implements MqttCallback {
    private boolean toggleState = false; // TODO: 토글 상태 저장 변수(테스트 이후 삭제)
    private final TwinCraft plugin;
    private MqttClient client;

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
            // TODO: 테스트용 토픽 구독 시작 (7주차 이후 제거)
            String testTopic = "";
            client.subscribe(testTopic, 1); // QoS 1로 구독
            plugin.getLogger().info("테스트 토픽 구독 시작: " + testTopic);
            // END
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
        // TODO: 클라이언트를 통한 메시지 발행 로직 작성
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

    // TODO: 데이터 송신용 (테스트 후 삭제)
    public void startTestScheduler() {
        // 마인크래프트 서버의 스케줄러 활용 (20틱 = 1초)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (client != null && client.isConnected()) {
                // 상태값 토글 (true -> "1", false -> "0")
                toggleState = !toggleState;
                String value = toggleState ? "1" : "0";
                
                // 테스트용 타겟 토픽으로 전송
                String targetTopic = "";
                publish(targetTopic, value);
            }
        }, 100L, 100L); // 5초(100틱) 후 시작, 5초마다 반복
    }

    @Override
    public void connectionLost(Throwable throwable) {
        plugin.getLogger().warning("MQTT 연결이 유실되었습니다.");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // TODO: 구독한 토픽에 대한 메시지 수신 테스트 (이후 삭제 예정)
        plugin.getLogger().info("[MQTT 수신] 토픽: " + topic + " | 값: " + message);
        // END
        
        // [7~8주차 구현 예정] 메시지 수신 시 블록 업데이트 로직
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        // 메시지 전송 완료 시 처리
    }
}
