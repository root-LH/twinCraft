package org.root712.twinCraft;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

public class RegistryManager {
    private final TwinCraft plugin;
    private RegistryProvider provider;
    private final Map<String, DeviceConfig> deviceMap = new HashMap<>();
    private final Map<String, DeviceConfig> topicMap = new HashMap<>();
    private final Map<Location, DeviceConfig> locationMap = new HashMap<>();
    private final Map<DeviceConfig, String> deviceNameCache = new HashMap<>();

    public RegistryManager(TwinCraft plugin) {
        this.plugin = plugin;
    }

    public void setProvider(RegistryProvider provider) {
        this.provider = provider;
    }

    /**
     * [3~4주차] 매핑 테이블 로드 및 캐싱
     */
    public void reload() {
        if (provider == null) {
            plugin.getLogger().warning("RegistryProvider가 설정되지 않았습니다!");
            return;
        }

        deviceMap.clear();
        topicMap.clear();
        locationMap.clear();

        Map<String, DeviceConfig> devices = provider.loadAllDevices();
        if (devices != null) {
            deviceMap.putAll(devices);
            rebuildMaps();
            plugin.getLogger().info(devices.size() + "개의 장치를 매핑 테이블에 로드했습니다.");
        }
    }

    private void rebuildMaps() {
        topicMap.clear();
        locationMap.clear();
        deviceNameCache.clear();

        for (Map.Entry<String, DeviceConfig> entry : deviceMap.entrySet()) {

            String name = entry.getKey();
            DeviceConfig device = entry.getValue();

            deviceNameCache.put(device, name);

            if (device.getTopic() != null) {
                topicMap.put(device.getTopic(), device);
            }

            Location loc = device.toBukkitLocation();
            if (loc != null) {
                locationMap.put(loc, device);
            }
        }
    }

    public void save() {
        if (provider != null) {
            provider.saveAllDevices(deviceMap);
        }
    }

    public Map<String, DeviceConfig> getAllDevices() {
        return deviceMap;
    }

    public DeviceConfig getDevice(String name) {
        return deviceMap.get(name);
    }

    public void addDevice(String name, DeviceConfig device) {
        deviceMap.put(name, device);
        rebuildMaps();
        save();
    }

    public void deleteDevice(String name) {
        deviceMap.remove(name);
        rebuildMaps();
        save();
    }

    public void updateDevice() {
        rebuildMaps();
        save();
    }

    public DeviceConfig getDeviceByTopic(String topic) {
        return topicMap.get(topic);
    }

    public DeviceConfig getDeviceByLocation(Location loc) {
        return locationMap.get(loc);
    }

    public String getDeviceName(DeviceConfig device) {
        return deviceNameCache.get(device);
    }
}