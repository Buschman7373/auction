package donutauction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public class AuctionScreen extends Screen {
    private static final int GOLD = 0xFFAA00, GREEN = 0x55FF55, WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, RED = 0xFF5555;
    private static final int ROW_H = 20;

    private int px, py, pw, ph, cx, scroll = 0;
    private TextFieldWidget itemField, countField, timeField;

    public AuctionScreen() {
        super(Text.literal("Auction"));
    }

    @Override
    protected void init() {
        // Panel und ALLE Inhalte liegen mittig auf dem Bildschirm
        pw = (int) (width * 0.8);
        ph = (int) (height * 0.9);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = width / 2;

        int fy = py + ph - 84;
        int x0 = cx - 215; // Eingabezeile ist 430 breit und mittig

        itemField = new TextFieldWidget(textRenderer, x0, fy, 160, 20, Text.literal("Item"));
        itemField.setMaxLength(32);
        itemField.setText(Auction.itemName());
        addDrawableChild(itemField);

        countField = new TextFieldWidget(textRenderer, x0 + 170, fy, 70, 20, Text.literal("Anzahl"));
        countField.setMaxLength(5);
        countField.setTextPredicate(s -> s.matches("\\d{0,5}"));
        countField.setText(String.valueOf(Auction.itemCount()));
        addDrawableChild(countField);

        timeField = new TextFieldWidget(textRenderer, x0 + 250, fy, 80, 20, Text.literal("Sekunden"));
        timeField.setMaxLength(6);
        timeField.setTextPredicate(s -> s.matches("\\d{0,6}"));
        timeField.setText(String.valueOf(Auction.durationSec()));
        addDrawableChild(timeField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Auction starten"), b -> {
            String item = itemField.getText().trim();
            if (item.isEmpty()) item = "Item";
            Auction.start(item, parse(countField.getText(), 1), parse(timeField.getText(), 60));
            scroll = 0;
        }).dimensions(x0 + 340, fy, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            Auction.reset();
            scroll = 0;
        }).dimensions(cx - 95, py + ph - 32, 90, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
            .dimensions(cx + 5, py + ph - 32, 90, 22).build());
    }

    private static int parse(String s, int def) {
        try {
            int v = Integer.parseInt(s.trim());
            return v > 0 ? v : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private int width(String s, float sc) {
        return (int) (textRenderer.getWidth(s) * sc);
    }

    /** Text horizontal mittig um die Bildschirmmitte. */
    private void centered(DrawContext ctx, String s, int y, float sc, int color) {
        var m = ctx.getMatrices();
        m.push();
        m.scale(sc, sc, 1f);
        int x = cx - width(s, sc) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), (int) (x / sc), (int) (y / sc), color);
        m.pop();
    }

    private static String clock(long ms) {
        long s = (ms + 999) / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
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

        Auction.State st = Auction.state();
        List<Map.Entry<String, Double>> ranking = Auction.ranking();

        centered(ctx, "AUCTION", py + 10, 2.5f, GOLD);

        // Item + Anzahl
        String what = st == Auction.State.IDLE ? "Kein Auction aktiv"
            : Auction.itemCount() + "x " + Auction.itemName();
        centered(ctx, what, py + 38, 1.5f, WHITE);

        // Timer
        String timer;
        int timerColor;
        if (st == Auction.State.RUNNING) {
            long ms = Auction.remainingMs();
            timer = clock(ms);
            timerColor = ms <= 10_000 ? RED : GREEN;
        } else if (st == Auction.State.ENDED) {
            timer = "BEENDET";
            timerColor = RED;
        } else {
            timer = "--:--";
            timerColor = GRAY;
        }
        centered(ctx, timer, py + 58, 3.0f, timerColor);

        // Höchstbietender / Gewinner
        int bannerY = py + 96;
        ctx.fill(px + 10, bannerY, px + pw - 10, bannerY + 30, 0x60FFAA00);
        if (ranking.isEmpty()) {
            centered(ctx, "Noch kein Gebot", bannerY + 8, 2.0f, GRAY);
        } else {
            Map.Entry<String, Double> top = ranking.get(0);
            String label = st == Auction.State.ENDED ? "Gewinner: " : "Höchstbietender: ";
            centered(ctx, label + top.getKey() + "  -  " + Auction.format(top.getValue()), bannerY + 8, 2.0f, GOLD);
        }

        // Rangliste (mittig)
        centered(ctx, "Rangliste  (" + Auction.paymentCount() + " Payments, gesamt " + Auction.format(Auction.total()) + ")",
            py + 134, 1.2f, GRAY);
        int listTop = py + 152;
        int listBottom = py + ph - 100;
        int visible = Math.max(1, (listBottom - listTop) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ranking.size() - visible)));

        for (int i = 0; i < visible && i + scroll < ranking.size(); i++) {
            int rank = i + scroll;
            Map.Entry<String, Double> e = ranking.get(rank);
            int color = rank == 0 ? GOLD : rank == 1 ? WHITE : rank == 2 ? 0xFF8855 : GRAY;
            String line = "#" + (rank + 1) + "  " + e.getKey() + "  -  " + Auction.format(e.getValue());
            int y = listTop + i * ROW_H;
            if (rank == 0) {
                int w = width(line, 1.5f) + 24;
                ctx.fill(cx - w / 2, y - 3, cx + w / 2, y + ROW_H - 5, 0x30FFAA00);
            }
            centered(ctx, line, y, 1.5f, color);
        }

        // Beschriftung der Eingabefelder
        int fy = py + ph - 84;
        int x0 = cx - 215;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Item"), x0, fy - 11, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Anzahl"), x0 + 170, fy - 11, GRAY);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Zeit (Sek.)"), x0 + 250, fy - 11, GRAY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scroll -= (int) Math.signum(vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Keybind schließt nur, wenn man gerade nicht in ein Textfeld tippt
        if (!(getFocused() instanceof TextFieldWidget) && AuctionMod.openKey.matchesKey(keyCode, scanCode)) {
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
