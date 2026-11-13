package donutauction;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AuctionMod implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        // Keybind (Standard: O, änderbar unter Optionen > Steuerung)
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.donutauction.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.donutauction"));

        // Payments aus Server-Nachrichten erkennen (System- und Spieler-Chat)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) Auction.handleMessage(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) ->
            Auction.handleMessage(message.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AuctionScreen());
            }
        });

        // /auction öffnet das Menü, /auction debug zeigt nicht erkannte Geld-Nachrichten
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("auction")
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new AuctionScreen()));
                    return 1;
                })
                .then(ClientCommandManager.literal("debug").executes(ctx -> {
                    var list = Auction.unmatched();
                    ctx.getSource().sendFeedback(Text.literal(
                        list.isEmpty() ? "Keine nicht erkannten Geld-Nachrichten." : "Nicht erkannte Nachrichten:"));
                    for (String s : list) ctx.getSource().sendFeedback(Text.literal("- " + s));
                    return 1;
                }))));
    }
}
