package org.root712.twinCraft;

import org.eclipse.paho.client.mqttv3.*;

public class MqttManager implements MqttCallback {
    private final TwinCraft plugin;

    public MqttManager(TwinCraft plugin){
        this.plugin = plugin;
    }

    /**
     * [5~6주차 구현 예정] MQTT 브로커 연결 로직
     */
    public void connect(){
        // TODO: MqttClient 초기화 및 연결 로직 작성
    }

    /**
     * [9~10주차 구현 예정] MQTT 메시지 발행
     */
    public void publish(String topic, String message){
        // TODO: 클라이언트를 통한 메시지 발행 로직 작성
    }

    @Override
    public void connectionLost(Throwable throwable) {
        // 연결 유실 시 처리
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        // [7~8주차 구현 예정] 메시지 수신 시 블록 업데이트 로직
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        // 메시지 전송 완료 시 처리
    }
}