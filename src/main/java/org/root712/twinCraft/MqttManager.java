package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Tag;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MqttManager implements MqttCallback {
    private static final String TOPIC_GLOBAL = "twin/#";
    private static final float DEFAULT_TEXT_YAW = 180f;
    private static final float DEFAULT_TEXT_PITCH = 0f;
    private static final float DEFAULT_TEXT_VIEW_RANGE = 25f;

    private final TwinCraft plugin;
    private final Map<String, TextDisplay> textDisplays = new HashMap<>();
    private final Map<String, BukkitTask> particleTasks = new HashMap<>();
    private final Map<String, String> pendingSensorValues = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> pollingTasks = new HashMap<>();
    private final Map<String, Integer> pollingIntervals = new HashMap<>();
    private final Set<String> activePollingTopics = ConcurrentHashMap.newKeySet();
    private final Set<String> queuedPollingSetups = ConcurrentHashMap.newKeySet();

    private MqttClient client;

    public MqttManager(TwinCraft plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String broker = plugin.getConfig().getString("mqtt.broker", "");
        String clientId = plugin.getConfig().getString("mqtt.client_id", "TwinCraftPlugin");
        String username = plugin.getConfig().getString("mqtt.username", "");
        String password = plugin.getConfig().getString("mqtt.password", "");

        if (broker.isEmpty()) {
            plugin.getLogger().warning("MQTT broker URL is not configured. Check config.yml.");
            return;
        }

        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            if (!username.isEmpty()) {
                options.setUserName(username);
            }
            if (!password.isEmpty()) {
                options.setPassword(password.toCharArray());
            }

            client.setCallback(this);
            client.connect(options);
            client.subscribe(TOPIC_GLOBAL, 1);

            plugin.getLogger().info("Connected to MQTT broker: " + broker);
        } catch (MqttException e) {
            plugin.getLogger().severe("MQTT connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disconnect() {
        removeTextDisplays();
        stopAllParticleTasks();
        stopAllPollingTasks();

        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                plugin.getLogger().info("Disconnected from MQTT broker.");
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        if (client == null || !client.isConnected()) {
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8));
            mqttMessage.setQos(1);
            client.publish(topic, mqttMessage);
            plugin.getLogger().info("[MQTT publish] topic=" + topic + ", value=" + message);
        } catch (MqttException e) {
            plugin.getLogger().severe("MQTT publish failed: " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable throwable) {
        plugin.getLogger().warning("MQTT connection lost.");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String value = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
        DeviceConfig config = plugin.getRegistryManager().getDeviceByTopic(topic);
        if (config == null || !"SENSOR".equalsIgnoreCase(config.getType())) {
            return;
        }

        if (isPollingSensor(config)) {
            queuePollingSensorValue(config, value);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> handleSensor(config, value));
    }

    private boolean isPollingSensor(DeviceConfig sensor) {
        return "POLLING".equalsIgnoreCase(sensor.getTriggerType()) && sensor.getUpdateInterval() > 0;
    }

    private void queuePollingSensorValue(DeviceConfig sensor, String value) {
        String topic = sensor.getTopic();
        if (topic == null || topic.isEmpty()) {
            return;
        }

        pendingSensorValues.put(topic, value);
        if (activePollingTopics.contains(topic) || !queuedPollingSetups.add(topic)) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ensurePollingTask(sensor);
            } finally {
                queuedPollingSetups.remove(topic);
            }
        });
    }

    private void ensurePollingTask(DeviceConfig sensor) {
        String topic = sensor.getTopic();
        if (topic == null || topic.isEmpty() || !isPollingSensor(sensor)) {
            return;
        }

        int interval = sensor.getUpdateInterval();
        Integer currentInterval = pollingIntervals.get(topic);
        if (pollingTasks.containsKey(topic) && currentInterval != null && currentInterval == interval) {
            activePollingTopics.add(topic);
            return;
        }

        stopPollingTask(topic);

        long periodTicks = Math.max(1L, Math.round(interval / 50.0));
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> runPollingCycle(topic), periodTicks, periodTicks);
        pollingTasks.put(topic, task);
        pollingIntervals.put(topic, interval);
        activePollingTopics.add(topic);
    }

    private void runPollingCycle(String topic) {
        DeviceConfig sensor = plugin.getRegistryManager().getDeviceByTopic(topic);
        if (sensor == null || !"SENSOR".equalsIgnoreCase(sensor.getType()) || !isPollingSensor(sensor)) {
            pendingSensorValues.remove(topic);
            stopPollingTask(topic);
            return;
        }

        String value = pendingSensorValues.remove(topic);
        if (value != null) {
            handleSensor(sensor, value);
        }
    }

    private void handleSensor(DeviceConfig sensor, String value) {
        if (sensor == null || sensor.getAction() == null) {
            return;
        }

        switch (sensor.getAction().toUpperCase(Locale.ROOT)) {
            case "TOGGLE":
                handleToggle(sensor, value);
                break;
            case "TEXT_DISPLAY":
                handleTextDisplay(sensor, value);
                break;
            default:
                break;
        }
    }

    private void handleToggle(DeviceConfig sensor, String value) {
        if (!isBinaryValue(value) || sensor.getActionData() == null) {
            return;
        }

        sensor.setCurrentState(value);
        applyTargetState(sensor, value);
    }

    private void handleTextDisplay(DeviceConfig sensor, String value) {
        if (sensor.getActionData() == null) {
            return;
        }

        sensor.setCurrentState(value);
        refreshTextDisplay(sensor);
        applyThresholdAutomation(sensor, value);
    }

    public void refreshTextDisplay(DeviceConfig sensor) {
        if (sensor == null || sensor.getActionData() == null) {
            return;
        }

        Location loc = sensor.toBukkitLocation();
        String topic = sensor.getTopic();
        if (loc == null || loc.getWorld() == null || topic == null || topic.isEmpty()) {
            return;
        }

        TextDisplay display = textDisplays.get(topic);
        if (display == null || display.isDead()) {
            display = loc.getWorld().spawn(loc.clone().add(0.5, 1.5, 0.5), TextDisplay.class);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setDefaultBackground(false);
            display.setPersistent(false);
            textDisplays.put(topic, display);
        }

        float yaw = getFloatActionData(sensor, "yaw", DEFAULT_TEXT_YAW);
        float pitch = getFloatActionData(sensor, "pitch", DEFAULT_TEXT_PITCH);
        float viewRange = getFloatActionData(sensor, "view_range", DEFAULT_TEXT_VIEW_RANGE);

        display.teleport(loc.clone().add(0.5, 1.5, 0.5));
        display.setRotation(yaw, pitch);
        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(viewRange);
        display.setText(formatDisplayText(sensor, sensor.getCurrentState()));
    }

    public void refreshToggle(DeviceConfig sensor) {
        applyTargetState(sensor, null);
    }

    public void triggerVirtualToggle(DeviceConfig sensor) {
        if (sensor == null || sensor.getActionData() == null) {
            return;
        }

        String nextState = "1".equals(sensor.getCurrentState()) ? "0" : "1";
        sensor.setCurrentState(nextState);
        applyTargetState(sensor, nextState);
        publishSensorState(sensor, nextState);
        publishTargetActuatorState(sensor, nextState);
    }

    private void publishSensorState(DeviceConfig sensor, String state) {
        String topic = getPublishTopic(sensor);
        if (topic == null || topic.isEmpty()) {
            return;
        }

        publish(topic, state);
    }

    private void publishTargetActuatorState(DeviceConfig sensor, String state) {
        String targetName = sensor.getActionData().get("target");
        if (targetName == null || targetName.isEmpty()) {
            return;
        }

        DeviceConfig actuator = plugin.getRegistryManager().getDevice(targetName);
        if (actuator == null) {
            return;
        }

        String topic = getPublishTopic(actuator);
        if (topic == null || topic.isEmpty()) {
            return;
        }

        publish(topic, state);
    }

    private String getPublishTopic(DeviceConfig device) {
        if (device.getActionData() != null) {
            String publishTopic = device.getActionData().get("publish");
            if (publishTopic != null && !publishTopic.isEmpty()) {
                return publishTopic;
            }
        }

        return device.getTopic();
    }

    public void refreshPolling(DeviceConfig sensor) {
        if (sensor == null) {
            return;
        }

        if (isPollingSensor(sensor)) {
            ensurePollingTask(sensor);
            return;
        }

        String topic = sensor.getTopic();
        if (topic != null && !topic.isEmpty()) {
            stopPollingTask(topic);
        }
    }

    private void applyThresholdAutomation(DeviceConfig sensor, String value) {
        String thresholdValue = sensor.getActionData().get("threshold");
        if (thresholdValue == null) {
            return;
        }

        boolean shouldTurnOn = isThresholdActive(sensor);
        String dependencyName = sensor.getActionData().get("threshold_dependency");
        if (dependencyName != null && !dependencyName.isEmpty()) {
            DeviceConfig dependency = plugin.getRegistryManager().getDevice(dependencyName);
            shouldTurnOn = shouldTurnOn || isThresholdActive(dependency);
        }

        String state = shouldTurnOn
                ? sensor.getActionData().getOrDefault("on_value", "1")
                : sensor.getActionData().getOrDefault("off_value", "0");

        if (isBinaryValue(state)) {
            applyTargetState(sensor, state);
        }
    }

    private boolean isThresholdActive(DeviceConfig sensor) {
        if (sensor == null || sensor.getActionData() == null) {
            return false;
        }

        String thresholdValue = sensor.getActionData().get("threshold");
        if (thresholdValue == null) {
            return false;
        }

        try {
            double currentValue = Double.parseDouble(sensor.getCurrentState());
            double threshold = Double.parseDouble(thresholdValue);
            return currentValue >= threshold;
        } catch (NumberFormatException ignored) {
            plugin.getLogger().warning("Invalid threshold state: " + sensor.getCurrentState());
            return false;
        }
    }

    private void applyTargetState(DeviceConfig sensor, String state) {
        if (sensor == null || sensor.getActionData() == null) {
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

        String targetState = state != null ? state : actuator.getCurrentState();
        if (!isBinaryValue(targetState)) {
            return;
        }

        Location loc = actuator.toBukkitLocation();
        if (loc == null || loc.getWorld() == null) {
            return;
        }

        actuator.setCurrentState(targetState);
        // WORLD_TIME
        if ("WORLD_TIME".equalsIgnoreCase(actuator.getAction())) {
            applyWorldTime(actuator, targetState);
            return;
        }

        // CIRCUIT
        if ("CIRCUIT".equalsIgnoreCase(actuator.getAction())){
            applyCircuit(actuator, targetState);
            return;
        }

        // BLOCK_CHANGE
        String materialName = "1".equals(targetState)
                ? actuator.getActionData().get("on_material")
                : actuator.getActionData().get("off_material");
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material for actuator " + targetName + ": " + materialName);
            return;
        }

        Block block = loc.getBlock();

        if ("WALL_FAN".equalsIgnoreCase(actuator.getActionData().get("block_style"))) {
            if ("1".equals(targetState)) {
                placeWallFan(block, material, actuator);
                startFanParticle(targetName, block, actuator);
            } else {
                stopFanParticle(targetName);
                block.setType(Material.AIR, false);
            }
            return;
        }

        if (Tag.DOORS.isTagged(material)) {
            placeDoor(block, material);
            return;
        }

        if (material == Material.AIR) {
            removeDoorIfPresent(block);
        }

        block.setType(material);
    }

    private void applyCircuit(DeviceConfig actuator, String targetState) {
        Location loc = actuator.toBukkitLocation();
        if (loc == null || loc.getWorld() == null) return;

        Block block = loc.getBlock();
        boolean isOn = "1".equals(targetState);

        applyCircuitToBlock(block, isOn);

        BlockData blockData = block.getBlockData();
        if (blockData instanceof Door door) {
            Block otherHalf = (door.getHalf() == Bisected.Half.BOTTOM)
                    ? block.getRelative(BlockFace.UP)
                    : block.getRelative(BlockFace.DOWN);
            if (otherHalf.getBlockData() instanceof Door) {
                applyCircuitToBlock(otherHalf, isOn);
            }
        }
    }

    private void applyCircuitToBlock(Block block, boolean isOn) {
        BlockData blockData = block.getBlockData();

        if (blockData instanceof org.bukkit.block.data.Lightable lightable){
            lightable.setLit(isOn);
        }

        if (blockData instanceof org.bukkit.block.data.Openable openable){
            openable.setOpen(isOn);
        }

        if (blockData instanceof org.bukkit.block.data.Powerable powerable){
            powerable.setPowered(isOn);
        }

        block.setBlockData(blockData, false);
    }

    private String formatDisplayText(DeviceConfig sensor, String value) {
        String format = sensor.getActionData().getOrDefault("format", "%s");
        ChatColor color = getChatColor(sensor.getActionData().getOrDefault("color", "WHITE"));

        try {
            return color + String.format(format, value);
        } catch (IllegalArgumentException e) {
            return color + value;
        }
    }

    private ChatColor getChatColor(String colorName) {
        try {
            return ChatColor.valueOf(colorName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return ChatColor.WHITE;
        }
    }

    private float getFloatActionData(DeviceConfig sensor, String key, float defaultValue) {
        try {
            return Float.parseFloat(sensor.getActionData().getOrDefault(key, Float.toString(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean isBinaryValue(String value) {
        return "0".equals(value) || "1".equals(value);
    }

    private void removeTextDisplays() {
        for (TextDisplay display : textDisplays.values()) {
            if (display != null && !display.isDead()) {
                display.remove();
            }
        }
        textDisplays.clear();
    }

    private void placeDoor(Block block, Material material) {
        if (!Tag.DOORS.isTagged(material)) {
            block.setType(material);
            return;
        }

        Block upperBlock = block.getRelative(BlockFace.UP);
        Block supportBlock = block.getRelative(BlockFace.DOWN);

        if (!supportBlock.getType().isSolid()) {
            plugin.getLogger().warning("Cannot place door because there is no solid support below: " + supportBlock.getLocation());
            return;
        }

        if (!isAirOrDoor(block) || !isAirOrDoor(upperBlock)) {
            plugin.getLogger().warning("Cannot place door because the space is occupied: " + block.getLocation());
            return;
        }

        removeDoorIfPresent(block);

        Door lower = (Door) material.createBlockData();
        lower.setHalf(Bisected.Half.BOTTOM);
        block.setBlockData(lower, false);

        Door upper = (Door) material.createBlockData();
        upper.setHalf(Bisected.Half.TOP);
        upperBlock.setBlockData(upper, false);
    }

    private void removeDoorIfPresent(Block block) {
        removeDoorHalf(block);
        removeDoorHalf(block.getRelative(BlockFace.UP));
        removeDoorHalf(block.getRelative(BlockFace.DOWN));
    }

    private void removeDoorHalf(Block block) {
        if (Tag.DOORS.isTagged(block.getType())) {
            block.setType(Material.AIR, false);
        }
    }

    private boolean isAirOrDoor(Block block) {
        return block.getType().isAir() || Tag.DOORS.isTagged(block.getType());
    }

    private void applyWorldTime(DeviceConfig actuator, String targetState) {
        Location loc = actuator.toBukkitLocation();
        if (loc == null || loc.getWorld() == null || actuator.getActionData() == null) {
            return;
        }

        String timeValue = "1".equals(targetState)
                ? actuator.getActionData().get("on_time")
                : actuator.getActionData().get("off_time");

        if (timeValue == null) {
            plugin.getLogger().warning("Missing world time value for actuator: " + actuator.getTopic());
            return;
        }

        try {
            long time = Long.parseLong(timeValue);
            loc.getWorld().setTime(time);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid world time value: " + timeValue);
        }
    }

    private void placeWallFan(Block block, Material material, DeviceConfig actuator) {
        if (!isTrapDoor(material)) {
            block.setType(material, false);
            return;
        }

        if (!block.getType().isAir() && !isTrapDoor(block.getType())) {
            plugin.getLogger().warning("Cannot place wall fan because the space is occupied: " + block.getLocation());
            return;
        }

        TrapDoor trapDoor = (TrapDoor) material.createBlockData();
        trapDoor.setOpen(true);
        trapDoor.setHalf(Bisected.Half.TOP);
        trapDoor.setFacing(parseBlockFace(actuator.getActionData().getOrDefault("facing", "EAST")));

        block.setBlockData(trapDoor, false);
    }

    private void startFanParticle(String key, Block block, DeviceConfig actuator) {
        stopFanParticle(key);

        BlockFace facing = parseBlockFace(actuator.getActionData().getOrDefault("facing", "EAST"));
        Particle particle = parseParticle(actuator.getActionData().getOrDefault("particle", "DUST"));
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(185, 195, 205), 0.6f);
        double[] angle = {0.0};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (block.getWorld() == null || !isTrapDoor(block.getType())) {
                stopFanParticle(key);
                return;
            }

            Location center = block.getLocation().add(
                    0.5 + facing.getModX() * 0.56,
                    0.5,
                    0.5 + facing.getModZ() * 0.56
            );
            double sideX = Math.abs(facing.getModZ());
            double sideZ = Math.abs(facing.getModX());
            for (int blade = 0; blade < 3; blade++) {
                double bladeAngle = angle[0] + (Math.PI * 2.0 * blade / 3.0);
                double radius = 0.26;
                Location particleLoc = center.clone().add(
                        sideX * Math.cos(bladeAngle) * radius,
                        Math.sin(bladeAngle) * radius,
                        sideZ * Math.cos(bladeAngle) * radius
                );
                if (particle == Particle.DUST) {
                    block.getWorld().spawnParticle(particle, particleLoc, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
                } else {
                    block.getWorld().spawnParticle(particle, particleLoc, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            angle[0] = (angle[0] + Math.PI / 6.0) % (Math.PI * 2.0);
        }, 0L, 4L);

        particleTasks.put(key, task);
    }

    private void stopFanParticle(String key) {
        BukkitTask task = particleTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    private void stopAllParticleTasks() {
        for (BukkitTask task : particleTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        particleTasks.clear();
    }

    private void stopPollingTask(String topic) {
        BukkitTask task = pollingTasks.remove(topic);
        if (task != null) {
            task.cancel();
        }
        pollingIntervals.remove(topic);
        activePollingTopics.remove(topic);
    }

    private void stopAllPollingTasks() {
        for (BukkitTask task : pollingTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        pollingTasks.clear();
        pollingIntervals.clear();
        activePollingTopics.clear();
        queuedPollingSetups.clear();
        pendingSensorValues.clear();
    }

    private boolean isTrapDoor(Material material) {
        return material != null && Tag.TRAPDOORS.isTagged(material);
    }

    private BlockFace parseBlockFace(String value) {
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
            return Particle.DUST;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
