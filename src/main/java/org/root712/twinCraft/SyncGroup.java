package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class SyncGroup {
    String groupId;
    String mqttTopic;
    List<Component> components = new ArrayList<>();

    public static class Component {
        String id;       // JSON 메시지 내의 키값 (예: "temp", "relay1")
        String type;     // 부품 타입 (예: "SENSOR", "ACTUATOR")
        String material; // 마인크래프트 블록 종류
        String world;
        double x, y, z;

        public Location toBukkitLocation() {
            if (world == null || Bukkit.getWorld(world) == null) return null;
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
    }
}
