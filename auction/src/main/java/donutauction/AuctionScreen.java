package donutauction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class AuctionScreen extends Screen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int GOLD = 0xFFAA00, GREEN = 0x55FF55, WHITE = 0xFFFFFF, GRAY = 0xAAAAAA;
    private static final int ROW_H = 24;

    private int px, py, pw, ph, scroll = 0;

    public AuctionScreen() {
        super(Text.literal("Auction"));
    }

    @Override
    protected void init() {
        // Sehr großes Menü: 95% Breite, 92% Höhe
        pw = (int) (width * 0.95);
        ph = (int) (height * 0.92);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            Auction.reset();
            scroll = 0;
        }).dimensions(px + pw - 190, py + ph - 30, 85, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
            .dimensions(px + pw - 98, py + ph - 30, 85, 22).build());
    }

    private void text(DrawContext ctx, String s, int x, int y, float sc, int color) {
        var m = ctx.getMatrices();
        m.push();
        m.scale(sc, sc, 1f);
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), (int) (x / sc), (int) (y / sc), color);
        m.pop();
    }

    private int width(String s, float sc) {
        return (int) (textRenderer.getWidth(s) * sc);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px, py, px + pw, py + ph, 0xF0101018);
        ctx.drawBorder(px, py, pw, ph, 0xFFFFAA00);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        List<Map.Entry<String, Double>> ranking = Auction.ranking();
        int right = px + pw - 14;

        // Kopfzeile
        text(ctx, "AUCTION", px + 14, py + 12, 2.0f, GOLD);
        String stats = "Gesamt: " + Auction.format(Auction.total()) + "  |  Payments: " + Auction.paymentCount();
        text(ctx, stats, right - width(stats, 1.5f), py + 16, 1.5f, WHITE);

        // Höchstbietender (Banner)
        ctx.fill(px + 10, py + 44, px + pw - 10, py + 86, 0x60FFAA00);
        if (ranking.isEmpty()) {
            text(ctx, "Noch kein Gebot", px + 20, py + 56, 2.0f, GRAY);
        } else {
            Map.Entry<String, Double> top = ranking.get(0);
            text(ctx, "Höchstbietender: " + top.getKey(), px + 20, py + 56, 2.0f, GOLD);
            String amt = Auction.format(top.getValue());
            text(ctx, amt, right - width(amt, 2.0f) - 6, py + 56, 2.0f, GREEN);
        }

        int bodyTop = py + 96;
        int leftRight = px + (int) (pw * 0.60);
        int rightLeft = px + (int) (pw * 0.62);
        ctx.fill(leftRight + (rightLeft - leftRight) / 2, bodyTop, leftRight + (rightLeft - leftRight) / 2 + 1, py + ph - 40, 0x80FFAA00);

        // Rangliste (links)
        text(ctx, "Rangliste", px + 14, bodyTop, 1.5f, GOLD);
        int listTop = bodyTop + 22;
        int visible = Math.max(1, (py + ph - 40 - listTop) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ranking.size() - visible)));

        if (ranking.isEmpty()) {
            text(ctx, "Noch keine Payments erhalten.", px + 14, listTop, 1.5f, GRAY);
        }
        for (int i = 0; i < visible && i + scroll < ranking.size(); i++) {
            int rank = i + scroll;
            Map.Entry<String, Double> e = ranking.get(rank);
            int color = rank == 0 ? GOLD : rank == 1 ? 0xFFFFFF : rank == 2 ? 0xFF8855 : GRAY;
            int y = listTop + i * ROW_H;
            if (rank == 0) ctx.fill(px + 10, y - 3, leftRight, y + ROW_H - 5, 0x30FFAA00);
            text(ctx, "#" + (rank + 1) + "  " + e.getKey(), px + 14, y, 1.5f, color);
            String amt = Auction.format(e.getValue());
            text(ctx, amt, leftRight - 8 - width(amt, 1.5f), y, 1.5f, GREEN);
        }

        // Letzte Payments (rechts)
        text(ctx, "Letzte Payments", rightLeft + 6, bodyTop, 1.5f, GOLD);
        List<Auction.Entry> recent = Auction.recent();
        int rTop = bodyTop + 22;
        int rVisible = Math.max(1, (py + ph - 40 - rTop) / 14);
        for (int i = 0; i < rVisible && i < recent.size(); i++) {
            Auction.Entry en = recent.get(i);
            int y = rTop + i * 14;
            String time = TIME.format(Instant.ofEpochMilli(en.time()).atZone(ZoneId.systemDefault()));
            text(ctx, time + "  " + en.player(), rightLeft + 6, y, 1.0f, WHITE);
            String amt = Auction.format(en.amount());
            text(ctx, amt, right - width(amt, 1.0f), y, 1.0f, GREEN);
        }

        text(ctx, "Scrollen: Mausrad  |  Taste zum Schließen: Keybind oder ESC", px + 14, py + ph - 24, 1.0f, GRAY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scroll -= (int) Math.signum(vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (AuctionMod.openKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
