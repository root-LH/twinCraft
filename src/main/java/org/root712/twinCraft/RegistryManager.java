package org.root712.twinCraft;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class RegistryManager {
    private final TwinCraft plugin;
    private RegistryProvider provider;
    private final Map<String, SyncGroup> topicMap = new HashMap<>();
    private final Map<Location, ComponentContext> locationMap = new HashMap<>();

    public static class ComponentContext {
        public final SyncGroup group;
        public final SyncGroup.Component component;

        public ComponentContext(SyncGroup group, SyncGroup.Component component) {
            this.group = group;
            this.component = component;
        }
    }

    public RegistryManager(TwinCraft plugin) {
        this.plugin = plugin;
    }

    public void setProvider(RegistryProvider provider) {
        this.provider = provider;
    }

    public void reload() {
        if (provider == null) {
            plugin.getLogger().warning("RegistryProvider가 설정되지 않았습니다!");
            return;
        }

        topicMap.clear();
        locationMap.clear();

        for (SyncGroup group : provider.loadAllGroups()) {
            topicMap.put(group.mqttTopic, group);
            for (SyncGroup.Component comp : group.components) {
                Location loc = comp.toBukkitLocation();
                if (loc != null) {
                    locationMap.put(loc, new ComponentContext(group, comp));
                }
            }
        }
        plugin.getLogger().info(topicMap.size() + "개의 그룹을 로드했습니다.");
    }

    public SyncGroup getGroupByTopic(String topic) {
        return topicMap.get(topic);
    }

    public ComponentContext getContextByLocation(Location loc) {
        return locationMap.get(loc);
    }

}
