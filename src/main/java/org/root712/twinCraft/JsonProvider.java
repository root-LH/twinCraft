package org.root712.twinCraft;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JsonProvider implements RegistryProvider{
    private final File file;

    public JsonProvider(File dataFolder){
        this.file = new File(dataFolder, "mapping.json");
    }

    @Override
    public List<SyncGroup> loadAllGroups() {
        try (FileReader reader = new FileReader(file)){
            Map<String, List<SyncGroup>> data = new Gson().fromJson(reader,
                    new TypeToken<Map<String, List<SyncGroup>>>(){}.getType());
            return data.get("sync_groups");
        } catch (Exception e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
