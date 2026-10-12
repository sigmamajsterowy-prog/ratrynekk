package pl.ratrynek;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class RatRynekClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.ratrynek.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_7, "category.ratrynek"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new RatScreen());
            }
            AutoBuyer.tick(mc);
        });
    }
}
