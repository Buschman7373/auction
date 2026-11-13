package donutauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AuctionMod implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        // Keybind (Standard: O, änderbar unter Optionen > Steuerung)
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donutauction.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.donutauction"));

        // Payments aus Server-Nachrichten erkennen -> landen direkt im Auction-Menü
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) Auction.handleMessage(message.getString());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AuctionScreen());
            }
        });
    }
}
