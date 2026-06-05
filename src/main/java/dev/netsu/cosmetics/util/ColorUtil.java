package dev.netsu.cosmetics.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_COLOR = Pattern.compile("&([0-9a-fk-orA-FK-OR])");

    public static Component toComponent(String text) {
        if (text == null) return Component.empty();
        return MM.deserialize("<!italic>" + toMiniMessage(text));
    }

    public static List<Component> toComponents(List<String> list) {
        List<Component> result = new ArrayList<>();
        for (String s : list) result.add(toComponent(s));
        return result;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static String toMiniMessage(String text) {
        Matcher hex = HEX.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (hex.find()) {
            hex.appendReplacement(sb, "<color:#" + hex.group(1) + ">");
        }
        hex.appendTail(sb);
        text = sb.toString();

        text = text.replace("&l", "<bold>")
                   .replace("&o", "<italic>")
                   .replace("&n", "<underlined>")
                   .replace("&m", "<strikethrough>")
                   .replace("&k", "<obfuscated>")
                   .replace("&r", "<reset><!italic>");

        Matcher legacy = LEGACY_COLOR.matcher(text);
        StringBuffer sb2 = new StringBuffer();
        while (legacy.find()) {
            legacy.appendReplacement(sb2, legacyToMM(legacy.group(1).toLowerCase()));
        }
        legacy.appendTail(sb2);
        return sb2.toString();
    }

    private static String legacyToMM(String code) {
        return switch (code) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            default -> "";
        };
    }
}
