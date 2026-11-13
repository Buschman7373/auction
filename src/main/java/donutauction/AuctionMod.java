package donutauction;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
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

    // End sound: note block "bell" (the gold block instrument) as a short rising chime.
    private static int soundTick = -1;
    private static final int[] SOUND_AT = {0, 3, 6, 10};
    private static final float[] SOUND_PITCH = {1.0f, 1.26f, 1.5f, 2.0f};

    // Group 1 = player name, group 2 = amount. This is only a guess, adjust with /auction pattern!
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
                soundTick = 0;
                Map.Entry<String, Double> w = current.validLeader();
                if (w == null) {
                    say("Auction ended: " + current.item + " - no valid bids.");
                } else {
                    say("Auction ended: " + current.item + " goes to " + w.getKey()
                            + " (" + fmt(w.getValue()) + ")");
                }
            }

            if (soundTick >= 0) {
                for (int i = 0; i < SOUND_AT.length; i++) {
                    if (SOUND_AT[i] == soundTick) playBell(client, SOUND_PITCH[i]);
                }
                soundTick++;
                if (soundTick > 12) soundTick = -1;
            }
        });

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("donutauction", "overlay"),
                (graphics, tickCounter) -> renderOverlay(graphics));

        registerCommands();
    }

    private static void playBell(Minecraft mc, float pitch) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, pitch, 1.0F));
    }

    // --------------------------------------------------------------- commands

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("auction")
                        .executes(this::cmdMenu)
                        .then(ClientCommandManager.literal("menu").executes(this::cmdMenu))
                        .then(ClientCommandManager.literal("start")
                                .then(ClientCommandManager.argument("seconds", IntegerArgumentType.integer(5))
                                        .then(ClientCommandManager.argument("startbid", StringArgumentType.word())
                                                .then(ClientCommandManager.argument("item", StringArgumentType.greedyString())
                                                        .executes(this::cmdStart)))))
                        .then(ClientCommandManager.literal("stop").executes(this::cmdStop))
                        .then(ClientCommandManager.literal("status").executes(this::cmdStatus))
                        .then(ClientCommandManager.literal("sound").executes(this::cmdSound))
                        .then(ClientCommandManager.literal("pattern")
                                .then(ClientCommandManager.argument("regex", StringArgumentType.greedyString())
                                        .executes(this::cmdPattern)))
                        .then(ClientCommandManager.literal("fakebid")
                                .then(ClientCommandManager.argument("name", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("amount", StringArgumentType.word())
                                                .executes(this::cmdFakeBid))))));
    }

    private int cmdMenu(CommandContext<FabricClientCommandSource> ctx) {
        // The chat screen closes after a command, so the menu opens on the next tick.
        openMenu = true;
        return 1;
    }

    private int cmdStart(CommandContext<FabricClientCommandSource> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        String startRaw = StringArgumentType.getString(ctx, "startbid");
        String item = StringArgumentType.getString(ctx, "item");
        Double start = parseMoney(startRaw);
        if (start == null) {
            feedback(ctx, "Invalid starting bid: " + startRaw + " (examples: 5000, 10k, 2.5m)");
            return 0;
        }
        startAuction(item, ItemStack.EMPTY, start, seconds);
        return 1;
    }

    /** Used by the command and by the menu. */
    public static void startAuction(String item, ItemStack icon, double start, int seconds) {
        current = new Auction(item, icon, start, seconds * 1000L);
        endAnnounced = false;
        soundTick = -1;
        say("Started: " + item + " | start " + fmt(start) + " | " + fmtTime(seconds * 1000L));
    }

    private int cmdStop(CommandContext<FabricClientCommandSource> ctx) {
        current = null;
        feedback(ctx, "Auction removed.");
        return 1;
    }

    private int cmdSound(CommandContext<FabricClientCommandSource> ctx) {
        soundTick = 0;
        return 1;
    }

    private int cmdStatus(CommandContext<FabricClientCommandSource> ctx) {
        if (current == null) {
            feedback(ctx, "No active auction.");
            return 0;
        }
        feedback(ctx, current.item + " | time left: " + fmtTime(current.remainingMs()));
        for (Map.Entry<String, Double> e : current.totals.entrySet()) {
            feedback(ctx, " - " + e.getKey() + ": " + fmt(e.getValue()));
        }
        return 1;
    }

    private int cmdPattern(CommandContext<FabricClientCommandSource> ctx) {
        String regex = StringArgumentType.getString(ctx, "regex");
        try {
            payPattern = Pattern.compile(regex);
            feedback(ctx, "New pattern set. (group 1 = name, group 2 = amount)");
            return 1;
        } catch (PatternSyntaxException e) {
            feedback(ctx, "Invalid regex: " + e.getDescription());
            return 0;
        }
    }

    private int cmdFakeBid(CommandContext<FabricClientCommandSource> ctx) {
        if (current == null) {
            feedback(ctx, "No active auction.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        Double amount = parseMoney(StringArgumentType.getString(ctx, "amount"));
        if (amount == null) {
            feedback(ctx, "Invalid amount.");
            return 0;
        }
        registerPayment(name, amount);
        return 1;
    }

    // ------------------------------------------------------------------- chat

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
        say("+" + fmt(amount) + " from " + player + " (total " + fmt(current.totals.get(player)) + ")");
    }

    // ---------------------------------------------------------------- overlay

    private static void renderOverlay(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.options.hideGui) return;
        Font font = mc.font;

        boolean over = current.isOver();
        long rem = current.remainingMs();

        String l1 = "AUCTION: " + current.item;
        String l2 = over ? "ENDED" : "Time left: " + fmtTime(rem);
        Map.Entry<String, Double> lead = current.leader();
        String l3;
        if (over) {
            Map.Entry<String, Double> w = current.validLeader();
            l3 = w == null ? "No valid bids" : "Winner: " + w.getKey() + " - " + fmt(w.getValue());
        } else {
            l3 = lead == null
                    ? "No bids yet (start " + fmt(current.startBid) + ")"
                    : "Leading: " + lead.getKey() + " - " + fmt(lead.getValue());
        }

        int iconW = current.icon.isEmpty() ? 0 : 22;
        int textW = Math.max(font.width(l1), Math.max(font.width(l2), font.width(l3)));
        int x = 8;
        int y = 8;
        int w = iconW + textW + 14;
        int h = 3 * 11 + 8;

        // green panel
        g.fill(x, y, x + w, y + h, 0xB00B2A12);
        g.fill(x, y, x + 2, y + h, 0xFF2ECC40);

        if (!current.icon.isEmpty()) {
            g.renderItem(current.icon, x + 6, y + (h - 16) / 2);
        }

        int tx = x + 8 + iconW;
        int ty = y + 5;
        int timeColor = over ? 0xFFFFAA00 : (rem < 10_000 ? 0xFFFFFF55 : 0xFFFFFFFF);
        g.drawString(font, l1, tx, ty, 0xFF55FF55);
        g.drawString(font, l2, tx, ty + 11, timeColor);
        g.drawString(font, l3, tx, ty + 22, 0xFF9CFF9C);
    }

    // ---------------------------------------------------------------- helpers

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Component msg = Component.literal("[Auction] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(s).withStyle(ChatFormatting.WHITE));
        mc.player.displayClientMessage(msg, false);
    }

    private static void feedback(CommandContext<FabricClientCommandSource> ctx, String s) {
        Component msg = Component.literal("[Auction] ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(s).withStyle(ChatFormatting.WHITE));
        ctx.getSource().sendFeedback(msg);
    }

    /** Understands 5000, 5,000, 10k, 2.5m, 1b. */
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
        long s = (ms + 999) / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
