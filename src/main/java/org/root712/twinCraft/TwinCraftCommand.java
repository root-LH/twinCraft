package org.root712.twinCraft;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class TwinCraftCommand implements CommandExecutor {
    private final TwinCraft plugin;

    public TwinCraftCommand(TwinCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                handleList(sender);
                break;
            case "edit":
                handleEdit(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "view":
                handleView(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void handleList(CommandSender sender) {
        Map<String, DeviceConfig> devices = plugin.getRegistryManager().getAllDevices();
        if (devices.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "등록된 노드가 없습니다.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "=== 노드 목록 ===");
        for (String name : devices.keySet()) {
            sender.sendMessage(ChatColor.WHITE + "- " + name);
        }
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw edit topic|type|pos [name] [value...]");
            return;
        }

        String type = args[1].toLowerCase();
        String name = args[2];
        DeviceConfig device = plugin.getRegistryManager().getDevice(name);

        if (device == null) {
            sender.sendMessage(ChatColor.RED + "존재하지 않는 노드입니다: " + name);
            return;
        }

        switch (type) {
            case "topic":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit topic [name] [topic]");
                    return;
                }
                device.setTopic(args[3]);
                sender.sendMessage(ChatColor.GREEN + name + "의 topic이 " + args[3] + "(으)로 변경되었습니다.");
                break;
            case "type":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit type [name] [sensor|actuator]");
                    return;
                }
                String newType = args[3].toUpperCase();
                if (!newType.equals("SENSOR") && !newType.equals("ACTUATOR")) {
                    sender.sendMessage(ChatColor.RED + "타입은 sensor 또는 actuator여야 합니다.");
                    return;
                }
                device.setType(newType);
                sender.sendMessage(ChatColor.GREEN + name + "의 type이 " + newType + "(으)로 변경되었습니다.");
                break;
            case "pos":
                if (args.length < 6) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit pos [name] [x] [y] [z]");
                    return;
                }
                try {
                    double x = Double.parseDouble(args[3]);
                    double y = Double.parseDouble(args[4]);
                    double z = Double.parseDouble(args[5]);
                    device.setX(x);
                    device.setY(y);
                    device.setZ(z);
                    if (sender instanceof Player) {
                        device.setWorld(((Player) sender).getWorld().getName());
                    }
                    sender.sendMessage(ChatColor.GREEN + name + "의 좌표가 변경되었습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "좌표는 숫자여야 합니다.");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "사용법: /tw edit topic|type|pos [name] [value...]");
                return;
        }

        plugin.getRegistryManager().updateDevice();
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw delete [name]");
            return;
        }

        String name = args[1];
        if (plugin.getRegistryManager().getDevice(name) == null) {
            sender.sendMessage(ChatColor.RED + "존재하지 않는 노드입니다: " + name);
            return;
        }

        plugin.getRegistryManager().deleteDevice(name);
        sender.sendMessage(ChatColor.GREEN + name + " 노드가 삭제되었습니다.");
    }

    private void handleView(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw view [name]");
            return;
        }

        String name = args[1];
        DeviceConfig device = plugin.getRegistryManager().getDevice(name);

        if (device == null) {
            sender.sendMessage(ChatColor.RED + "존재하지 않는 노드입니다: " + name);
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "=== 노드 정보: " + name + " ===");
        sender.sendMessage(ChatColor.WHITE + "Topic: " + device.getTopic());
        sender.sendMessage(ChatColor.WHITE + "Type: " + device.getType());
        sender.sendMessage(ChatColor.WHITE + "World: " + device.getWorld());
        sender.sendMessage(ChatColor.WHITE + "X: " + device.getX());
        sender.sendMessage(ChatColor.WHITE + "Y: " + device.getY());
        sender.sendMessage(ChatColor.WHITE + "Z: " + device.getZ());
        sender.sendMessage(ChatColor.WHITE + "Action: " + device.getAction());
        if (device.getActionData() != null) {
            sender.sendMessage(ChatColor.WHITE + "Action Data: " + device.getActionData().toString());
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "=== TwinCraft 명령어 도움말 ===");
        sender.sendMessage(ChatColor.WHITE + "/tw list - 모든 노드 목록 출력");
        sender.sendMessage(ChatColor.WHITE + "/tw edit topic [name] [topic] - 토픽 변경");
        sender.sendMessage(ChatColor.WHITE + "/tw edit type [name] [sensor|actuator] - 타입 변경");
        sender.sendMessage(ChatColor.WHITE + "/tw edit pos [name] [x] [y] [z] - 위치 변경");
        sender.sendMessage(ChatColor.WHITE + "/tw delete [name] - 노드 삭제");
        sender.sendMessage(ChatColor.WHITE + "/tw view [name] - 노드 정보 출력");
    }
}