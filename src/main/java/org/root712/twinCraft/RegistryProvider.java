package org.root712.twinCraft;

import java.util.List;
import java.util.Map;

public interface RegistryProvider {
    Map<String, DeviceConfig> loadAllDevices();
    void saveAllDevices(Map<String, DeviceConfig> devices);
}
