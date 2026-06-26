package org.root712.twinCraft;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaybackManager {
    private static final String DEFAULT_PLAYBACK_URL = "";
    private static final int DEFAULT_LIMIT = 5000;
    private static final long UNTITLED_EVENT_INTERVAL_TICKS = 20L;
    private static final DateTimeFormatter PLAYBACK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final TwinCraft plugin;
    private final ProtocolManager protocolManager;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<UUID, PlaybackSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> playbackPacketBypass = ConcurrentHashMap.newKeySet();

    public PlaybackManager(TwinCraft plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        registerRealtimePacketFilter();
    }

    public void start(Player player, String start, String end, int limit) {
        int requestLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, DEFAULT_LIMIT);
        List<DeviceConfig> sensors = getPlaybackSensors();
        if (sensors.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Playback sensor nodes are empty.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Playback loading... sensors=" + sensors.size());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<PlaybackEvent> events = fetchAllEvents(start, end, requestLimit, sensors);
                Bukkit.getScheduler().runTask(plugin, () -> startSession(player, events));
            } catch (Exception e) {
                plugin.getLogger().warning("Playback request failed: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(ChatColor.RED + "Playback request failed: " + e.getMessage()));
            }
        });
    }

    public void stop(Player player) {
        PlaybackSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.stop(true);
            player.sendMessage(ChatColor.YELLOW + "Playback stopped.");
        }
    }

    public void stopAll() {
        for (PlaybackSession session : sessions.values()) {
            session.stop(true);
        }
        sessions.clear();
    }

    private void registerRealtimePacketFilter() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                ListenerPriority.HIGHEST,
                PacketType.Play.Server.BLOCK_CHANGE,
                PacketType.Play.Server.MULTI_BLOCK_CHANGE,
                PacketType.Play.Server.UPDATE_TIME
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                UUID playerId = event.getPlayer().getUniqueId();
                if (!sessions.containsKey(playerId)) {
                    return;
                }

                if (playbackPacketBypass.remove(playerId)) {
                    return;
                }

                event.setCancelled(true);
            }
        });
    }

    private void sendPlaybackPacket(Player player, PacketContainer packet) throws Exception {
        playbackPacketBypass.add(player.getUniqueId());
        try {
            protocolManager.sendServerPacket(player, packet);
        } finally {
            playbackPacketBypass.remove(player.getUniqueId());
        }
    }

    private List<DeviceConfig> getPlaybackSensors() {
        List<DeviceConfig> sensors = new ArrayList<>();
        for (DeviceConfig device : plugin.getRegistryManager().getAllDevices().values()) {
            if (device != null
                    && "SENSOR".equalsIgnoreCase(device.getType())
                    && device.getTopic() != null
                    && !device.getTopic().isEmpty()) {
                sensors.add(device);
            }
        }
        return sensors;
    }

    private List<PlaybackEvent> fetchAllEvents(String start, String end, int limit, List<DeviceConfig> sensors) throws IOException, InterruptedException {
        List<PlaybackEvent> events = new ArrayList<>();
        for (DeviceConfig sensor : sensors) {
            events.addAll(fetchEvents(start, end, sensor.getTopic(), limit));
        }

        boolean hasTimestamps = events.stream().anyMatch(event -> event.timestampMillis != null);
        if (hasTimestamps) {
            events.sort(Comparator.comparingLong(event -> event.timestampMillis == null ? Long.MAX_VALUE : event.timestampMillis));
        }
        return events;
    }

    private List<PlaybackEvent> fetchEvents(String start, String end, String topic, int limit) throws IOException, InterruptedException {
        String url = getPlaybackUrl()
                + "?start=" + encode(start)
                + "&end=" + encode(end)
                + "&topic=" + encode(topic)
                + "&limit=" + limit
                + "&include_debug=false";

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " for topic " + topic);
        }

        return parseEvents(response.body(), topic);
    }

    private String getPlaybackUrl() {
        return plugin.getConfig().getString("playback.url", DEFAULT_PLAYBACK_URL);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<PlaybackEvent> parseEvents(String body, String fallbackTopic) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray array = findEventArray(root);
        List<PlaybackEvent> events = new ArrayList<>();

        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String topic = firstString(object, "topic", "mqtt_topic", "device_topic");
            String value = firstString(object, "value", "payload", "state", "message");
            Long timestamp = firstTimestamp(object, "timestamp", "time", "created_at", "createdAt", "ts");

            if (topic == null) {
                topic = fallbackTopic;
            }
            if (topic == null || value == null) {
                continue;
            }

            events.add(new PlaybackEvent(topic, value, timestamp));
        }
        return events;
    }

    private JsonArray findEventArray(JsonElement root) {
        if (root == null || root.isJsonNull()) {
            return new JsonArray();
        }
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (!root.isJsonObject()) {
            return new JsonArray();
        }

        JsonObject object = root.getAsJsonObject();
        for (String key : List.of("data", "records", "items", "events", "result", "results")) {
            JsonElement value = object.get(key);
            if (value != null && value.isJsonArray()) {
                return value.getAsJsonArray();
            }
        }
        return new JsonArray();
    }

    private String firstString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value != null && !value.isJsonNull()) {
                return value.getAsString();
            }
        }
        return null;
    }

    private Long firstTimestamp(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || value.isJsonNull()) {
                continue;
            }

            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                long raw = value.getAsLong();
                return raw < 10_000_000_000L ? raw * 1000L : raw;
            }

            try {
                return Instant.parse(value.getAsString()).toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private void startSession(Player player, List<PlaybackEvent> events) {
        stop(player);
        if (events.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Playback data is empty.");
            return;
        }

        PlaybackSession session = new PlaybackSession(player, events);
        sessions.put(player.getUniqueId(), session);
        session.start();
        player.sendMessage(ChatColor.GREEN + "Playback started. events=" + events.size());
    }

    private String getDeviceName(DeviceConfig device) {
        for (Map.Entry<String, DeviceConfig> entry : plugin.getRegistryManager().getAllDevices().entrySet()) {
            if (entry.getValue() == device) {
                return entry.getKey();
            }
        }
        return null;
    }

    private final class PlaybackSession {
        private final Player player;
        private final List<PlaybackEvent> events;
        private final Map<String, String> sensorStates = new HashMap<>();
        private final Map<String, TextDisplay> textDisplays = new HashMap<>();
        private final Map<String, BukkitTask> particleTasks = new HashMap<>();
        private final Map<Location, BlockData> fakeBlocks = new HashMap<>();
        private final long firstTimestamp;
        private final boolean timed;

        private BukkitTask task;
        private int index = 0;
        private long elapsedTicks = 0L;
        private Long lastTime = null;
        private Long lastPlaybackTimestamp = null;

        PlaybackSession(Player player, List<PlaybackEvent> events) {
            this.player = player;
            this.events = events;
            this.timed = events.stream().allMatch(event -> event.timestampMillis != null);
            this.firstTimestamp = timed ? events.get(0).timestampMillis : 0L;
        }

        void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }

        void tick() {
            if (!player.isOnline()) {
                stop(true);
                sessions.remove(player.getUniqueId());
                return;
            }

            while (index < events.size() && getDelayTicks(index) <= elapsedTicks) {
                apply(events.get(index));
                index++;
            }

            if (elapsedTicks % 20L == 0L) {
                resendPlaybackView();
                showPlaybackTime();
            }

            if (index >= events.size()) {
                stop(false);
                player.sendMessage(ChatColor.GREEN + "Playback finished. Use /tw playback stop to clear playback view.");
                return;
            }

            elapsedTicks++;
        }

        long getDelayTicks(int eventIndex) {
            PlaybackEvent event = events.get(eventIndex);
            if (!timed) {
                return eventIndex * UNTITLED_EVENT_INTERVAL_TICKS;
            }
            return Math.max(0L, Math.round((event.timestampMillis - firstTimestamp) / 50.0));
        }

        void apply(PlaybackEvent event) {
            if (event.timestampMillis != null) {
                lastPlaybackTimestamp = event.timestampMillis;
                showPlaybackTime();
            }

            DeviceConfig sensor = plugin.getRegistryManager().getDeviceByTopic(event.topic);
            if (sensor == null || sensor.getAction() == null || !"SENSOR".equalsIgnoreCase(sensor.getType())) {
                return;
            }

            String sensorName = getDeviceName(sensor);
            if (sensorName != null) {
                String oldValue = sensorStates.get(sensorName);
                if (oldValue == null || !oldValue.equals(event.value)) {
                    String timePrefix = "";
                    if (event.timestampMillis != null) {
                        timePrefix = ChatColor.YELLOW + "["
                                + LocalDateTime.ofInstant(Instant.ofEpochMilli(event.timestampMillis), ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                                + "] ";
                    }
                    player.sendMessage(timePrefix
                            + ChatColor.GRAY + "[Playback] "
                            + ChatColor.AQUA + sensorName
                            + ChatColor.GRAY + ": "
                            + ChatColor.RED + (oldValue == null ? "-" : oldValue)
                            + ChatColor.GRAY + " → "
                            + ChatColor.GREEN + event.value);
                }
                sensorStates.put(sensorName, event.value);
            }

            switch (sensor.getAction().toUpperCase(Locale.ROOT)) {
                case "TOGGLE":
                    if (isBinaryValue(event.value)) {
                        applyTarget(sensor, event.value);
                    }
                    break;
                case "TEXT_DISPLAY":
                    showTextDisplay(sensor, event.value);
                    applyThreshold(sensor);
                    break;
                default:
                    break;
            }
        }

        void applyThreshold(DeviceConfig sensor) {
            if (sensor.getActionData() == null || sensor.getActionData().get("threshold") == null) {
                return;
            }

            boolean shouldTurnOn = isThresholdActive(sensor);
            String dependencyName = sensor.getActionData().get("threshold_dependency");
            if (dependencyName != null && !dependencyName.isEmpty()) {
                shouldTurnOn = shouldTurnOn || isThresholdActive(plugin.getRegistryManager().getDevice(dependencyName));
            }

            String state = shouldTurnOn
                    ? sensor.getActionData().getOrDefault("on_value", "1")
                    : sensor.getActionData().getOrDefault("off_value", "0");
            if (isBinaryValue(state)) {
                applyTarget(sensor, state);
            }
        }

        boolean isThresholdActive(DeviceConfig sensor) {
            if (sensor == null || sensor.getActionData() == null) {
                return false;
            }
            String sensorName = getDeviceName(sensor);
            String currentState = sensorName == null ? "0" : sensorStates.getOrDefault(sensorName, "0");
            String thresholdValue = sensor.getActionData().get("threshold");
            if (thresholdValue == null) {
                return false;
            }

            try {
                return Double.parseDouble(currentState) >= Double.parseDouble(thresholdValue);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }

        void applyTarget(DeviceConfig sensor, String state) {
            if (sensor.getActionData() == null) {
                return;
            }

            String targetName = sensor.getActionData().get("target");
            if (targetName == null || targetName.isEmpty()) {
                return;
            }

            DeviceConfig actuator = plugin.getRegistryManager().getDevice(targetName);
            if (actuator == null || actuator.getActionData() == null) {
                return;
            }

            if ("WORLD_TIME".equalsIgnoreCase(actuator.getAction())) {
                sendTime(actuator, state);
                return;
            }

            if ("CIRCUIT".equalsIgnoreCase(actuator.getAction())) {
                sendCircuit(actuator, state);
                return;
            }

            Material material = getStateMaterial(actuator, state);
            if (material == null) {
                return;
            }

            Location loc = actuator.toBukkitLocation();
            if (loc == null || loc.getWorld() == null) {
                return;
            }

            if ("WALL_FAN".equalsIgnoreCase(actuator.getActionData().get("block_style"))) {
                if ("1".equals(state)) {
                    sendBlock(loc, material);
                    startFanParticle(targetName, loc, actuator);
                } else {
                    stopFanParticle(targetName);
                    sendBlock(loc, Material.AIR);
                }
                return;
            }

            if (Tag.DOORS.isTagged(material)) {
                sendDoor(loc, material);
                return;
            }

            if (material == Material.AIR) {
                if (isDoorActuator(actuator)) {
                    sendBlock(loc, Material.AIR);
                    sendBlock(loc.clone().add(0, 1, 0), Material.AIR);
                } else {
                    sendBlock(loc, Material.AIR);
                }
                return;
            }

            sendBlock(loc, material);
        }

        Material getStateMaterial(DeviceConfig actuator, String state) {
            String materialName = "1".equals(state)
                    ? actuator.getActionData().get("on_material")
                    : actuator.getActionData().get("off_material");
            Material material = Material.matchMaterial(materialName == null ? "" : materialName);
            if (material == null) {
                plugin.getLogger().warning("Invalid playback material: " + materialName);
            }
            return material;
        }

        void sendBlock(Location loc, Material material) {
            sendBlockData(loc, material.createBlockData());
        }

        void sendBlockData(Location loc, BlockData blockData) {
            try {
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.BLOCK_CHANGE);
                packet.getBlockPositionModifier().write(0, new BlockPosition(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                packet.getBlockData().write(0, WrappedBlockData.createData(blockData));
                sendPlaybackPacket(player, packet);
                fakeBlocks.put(loc.getBlock().getLocation(), blockData);
            } catch (Exception e) {
                plugin.getLogger().warning("Playback block packet failed: " + e.getMessage());
            }
        }

        void sendDoor(Location loc, Material material) {
            Door lower = (Door) material.createBlockData();
            lower.setHalf(Bisected.Half.BOTTOM);
            sendBlockData(loc, lower);

            Door upper = (Door) material.createBlockData();
            upper.setHalf(Bisected.Half.TOP);
            sendBlockData(loc.clone().add(0, 1, 0), upper);
        }

        void sendCircuit(DeviceConfig actuator, String state) {
            Location loc = actuator.toBukkitLocation();
            if (loc == null || loc.getWorld() == null) return;

            boolean isOn = "1".equals(state);

            applyCircuitAndSend(loc, isOn);

            BlockData blockData = loc.getBlock().getBlockData();
            if (blockData instanceof Door door) {
                Location otherLoc = (door.getHalf() == Bisected.Half.BOTTOM)
                        ? loc.clone().add(0, 1, 0)
                        : loc.clone().add(0, -1, 0);
                if (otherLoc.getBlock().getBlockData() instanceof Door) {
                    applyCircuitAndSend(otherLoc, isOn);
                }
            }
        }

        private void applyCircuitAndSend(Location loc, boolean isOn) {
            BlockData blockData = loc.getBlock().getBlockData();

            if (blockData instanceof org.bukkit.block.data.Lightable lightable) {
                lightable.setLit(isOn);
            }
            if (blockData instanceof org.bukkit.block.data.Openable openable) {
                openable.setOpen(isOn);
            }
            if (blockData instanceof org.bukkit.block.data.Powerable powerable) {
                powerable.setPowered(isOn);
            }

            sendBlockData(loc, blockData);
        }

        boolean isDoorActuator(DeviceConfig actuator) {
            Material onMaterial = Material.matchMaterial(actuator.getActionData().getOrDefault("on_material", ""));
            return onMaterial != null && Tag.DOORS.isTagged(onMaterial);
        }

        void sendTime(DeviceConfig actuator, String state) {
            Location loc = actuator.toBukkitLocation();
            if (loc == null || loc.getWorld() == null) {
                return;
            }

            String timeValue = "1".equals(state)
                    ? actuator.getActionData().get("on_time")
                    : actuator.getActionData().get("off_time");
            if (timeValue == null) {
                return;
            }

            try {
                long time = Long.parseLong(timeValue);
                PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.UPDATE_TIME);
                packet.getLongs().write(0, loc.getWorld().getFullTime());
                packet.getLongs().write(1, time);
                sendPlaybackPacket(player, packet);
                lastTime = time;
            } catch (Exception e) {
                plugin.getLogger().warning("Playback time packet failed: " + e.getMessage());
            }
        }

        void showTextDisplay(DeviceConfig sensor, String value) {
            if (sensor.getActionData() == null) {
                return;
            }

            Location loc = sensor.toBukkitLocation();
            if (loc == null || loc.getWorld() == null || sensor.getTopic() == null) {
                return;
            }

            TextDisplay display = textDisplays.get(sensor.getTopic());
            if (display == null || display.isDead()) {
                display = loc.getWorld().spawn(loc.clone().add(0.5, 1.5, 0.5), TextDisplay.class);
                display.setBillboard(Display.Billboard.FIXED);
                display.setSeeThrough(true);
                display.setShadowed(true);
                display.setDefaultBackground(false);
                display.setPersistent(false);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.hideEntity(plugin, display);
                }
                player.showEntity(plugin, display);
                textDisplays.put(sensor.getTopic(), display);
            }

            display.teleport(loc.clone().add(0.5, 1.5, 0.5));
            display.setRotation(getFloat(sensor, "yaw", 180f), getFloat(sensor, "pitch", 0f));
            display.setViewRange(getFloat(sensor, "view_range", 25f));
            display.setText(formatText(sensor, value));
        }

        String formatText(DeviceConfig sensor, String value) {
            String format = sensor.getActionData().getOrDefault("format", "%s");
            ChatColor color = getColor(sensor.getActionData().getOrDefault("color", "WHITE"));
            try {
                return color + String.format(format, value);
            } catch (IllegalArgumentException e) {
                return color + value;
            }
        }

        ChatColor getColor(String value) {
            try {
                return ChatColor.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException ignored) {
                return ChatColor.WHITE;
            }
        }

        float getFloat(DeviceConfig sensor, String key, float defaultValue) {
            try {
                return Float.parseFloat(sensor.getActionData().getOrDefault(key, Float.toString(defaultValue)));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        void startFanParticle(String key, Location loc, DeviceConfig actuator) {
            stopFanParticle(key);
            BlockFace facing = parseFace(actuator.getActionData().getOrDefault("facing", "EAST"));
            Particle particle = parseParticle(actuator.getActionData().getOrDefault("particle", "CLOUD"));

            BukkitTask particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) {
                    stopFanParticle(key);
                    return;
                }

                Location particleLoc = loc.clone().add(
                        0.5 + facing.getModX() * 0.75,
                        0.5,
                        0.5 + facing.getModZ() * 0.75
                );
                player.spawnParticle(particle, particleLoc, 6,
                        Math.abs(facing.getModZ()) * 0.35,
                        0.08,
                        Math.abs(facing.getModX()) * 0.35,
                        0.02);
            }, 0L, 10L);

            particleTasks.put(key, particleTask);
        }

        void stopFanParticle(String key) {
            BukkitTask particleTask = particleTasks.remove(key);
            if (particleTask != null) {
                particleTask.cancel();
            }
        }

        void stop(boolean clearView) {
            if (task != null) {
                task.cancel();
                task = null;
            }
            for (BukkitTask particleTask : particleTasks.values()) {
                if (particleTask != null) {
                    particleTask.cancel();
                }
            }
            particleTasks.clear();

            for (TextDisplay display : textDisplays.values()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
            textDisplays.clear();

            if (clearView && player.isOnline()) {
                for (Location loc : new ArrayList<>(fakeBlocks.keySet())) {
                    sendBlock(loc, loc.getBlock().getType());
                }
            }
            fakeBlocks.clear();
        }

        void resendPlaybackView() {
            if (!player.isOnline()) {
                return;
            }

            for (Map.Entry<Location, BlockData> entry : new ArrayList<>(fakeBlocks.entrySet())) {
                sendBlockData(entry.getKey(), entry.getValue());
            }

            if (lastTime != null) {
                try {
                    PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.UPDATE_TIME);
                    packet.getLongs().write(0, player.getWorld().getFullTime());
                    packet.getLongs().write(1, lastTime);
                    sendPlaybackPacket(player, packet);
                } catch (Exception e) {
                    plugin.getLogger().warning("Playback time resend failed: " + e.getMessage());
                }
            }
        }

        void showPlaybackTime() {
            if (!player.isOnline()) {
                return;
            }

            long displayTimestamp;
            if (timed) {
                displayTimestamp = firstTimestamp + elapsedTicks * 50L;
            } else if (lastPlaybackTimestamp != null) {
                displayTimestamp = lastPlaybackTimestamp;
            } else {
                return;
            }

            String label = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(displayTimestamp),
                    ZoneId.systemDefault()
            ).format(PLAYBACK_TIME_FORMAT);
            player.spigot().sendMessage(
                    ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(ChatColor.AQUA + "Playback " + ChatColor.WHITE + label)
            );
        }
    }

    private boolean isBinaryValue(String value) {
        return "0".equals(value) || "1".equals(value);
    }

    private BlockFace parseFace(String value) {
        try {
            BlockFace face = BlockFace.valueOf(value.toUpperCase(Locale.ROOT));
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH || face == BlockFace.EAST || face == BlockFace.WEST) {
                return face;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
        }
        return BlockFace.EAST;
    }

    private Particle parseParticle(String value) {
        try {
            return Particle.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Particle.CLOUD;
        }
    }

    private record PlaybackEvent(String topic, String value, Long timestampMillis) {
    }
}
