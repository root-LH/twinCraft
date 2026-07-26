package org.root712.twinCraft;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TwinCraftCommand implements CommandExecutor, TabCompleter {
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
            case "new":
                handleNew(sender, args);
                break;
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
            case "playback":
                handlePlayback(sender, args);
                break;
            case "help":
            case "?":
                handleHelp(sender, args);
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return complete(args[0], List.of("new", "list", "edit", "delete", "view", "playback", "help"));
        }

        String subCommand = args[0].toLowerCase();
        if (args.length == 2) {
            return switch (subCommand) {
                case "edit" -> complete(args[1], List.of("topic", "type", "pos", "trigger", "interval", "action", "data", "data-remove"));
                case "delete", "view" -> complete(args[1], plugin.getRegistryManager().getAllDevices().keySet());
                case "playback" -> complete(args[1], List.of("stop", "next"));
                case "help", "?" -> complete(args[1], List.of("edit", "playback"));
                default -> List.of();
            };
        }

        if (args.length == 3 && "edit".equals(subCommand)) {
            return complete(args[2], plugin.getRegistryManager().getAllDevices().keySet());
        }

        return List.of();
    }

    private List<String> complete(String input, Iterable<String> candidates) {
        String lowerInput = input.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase().startsWith(lowerInput)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    private void handlePlayback(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Playback은 플레이어만 사용할 수 있습니다.");
            return;
        }

        Player player = (Player) sender;
        if (args.length >= 2) {
            if ("stop".equalsIgnoreCase(args[1])) {
                plugin.getPlaybackManager().stop(player);
                return;
            }

            if ("next".equalsIgnoreCase(args[1])) {
                plugin.getPlaybackManager().next(player);
                return;
            }
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw playback [start] [end] [limit]");
            sender.sendMessage(ChatColor.YELLOW + "/tw playback next - 다음 Playback 이벤트를 즉시 적용");
            sender.sendMessage(ChatColor.YELLOW + "/tw playback stop - 현재 playback 종료");
            return;
        }

        int limit = 5000;
        if (args.length >= 4) {
            try {
                limit = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Limit must be a number.");
                return;
            }
        }

        plugin.getPlaybackManager().start(player, args[1], args[2], limit);
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

    private void handleNew(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw new [name]");
            return;
        }

        String name = args[1];
        if (plugin.getRegistryManager().getDevice(name) != null) {
            sender.sendMessage(ChatColor.RED + "이미 존재하는 노드 이름입니다: " + name);
            return;
        }

        DeviceConfig device = new DeviceConfig();
        device.setType("SENSOR"); // 기본값 설정
        if (sender instanceof Player) {
            Player player = (Player) sender;
            device.setWorld(player.getWorld().getName());
            device.setX(player.getLocation().getX());
            device.setY(player.getLocation().getY());
            device.setZ(player.getLocation().getZ());
        }

        plugin.getRegistryManager().addDevice(name, device);
        sender.sendMessage(ChatColor.GREEN + name + " 노드가 생성되었습니다.");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit를 통해 상세 설정을 완료해주세요.");
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "사용법: /tw edit topic|type|pos|trigger|interval [name] [value...]");
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
                refreshRuntimeState(device);
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
            case "trigger":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit trigger [name] [polling|event]");
                    return;
                }
                String trigger = args[3].toUpperCase();
                if (!trigger.equals("POLLING") && !trigger.equals("EVENT")) {
                    sender.sendMessage(ChatColor.RED + "트리거는 polling 또는 event여야 합니다.");
                    return;
                }
                device.setTriggerType(trigger);
                refreshRuntimeState(device);
                sender.sendMessage(ChatColor.GREEN + name + "의 trigger가 " + trigger + "(으)로 변경되었습니다.");
                break;
            case "interval":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit interval [name] [ms]");
                    return;
                }
                try {
                    int interval = Integer.parseInt(args[3]);
                    device.setUpdateInterval(interval);
                    refreshRuntimeState(device);
                    sender.sendMessage(ChatColor.GREEN + name + "의 interval이 " + interval + "ms로 변경되었습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "인터벌은 숫자(ms)여야 합니다.");
                }
                break;
            case "action":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit action [name] [value]");
                    return;
                }
                device.setAction(args[3]);
                sender.sendMessage(ChatColor.GREEN + name + "의 action이 " + args[3] + "(으)로 변경되었습니다.");
                break;
            case "data":
                if (args.length < 5) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit data [name] [key] [value]");
                    return;
                }
                if (device.getActionData() == null) {
                    device.setActionData(new java.util.HashMap<>());
                }
                device.getActionData().put(args[3], args[4]);
                refreshRuntimeState(device);
                sender.sendMessage(ChatColor.GREEN + name + "의 actionData[" + args[3] + "]가 " + args[4] + "(으)로 설정되었습니다.");
                break;
            case "data-remove":
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "사용법: /tw edit data-remove [name] [key]");
                    return;
                }
                if (device.getActionData() == null || !device.getActionData().containsKey(args[3])) {
                    sender.sendMessage(ChatColor.RED + name + " actionData[" + args[3] + "] 가 존재하지 않습니다.");
                    return;
                }
                device.getActionData().remove(args[3]);
                refreshRuntimeState(device);
                sender.sendMessage(ChatColor.GREEN + name + " actionData[" + args[3] + "] 가 제거 되었습니다.");
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
                    refreshRuntimeState(device);
                    sender.sendMessage(ChatColor.GREEN + name + "의 좌표가 변경되었습니다.");
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "좌표는 숫자여야 합니다.");
                }
                break;
            default:
                sender.sendMessage(ChatColor.RED + "사용법: /tw edit topic|type|pos|trigger|interval|action|data [name] [value...]");
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
        if (device.getActionData() != null && !device.getActionData().isEmpty()) {
            sender.sendMessage(ChatColor.WHITE + "ActionData: " + device.getActionData());
        }
        sender.sendMessage(ChatColor.WHITE + "CurrentState: " + device.getCurrentState());
        sender.sendMessage(ChatColor.WHITE + "Trigger: " + device.getTriggerType());
        sender.sendMessage(ChatColor.WHITE + "Interval: " + device.getUpdateInterval() + "ms");
    }

    private void refreshRuntimeState(DeviceConfig device) {
        plugin.getMqttManager().refreshPolling(device);

        if ("TEXT_DISPLAY".equalsIgnoreCase(device.getAction())) {
            plugin.getMqttManager().refreshTextDisplay(device);
        } else if ("TOGGLE".equalsIgnoreCase(device.getAction())) {
            plugin.getMqttManager().refreshToggle(device);
        }
    }

    private void handleHelp(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendHelp(sender);
            return;
        }

        switch (args[1].toLowerCase()) {
            case "edit":
                sendEditHelp(sender);
                break;
            case "playback":
                sendPlaybackHelp(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "알 수 없는 도움말 항목입니다: " + args[1]);
                sender.sendMessage(ChatColor.GRAY + "사용법: /tw help edit 또는 /tw help playback");
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== TwinCraft 도움말 ===");
        sender.sendMessage(ChatColor.YELLOW + "/tw new <name>" + ChatColor.GRAY + " - 장치 생성");
        sender.sendMessage(ChatColor.YELLOW + "/tw list" + ChatColor.GRAY + " - 장치 목록");
        sender.sendMessage(ChatColor.YELLOW + "/tw view <name>" + ChatColor.GRAY + " - 장치 정보");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit ..." + ChatColor.GRAY + " - 장치 설정 변경");
        sender.sendMessage(ChatColor.YELLOW + "/tw delete <name>" + ChatColor.GRAY + " - 장치 삭제");
        sender.sendMessage(ChatColor.YELLOW + "/tw playback ..." + ChatColor.GRAY + " - Playback 제어");
        sender.sendMessage(ChatColor.DARK_GRAY + "세부 도움말: /tw help edit, /tw help playback");
    }

    private void sendEditHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== 장치 설정 명령어 ===");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit topic <name> <topic>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit type <name> <sensor|actuator>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit pos <name> <x> <y> <z>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit trigger <name> <polling|event>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit interval <name> <ms>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit action <name> <action>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit data <name> <key> <value>");
        sender.sendMessage(ChatColor.YELLOW + "/tw edit data-remove <name> <key>");
    }

    private void sendPlaybackHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Playback 명령어 ===");
        sender.sendMessage(ChatColor.YELLOW + "/tw playback <start> <end> [limit]");
        sender.sendMessage(ChatColor.YELLOW + "/tw playback stop");
        sender.sendMessage(ChatColor.YELLOW + "/tw playback next");
    }
}
