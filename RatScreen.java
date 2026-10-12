package pl.ratrynek;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

public class RatScreen extends Screen {

    /** Czerwony przycisk z hoverem i pulsowaniem. */
    private static class RedButton extends ClickableWidget {
        private final Runnable action;
        private final BooleanSupplier glow;
        private final long born = System.currentTimeMillis();

        RedButton(int x, int y, int w, int h, Text text, BooleanSupplier glow, Runnable action) {
            super(x, y, w, h, text);
            this.action = action;
            this.glow = glow;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
            float t = (System.currentTimeMillis() - born) / 1000f;
            boolean on = glow != null && glow.getAsBoolean();
            int r = 90, g = 0, b = 0;
            if (isHovered()) { r = 150; g = 15; b = 15; }
            if (on) {
                float p = 0.5f + 0.5f * (float) Math.sin(t * 5);
                r = 150 + (int) (90 * p);
                g = (int) (30 * p);
                b = (int) (30 * p);
            }
            int a = (int) (0xE0 * this.alpha);
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            ctx.fill(x, y, x + w, y + h, argb(a, r, g, b));
            int br = isHovered() || on ? 255 : 200;
            int bc = argb((int) (0xFF * this.alpha), br, 40, 40);
            ctx.fill(x, y, x + w, y + 1, bc);
            ctx.fill(x, y + h - 1, x + w, y + h, bc);
            ctx.fill(x, y, x + 1, y + h, bc);
            ctx.fill(x + w - 1, y, x + w, y + h, bc);
            var tr = MinecraftClient.getInstance().textRenderer;
            ctx.drawCenteredTextWithShadow(tr, getMessage(), x + w / 2, y + (h - 8) / 2,
                    argb((int) (0xFF * this.alpha), 255, 255, 255));
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }
    }

    private static int argb(int a, int r, int g, int b) {
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    private static final int EMBERS = 70;
    private final float[] ex = new float[EMBERS], eoff = new float[EMBERS],
            espd = new float[EMBERS], eph = new float[EMBERS];
    private final int[] esz = new int[EMBERS], eg = new int[EMBERS];
    private final long openTime = System.currentTimeMillis();

    private TextFieldWidget itemField, priceField, cmdField, intervalField;
    private RedButton toggleBtn;
    private final List<RedButton> removeBtns = new ArrayList<>();

    public RatScreen() {
        super(Text.literal("RATRYNEK"));
        Random r = new Random(7);
        for (int i = 0; i < EMBERS; i++) {
            ex[i] = r.nextFloat();
            eoff[i] = r.nextFloat() * 1000f;
            espd[i] = 18f + r.nextFloat() * 45f;
            eph[i] = r.nextFloat() * 6.28f;
            esz[i] = 1 + r.nextInt(3);
            eg[i] = 40 + r.nextInt(110);
        }
    }

    private float time() { return (System.currentTimeMillis() - openTime) / 1000f; }

    private float appear() {
        float p = Math.min(1f, time() / 0.4f);
        return 1f - (1f - p) * (1f - p) * (1f - p);
    }

    @Override
    protected void init() {
        Config c = Config.get();
        int cx = width / 2;

        itemField = new TextFieldWidget(textRenderer, cx - 150, 40, 150, 20, Text.literal("Przedmiot"));
        itemField.setPlaceholder(Text.literal("przedmiot (np. diamond)"));
        itemField.setMaxLength(64);
        addDrawableChild(itemField);

        priceField = new TextFieldWidget(textRenderer, cx + 5, 40, 85, 20, Text.literal("Cena"));
        priceField.setPlaceholder(Text.literal("max cena"));
        priceField.setMaxLength(14);
        addDrawableChild(priceField);

        addDrawableChild(new RedButton(cx + 95, 40, 55, 20, Text.literal("+"), null, this::addRule));

        cmdField = new TextFieldWidget(textRenderer, cx - 150, 66, 100, 20, Text.literal("Komenda"));
        cmdField.setMaxLength(64);
        cmdField.setText(c.command);
        addDrawableChild(cmdField);

        intervalField = new TextFieldWidget(textRenderer, cx - 45, 66, 50, 20, Text.literal("ms"));
        intervalField.setMaxLength(5);
        intervalField.setText(String.valueOf(c.intervalMs));
        addDrawableChild(intervalField);

        toggleBtn = new RedButton(cx + 10, 66, 140, 20, toggleText(), () -> Config.get().enabled, () -> {
            Config.get().enabled = !Config.get().enabled;
            toggleBtn.setMessage(toggleText());
        });
        addDrawableChild(toggleBtn);

        setInitialFocus(itemField);
        refreshRules();
    }

    private Text toggleText() {
        return Text.literal(Config.get().enabled ? "AUTO-KUPNO: WLACZONE" : "AUTO-KUPNO: WYLACZONE");
    }

    private void addRule() {
        String it = itemField.getText().trim();
        long price;
        try { price = Long.parseLong(priceField.getText().trim().replaceAll("[^0-9]", "")); }
        catch (Exception e) { return; }
        if (it.isEmpty()) return;
        Config.Rule r = new Config.Rule();
        r.item = it;
        r.maxPrice = price;
        Config.get().rules.add(r);
        Config.save();
        itemField.setText("");
        priceField.setText("");
        setFocused(itemField);
        refreshRules();
    }

    private void refreshRules() {
        for (RedButton b : removeBtns) remove(b);
        removeBtns.clear();
        int cx = width / 2;
        List<Config.Rule> rules = Config.get().rules;
        int max = Math.max(0, (height - 125) / 22);
        for (int i = 0; i < rules.size() && i < max; i++) {
            final int idx = i;
            RedButton b = new RedButton(cx + 125, 100 + i * 22, 25, 20, Text.literal("X"), null, () -> {
                Config.get().rules.remove(idx);
                Config.save();
                refreshRules();
            });
            removeBtns.add(b);
            addDrawableChild(b);
        }
    }

    /** Tlo: czerwony gradient, unoszace sie iskry, panel z pulsujaca ramka i skanujaca linia. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = time();
        float ap = appear();
        int cx = width / 2;

        ctx.fillGradient(0, 0, width, height,
                argb((int) (0xE0 * ap), 18, 0, 0), argb((int) (0xE0 * ap), 85, 4, 4));

        // iskry
        for (int i = 0; i < EMBERS; i++) {
            float cycle = height + 20;
            float y = height - ((t * espd[i] + eoff[i]) % cycle);
            float x = ex[i] * width + (float) Math.sin(t * 1.5f + eph[i]) * 8f;
            float fl = 0.3f + 0.7f * Math.abs((float) Math.sin(t * 3f + eph[i]));
            int s = esz[i];
            ctx.fill((int) x, (int) y, (int) x + s, (int) y + s, argb((int) (255 * fl * ap), 255, eg[i], 20));
        }

        // panel
        int pw = 330, px = cx - pw / 2, top = 6, bot = height - 6;
        ctx.fill(px, top, px + pw, bot, argb((int) (0xCC * ap), 30, 0, 0));
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        int bc = argb((int) (0xFF * ap), 160 + (int) (95 * pulse), 15 + (int) (25 * pulse), 15);
        ctx.fill(px, top, px + pw, top + 2, bc);
        ctx.fill(px, bot - 2, px + pw, bot, bc);
        ctx.fill(px, top, px + 2, bot, bc);
        ctx.fill(px + pw - 2, top, px + pw, bot, bc);

        // skanujaca linia
        int ph = Math.max(1, bot - top - 4);
        int ly = top + 2 + (int) ((t * 70f) % ph);
        ctx.fill(px + 2, ly, px + pw - 2, ly + 2, argb((int) (0x45 * ap), 255, 60, 60));
        ctx.fill(px + 2, ly - 3, px + pw - 2, ly, argb((int) (0x18 * ap), 255, 60, 60));

        // paski pod reguly
        for (int i = 0; i < removeBtns.size(); i++) {
            int ry = 100 + i * 22;
            ctx.fill(cx - 152, ry, cx + 120, ry + 20, argb((int) (0x70 * ap), 80, 0, 0));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = time();
        float ap = appear();
        for (Element e : children()) {
            if (e instanceof ClickableWidget w) w.setAlpha(ap);
        }
        super.render(ctx, mouseX, mouseY, delta);

        int cx = width / 2;

        // tytul: falujace litery 2x
        String title = "RATRYNEK";
        float scale = 2f;
        float total = textRenderer.getWidth(title) * scale;
        float x0 = cx - total / 2f;
        float off = 0;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float bob = (float) Math.sin(t * 4f + i * 0.7f) * 2f;
            float k = 0.5f + 0.5f * (float) Math.sin(t * 3f + i * 0.6f);
            int col = argb((int) (255 * ap), 255, 40 + (int) (90 * k), 40 + (int) (50 * k));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x0 + off * scale, 12 + bob, 0f);
            ctx.getMatrices().scale(scale, scale, 1f);
            ctx.drawTextWithShadow(textRenderer, ch, 0, 0, col);
            ctx.getMatrices().pop();
            off += textRenderer.getWidth(ch);
        }

        List<Config.Rule> rules = Config.get().rules;
        for (int i = 0; i < removeBtns.size(); i++) {
            Config.Rule r = rules.get(i);
            ctx.drawTextWithShadow(textRenderer, r.item + "  <=  " + r.maxPrice,
                    cx - 148, 106 + i * 22, argb((int) (255 * ap), 255, 225, 225));
        }
        if (rules.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Wpisz przedmiot + cene i kliknij +"), cx, 106,
                    argb((int) (255 * ap), 220, 120, 120));
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("ESC = zamknij"), cx, height - 20, argb((int) (200 * ap), 200, 90, 90));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && (itemField.isFocused() || priceField.isFocused())) {
            addRule();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        Config c = Config.get();
        c.command = cmdField.getText().trim();
        try {
            c.intervalMs = Math.max(50, Math.min(60000, Integer.parseInt(intervalField.getText().trim())));
        } catch (Exception ignored) {}
        Config.save();
    }

    @Override
    public boolean shouldPause() { return false; }
}
