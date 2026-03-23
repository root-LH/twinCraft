package org.root712.twinCraft;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class RegistryManager {
    private final TwinCraft plugin;
    private RegistryProvider provider;
    private final Map<String, SyncGroup> topicMap = new HashMap<>();
    private final Map<Location, SyncGroup> locationMap = new HashMap<>();

    public RegistryManager(TwinCraft plugin) {
        this.plugin = plugin;
    }

    public void setProvider(RegistryProvider provider){
        this.provider = provider;
    }

    public void reload(){
        if (provider == null){
            plugin.getLogger().warning("RegistryProvider가 설정되지 않았습니다!");
            return;
        }

        topicMap.clear();
        locationMap.clear();

        for (SyncGroup group : provider.loadAllGroups()){
            topicMap.put(group.mqttTopic, group);
            SyncGroup.Component sw = group.components.get("switch");
            if (sw != null){
                locationMap.put(sw.toBukkitLocation(), group);
            }
        }
        plugin.getLogger().info(topicMap.size() + "개의 그룹을 로드했습니다.");
    }

    public SyncGroup getGroupByTopic(String topic){
        return topicMap.get(topic);
    }

    public SyncGroup getGroupByLocation(Location loc){
        return locationMap.get(loc);
    }

}
