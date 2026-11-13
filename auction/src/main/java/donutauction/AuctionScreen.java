package donutauction;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

public class AuctionScreen extends Screen {
    private static final int ROW_H = 16;
    private int px, py, pw, ph, scroll = 0;

    public AuctionScreen() {
        super(Text.literal("Auction"));
    }

    @Override
    protected void init() {
        // Großes Menü: 80% Breite, 85% Höhe des Fensters
        pw = (int) (width * 0.8);
        ph = (int) (height * 0.85);
        px = (width - pw) / 2;
        py = (height - ph) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), b -> {
            Auction.reset();
            scroll = 0;
        }).dimensions(px + pw - 170, py + ph - 28, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
            .dimensions(px + pw - 85, py + ph - 28, 80, 20).build());
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px, py, px + pw, py + ph, 0xE0101018);
        ctx.drawBorder(px, py, pw, ph, 0xFFFFAA00);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawTextWithShadow(textRenderer, Text.literal("§6§lAuction"), px + 12, py + 12, 0xFFFFFF);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Gesamt: §a" + Auction.format(Auction.total()) + "§r  |  Payments: §e" + Auction.paymentCount()),
            px + 12, py + 28, 0xFFFFFF);
        ctx.fill(px + 8, py + 42, px + pw - 8, py + 43, 0xFFFFAA00);

        List<Map.Entry<String, Double>> ranking = Auction.ranking();
        int listTop = py + 50;
        int visible = Math.max(1, (ph - 100) / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, ranking.size() - visible)));

        if (ranking.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("§7Noch keine Payments erhalten."), px + 12, listTop, 0xFFFFFF);
            return;
        }
        for (int i = 0; i < visible && i + scroll < ranking.size(); i++) {
            int rank = i + scroll;
            Map.Entry<String, Double> e = ranking.get(rank);
            String color = rank == 0 ? "§6" : rank == 1 ? "§f" : rank == 2 ? "§c" : "§7";
            int y = listTop + i * ROW_H;
            ctx.drawTextWithShadow(textRenderer, Text.literal(color + "#" + (rank + 1) + " §r" + e.getKey()), px + 12, y, 0xFFFFFF);
            String amt = "§a" + Auction.format(e.getValue());
            ctx.drawTextWithShadow(textRenderer, Text.literal(amt), px + pw - 12 - textRenderer.getWidth(amt.replaceAll("§.", "")), y, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        scroll -= (int) Math.signum(vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Gleiche Taste schließt das Menü wieder
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
