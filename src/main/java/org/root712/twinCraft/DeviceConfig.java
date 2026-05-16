package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.util.Map;

public class DeviceConfig {
    String topic;
    String type; // SENSOR, ACTUATOR
    String world;
    double x, y, z;
    String action; // CIRCUIT, BLOCK_CHANGE, TEXT_DISPLAY
    Map<String, String> actionData;

    // 추가: 0.1초(100ms) 등의 주기적 데이터 획득 혹은 상호작용(EVENT) 구분
    int updateInterval; // 0이면 이벤트 기반, 그 외는 ms 단위 주기
    String triggerType; // POLLING, EVENT

    public transient String currentState = "0";

    public Location toBukkitLocation() {
        if (world == null || Bukkit.getWorld(world) == null) return null;
        return new Location(Bukkit.getWorld(world), x, y, z);
    }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Map<String, String> getActionData() { return actionData; }
    public void setActionData(Map<String, String> actionData) { this.actionData = actionData; }

    public int getUpdateInterval() { return updateInterval; }
    public void setUpdateInterval(int updateInterval) { this.updateInterval = updateInterval; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public String getCurrentState(){ return currentState == null ? "0" : currentState; }
    public String setCurrentState(String state){ this.currentState = state; }
}
