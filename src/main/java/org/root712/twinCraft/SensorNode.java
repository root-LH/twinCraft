package org.root712.twinCraft;

import org.bukkit.Location;

public class SensorNode {
    String id, type;
    Location loc;
    public SensorNode(String id, String type, Location loc) {
        this.id = id;
        this.type = type;
        this.loc = loc;
    }

    public String getNodeId() { return id; }
    public String getType() { return type; }
    public Location getLocation() { return loc; }
}
