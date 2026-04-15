package org.root712.twinCraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class JsonProvider implements RegistryProvider {
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JsonProvider(File dataFolder) {
        this.file = new File(dataFolder, "mapping.json");
    }

    @Override
    public Map<String, DeviceConfig> loadAllDevices() {
        try (FileReader reader = new FileReader(file)) {
            Map<String, DeviceConfig> result = gson.fromJson(reader,
                    new TypeToken<Map<String, DeviceConfig>>() {}.getType());
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @Override
    public void saveAllDevices(Map<String, DeviceConfig> devices) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(devices, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
