package pl.ratrynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoBuyer {
    private static final Pattern NUM = Pattern.compile("(\\d[\\d.,\\s]*)([kKmMbB])?");
    private static final Set<Item> CONFIRM_ITEMS = Set.of(
            Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
            Items.LIME_WOOL, Items.GREEN_WOOL, Items.EMERALD_BLOCK,
            Items.LIME_CONCRETE, Items.GREEN_CONCRETE,
            Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA);

    private static long lastClick = 0;
    private static long lastCmd = 0;
    private static long screenOpenedAt = 0;
    private static Screen lastScreen = null;

    /** Co tick: otwiera rynek komenda, odswieza go i skanuje. */
    public static void tick(MinecraftClient mc) {
        Config c = Config.get();
        if (!c.enabled || mc.player == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        Screen s = mc.currentScreen;

        if (s == null) {
            lastScreen = null;
            if (!c.command.isBlank() && now - lastCmd >= c.intervalMs) {
                lastCmd = now;
                String cmd = c.command.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                mc.player.networkHandler.sendChatCommand(cmd);
            }
            return;
        }

        if (!(s instanceof HandledScreen<?>) || s instanceof AbstractInventoryScreen<?>) return;

        if (scan(mc)) return;
        // rynek otwarty a nic nie pasuje -> zamknij, zeby komenda otworzyla swiezy
        if (now - screenOpenedAt >= c.intervalMs && now - lastClick > 1500) {
            mc.player.closeHandledScreen();
        }
    }

    /** Wywolywane tez przez mixin w momencie przyjscia pakietu z zawartoscia okna (najszybciej jak sie da). */
    public static boolean scan(MinecraftClient mc) {
        Config c = Config.get();
        if (!c.enabled || mc.player == null || mc.interactionManager == null) return false;
        Screen s = mc.currentScreen;
        if (!(s instanceof HandledScreen<?> hs) || s instanceof AbstractInventoryScreen<?>) return false;

        long now = System.currentTimeMillis();
        if (s != lastScreen) { lastScreen = s; screenOpenedAt = now; }
        if (now - lastClick < 100) return false;

        ScreenHandler h = hs.getScreenHandler();
        String title = hs.getTitle().getString().toLowerCase();
        boolean confirmTitle = title.contains("potwierd") || title.contains("confirm");
        boolean recent = now - lastClick < 2000;
        int containerSlots = Math.max(0, h.slots.size() - 36);

        if (confirmTitle || recent) {
            for (int i = 0; i < containerSlots; i++) {
                ItemStack st = h.getSlot(i).getStack();
                if (!st.isEmpty() && isConfirmButton(st)) { click(mc, h, i, now); return true; }
            }
            if (confirmTitle) return false;
        }

        for (int i = 0; i < containerSlots; i++) {
            ItemStack st = h.getSlot(i).getStack();
            if (st.isEmpty()) continue;
            for (Config.Rule r : c.rules) {
                if (!matches(st, r.item)) continue;
                long p = priceOf(st);
                if (p >= 0 && p <= r.maxPrice) { click(mc, h, i, now); return true; }
            }
        }
        return false;
    }

    private static void click(MinecraftClient mc, ScreenHandler h, int slot, long now) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        lastClick = now;
    }

    private static boolean isConfirmButton(ItemStack st) {
        String n = st.getName().getString().toLowerCase();
        return CONFIRM_ITEMS.contains(st.getItem())
                || n.contains("potwierd") || n.contains("confirm") || n.contains("akcept") || n.contains("accept");
    }

    private static boolean matches(ItemStack st, String query) {
        String q = query.toLowerCase().trim();
        if (q.isEmpty()) return false;
        String full = Registries.ITEM.getId(st.getItem()).toString();
        String path = Registries.ITEM.getId(st.getItem()).getPath();
        String name = st.getName().getString().toLowerCase();
        String q2 = q.replace(' ', '_');
        return full.equals(q) || path.equals(q2) || path.contains(q2) || name.contains(q);
    }

    /** Czyta cene z lore. Zwraca -1 gdy nie znajdzie (wtedy NIE kupuje). */
    private static long priceOf(ItemStack st) {
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore == null) return -1;
        for (Text t : lore.lines()) {
            String s = t.getString();
            String l = s.toLowerCase();
            if (l.contains("cen") || l.contains("price") || l.contains("koszt") || l.contains("cost")
                    || l.contains("$") || l.contains("zł") || l.contains("zl")) {
                long v = parse(s);
                if (v >= 0) return v;
            }
        }
        return -1;
    }

    private static long parse(String s) {
        try {
            Matcher m = NUM.matcher(s);
            if (!m.find()) return -1;
            String n = m.group(1);
            String suf = m.group(2);
            if (suf != null && !suf.isEmpty()) {
                double d = Double.parseDouble(n.replace(" ", "").replace(',', '.'));
                char ch = Character.toLowerCase(suf.charAt(0));
                double mult = ch == 'k' ? 1e3 : ch == 'm' ? 1e6 : 1e9;
                return (long) (d * mult);
            }
            String digits = n.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? -1 : Long.parseLong(digits);
        } catch (Exception e) {
            return -1;
        }
    }
}
