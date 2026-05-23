package com.createnuclearindustrys;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.IdentityHashMap;
import java.util.Map;

public class ClientTooltipHandler {

    private static final Map<Item, String> KEYS = new IdentityHashMap<>();

    public static void register(Item item, String baseKey) {
        KEYS.put(item, baseKey);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        String base = KEYS.get(event.getItemStack().getItem());
        if (base == null) return;

        if (Screen.hasShiftDown()) {
            event.getToolTip().add(parse(base + ".tooltip.summary"));
            for (int i = 1; i <= 4; i++) {
                String condKey = base + ".tooltip.condition" + i;
                String behKey  = base + ".tooltip.behaviour" + i;
                if (!I18n.exists(condKey)) break;
                event.getToolTip().add(Component.empty());
                event.getToolTip().add(Component.translatable(condKey)
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                event.getToolTip().add(parse(behKey));
            }
        } else {
            event.getToolTip().add(Component.literal("Hold ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal("[SHIFT]").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" to read more").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** Parses _highlighted_ segments: odd segments (inside underscores) → WHITE, even → GRAY */
    private static MutableComponent parse(String key) {
        String text = I18n.get(key);
        MutableComponent result = Component.empty();
        String[] parts = text.split("_", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            result = result.append(Component.literal(parts[i])
                    .withStyle(i % 2 == 1 ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        return result;
    }
}
