package org.root712.twinCraft;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Tag;
import org.bukkit.block.data.Bisected;
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

public class MqttManager implements MqttCallback {
    private static final String TOPIC_GLOBAL = "twin/#";
    private static final float DEFAULT_TEXT_YAW = 180f;
    private static final float DEFAULT_TEXT_PITCH = 0f;
    private static final float DEFAULT_TEXT_VIEW_RANGE = 25f;

    private final TwinCraft plugin;
    private final Map<String, TextDisplay> textDisplays = new HashMap<>();

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

        Bukkit.getScheduler().runTask(plugin, () -> handleSensor(config, value));
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

    private void applyThresholdAutomation(DeviceConfig sensor, String value) {
        String thresholdValue = sensor.getActionData().get("threshold");
        if (thresholdValue == null) {
            return;
        }

        try {
            double currentValue = Double.parseDouble(value);
            double threshold = Double.parseDouble(thresholdValue);
            String state = currentValue >= threshold
                    ? sensor.getActionData().getOrDefault("on_value", "1")
                    : sensor.getActionData().getOrDefault("off_value", "0");

            if (isBinaryValue(state)) {
                applyTargetState(sensor, state);
            }
        } catch (NumberFormatException ignored) {
            plugin.getLogger().warning("Invalid threshold payload: " + value);
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

        String materialName = "1".equals(targetState)
                ? actuator.getActionData().get("on_material")
                : actuator.getActionData().get("off_material");
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material for actuator " + targetName + ": " + materialName);
            return;
        }

        Block block = loc.getBlock();
        actuator.setCurrentState(targetState);
        block.setType(material);
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

    private void placeDoor(Block block, Material material){
        block.setType(material);

        Door lower = (Door) block.getBlockData();
        lower.setHalf(Bisected.Half.BOTTOM);
        block.setBlockData(lower);

        Block upperBlock = block.getRelative(BlockFace.UP);
        upperBlock.setType(material);
        Door upper = (Door) upperBlock.getBlockData();
        upper.setHalf(Bisected.Half.TOP);
        upperBlock.setBlockData(upper);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
