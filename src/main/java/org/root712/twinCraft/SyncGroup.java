package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class SyncGroup {
    String groupId;
    String mqttTopic;
    Map<String, Component> components = new HashMap<>();

    public static class Component{
        String material;
        String world;
        double x, y, z;

        public Location toBukkitLocation(){
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
    }
}
