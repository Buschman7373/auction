package donutauction;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AuctionMod implements ClientModInitializer {

    private static Auction current;
    private static boolean endAnnounced;
    private static boolean openMenu;

    // Gruppe 1 = Spielername, Gruppe 2 = Betrag. Nur geraten, mit /auction pattern anpassen!
    private static Pattern payPattern =
            Pattern.compile("(\\w{3,16}) paid you \\$?([\\d.,]+\\s?[kKmMbB]?)");

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handleChat(message.getString());
        });
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) ->
                handleChat(message.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openMenu) {
                openMenu = false;
                client.setScreen(new AuctionScreen());
            }
            if (current != null && current.isOver() && !endAnnounced) {
                endAnnounced = true;
                Map.Entry<String, Double> w = current.validLeader();
                if (w == null) {
                    say("Auktion beendet: " + current.item + " - kein gueltiges Gebot.");
                } else {
                    say("Auktion beendet: " + current.item + " geht an " + w.getKey()
                            + " (" + fmt(w.getValue()) + ")");
                }
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("donutauction", "overlay"),
                (graphics, tickCounter) -> renderOverlay(graphics));

        registerCommands();
    }

    // ---------------------------------------------------------------- Befehle

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("auction")
                        .executes(this::cmdMenu)
                        .then(ClientCommandManager.literal("menu").executes(this::cmdMenu))
                        .then(ClientCommandManager.literal("start")
                                .then(ClientCommandManager.argument("minutes", DoubleArgumentType.doubleArg(0.1))
                                        .then(ClientCommandManager.argument("startbid", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                                        .executes(this::cmdStart)))))
                        .then(ClientCommandManager.literal("stop").executes(this::cmdStop))
                        .then(ClientCommandManager.literal("status").executes(this::cmdStatus))
                        .then(ClientCommandManager.literal("pattern")
                                .then(ClientCommandManager.argument("regex", StringArgumentType.greedyString())
                                        .executes(this::cmdPattern)))
                        .then(ClientCommandManager.literal("fakebid")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                                                .executes(this::cmdFakeBid))))));
    }

    private int cmdStart(CommandContext<FabricClientCommandSource> ctx) {
        double minutes = DoubleArgumentType.getDouble(ctx, "minutes");
        String startRaw = StringArgumentType.getString(ctx, "startbid");
        String item = StringArgumentType.getString(ctx, "item");
        Double start = parseMoney(startRaw);
        if (start == null) {
            feedback(ctx, "Ungueltiges Startgebot: " + startRaw + " (Beispiele: 5000, 10k, 2.5m)");
            return 0;
        }
        startAuction(item, ItemStack.EMPTY, start, minutes);
        return 1;
    }

    private int cmdMenu(CommandContext<FabricClientCommandSource> ctx) {
        // Das Chatfenster schliesst sich nach dem Befehl, daher oeffnen wir das Menue im naechsten Tick.
        openMenu = true;
        return 1;
    }

    /** Wird vom Befehl und vom Menue benutzt. */
    public static void startAuction(String item, ItemStack icon, double start, double minutes) {
        current = new Auction(item, icon, start, (long) (minutes * 60_000));
        endAnnounced = false;
        say("Gestartet: " + item + " | Start " + fmt(start) + " | " + minutes + " Min.");
    }

    private int cmdStop(CommandContext<FabricClientCommandSource> ctx) {
        current = null;
        feedback(ctx, "Auktion entfernt.");
        return 1;
    }

    private int cmdStatus(CommandContext<FabricClientCommandSource> ctx) {
        if (current == null) {
            feedback(ctx, "Keine Auktion aktiv.");
            return 0;
        }
        feedback(ctx, current.item + " | Zeit: " + fmtTime(current.remainingMs()));
        for (Map.Entry<String, Double> e : current.totals.entrySet()) {
            feedback(ctx, " - " + e.getKey() + ": " + fmt(e.getValue()));
        }
        return 1;
    }

    private int cmdPattern(CommandContext<FabricClientCommandSource> ctx) {
        String regex = StringArgumentType.getString(ctx, "regex");
        try {
            payPattern = Pattern.compile(regex);
            feedback(ctx, "Neues Muster gesetzt. (Gruppe 1 = Name, Gruppe 2 = Betrag)");
            return 1;
        } catch (PatternSyntaxException e) {
            feedback(ctx, "Ungueltiges Regex: " + e.getDescription());
            return 0;
        }
    }

    private int cmdFakeBid(CommandContext<FabricClientCommandSource> ctx) {
        if (current == null) {
            feedback(ctx, "Keine Auktion aktiv.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        Double amount = parseMoney(StringArgumentType.getString(ctx, "amount"));
        if (amount == null) {
            feedback(ctx, "Ungueltiger Betrag.");
            return 0;
        }
        registerPayment(name, amount);
        return 1;
    }

    // ---------------------------------------------------------------- Chat

    private static void handleChat(String raw) {
        if (current == null || current.isOver()) return;
        Matcher m = payPattern.matcher(raw);
        if (!m.find()) return;
        Double amount = parseMoney(m.group(2));
        if (amount == null) return;
        registerPayment(m.group(1), amount);
    }

    private static void registerPayment(String player, double amount) {
        current.addPayment(player, amount);
        say("+" + fmt(amount) + " von " + player + " (gesamt " + fmt(current.totals.get(player)) + ")");
    }

    // ---------------------------------------------------------------- Overlay

    private static void renderOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.options.hideGui) return;
        Font font = mc.font;
        int x = 6, y = 6;

        if (!current.icon.isEmpty()) {
            g.renderItem(current.icon, x, y - 3);
            x += 20;
        }
        g.drawString(font, "Auktion: " + current.item, x, y, 0xFFFFFF55);
        y += 11;

        String time = current.isOver() ? "BEENDET" : "Zeit: " + fmtTime(current.remainingMs());
        g.drawString(font, time, x, y, current.isOver() ? 0xFFFF5555 : 0xFFFFFFFF);
        y += 11;

        Map.Entry<String, Double> l = current.leader();
        String top = (l == null)
                ? "Noch keine Gebote (Start " + fmt(current.startBid) + ")"
                : "Vorne: " + l.getKey() + " - " + fmt(l.getValue());
        g.drawString(font, top, x, y, 0xFF55FF55);
    }

    // ---------------------------------------------------------------- Helfer

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Auktion] " + s), false);
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String s) {
        ctx.getSource().sendFeedback(Component.literal("[Auktion] " + s));
    }

    /** Versteht 5000, 5,000, 10k, 2.5m, 1b. */
    static Double parseMoney(String raw) {
        try {
            String s = raw.trim().replace(",", "").toLowerCase(Locale.ROOT);
            double mult = 1;
            if (s.endsWith("k")) { mult = 1e3; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("m")) { mult = 1e6; s = s.substring(0, s.length() - 1); }
            else if (s.endsWith("b")) { mult = 1e9; s = s.substring(0, s.length() - 1); }
            return Double.parseDouble(s.trim()) * mult;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String fmt(double v) {
        if (v >= 1e9) return String.format(Locale.ROOT, "$%.2fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.ROOT, "$%.2fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.ROOT, "$%.1fK", v / 1e3);
        return String.format(Locale.ROOT, "$%.0f", v);
    }

    static String fmtTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
