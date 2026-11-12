package donutauction;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Menue zum Erstellen einer Auktion: Item aus dem Inventar waehlen oder Namen tippen. */
public class AuctionScreen extends Screen {
    private static final int SLOT = 18;

    private int gx;
    private int top;
    private int fieldY;

    private int selectedSlot = -1;
    private ItemStack selectedStack = ItemStack.EMPTY;

    private EditBox itemField;
    private EditBox minutesField;
    private EditBox bidField;
    private String error = "";

    public AuctionScreen() {
        super(Component.literal("Auktion erstellen"));
    }

    private int slotX(int slot) {
        return slot < 9 ? gx + slot * SLOT : gx + ((slot - 9) % 9) * SLOT;
    }

    private int slotY(int slot) {
        return slot < 9 ? top + 3 * SLOT + 4 : top + ((slot - 9) / 9) * SLOT;
    }

    @Override
    protected void init() {
        int gridW = 9 * SLOT;
        gx = width / 2 - gridW / 2;
        top = 36;
        fieldY = top + 3 * SLOT + 4 + SLOT + 16;

        for (int slot = 0; slot < 36; slot++) {
            final int s = slot;
            addRenderableWidget(Button.builder(Component.empty(), b -> select(s))
                    .bounds(slotX(slot), slotY(slot), SLOT, SLOT)
                    .build());
        }

        itemField = new EditBox(font, gx, fieldY, gridW, 18, Component.literal("Item"));
        itemField.setMaxLength(64);
        itemField.setHint(Component.literal("Item waehlen oder Namen tippen"));
        addRenderableWidget(itemField);

        minutesField = new EditBox(font, gx, fieldY + 32, 70, 18, Component.literal("Minuten"));
        minutesField.setMaxLength(8);
        minutesField.setValue("10");
        addRenderableWidget(minutesField);

        bidField = new EditBox(font, gx + 80, fieldY + 32, gridW - 80, 18, Component.literal("Startgebot"));
        bidField.setMaxLength(16);
        bidField.setValue("1k");
        addRenderableWidget(bidField);

        addRenderableWidget(Button.builder(Component.literal("Starten"), b -> start())
                .bounds(gx, fieldY + 58, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Abbrechen"), b -> onClose())
                .bounds(gx + 86, fieldY + 58, 76, 20).build());
    }

    private void select(int slot) {
        if (minecraft == null || minecraft.player == null) return;
        ItemStack stack = minecraft.player.getInventory().getItem(slot);
        if (stack.isEmpty()) return;
        selectedSlot = slot;
        selectedStack = stack.copy();
        itemField.setValue(stack.getHoverName().getString());
        error = "";
    }

    private void start() {
        String item = itemField.getValue().trim();
        if (item.isEmpty()) {
            error = "Bitte ein Item waehlen oder einen Namen eingeben.";
            return;
        }
        Double bid = AuctionMod.parseMoney(bidField.getValue());
        if (bid == null) {
            error = "Ungueltiges Startgebot (z.B. 5000, 10k, 2.5m).";
            return;
        }
        double minutes;
        try {
            minutes = Double.parseDouble(minutesField.getValue().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            error = "Ungueltige Minutenzahl.";
            return;
        }
        if (minutes < 0.1) {
            error = "Mindestens 0.1 Minuten.";
            return;
        }
        AuctionMod.startAuction(item, selectedStack, bid, minutes);
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (minecraft == null || minecraft.player == null) return;

        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);

        ItemStack hovered = ItemStack.EMPTY;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack s = minecraft.player.getInventory().getItem(slot);
            int x = slotX(slot);
            int y = slotY(slot);
            if (slot == selectedSlot) {
                g.fill(x, y, x + SLOT, y + SLOT, 0x9955FF55);
            }
            if (!s.isEmpty()) {
                g.renderItem(s, x + 1, y + 1);
                g.renderItemDecorations(font, s, x + 1, y + 1);
                if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                    hovered = s;
                }
            }
        }

        String label = hovered.isEmpty()
                ? "Item aus dem Inventar waehlen:"
                : hovered.getHoverName().getString();
        g.drawString(font, label, gx, top - 11, 0xFFAAAAAA);

        g.drawString(font, "Item-Name:", gx, fieldY - 11, 0xFFAAAAAA);
        g.drawString(font, "Minuten:", gx, fieldY + 21, 0xFFAAAAAA);
        g.drawString(font, "Startgebot:", gx + 80, fieldY + 21, 0xFFAAAAAA);

        if (!error.isEmpty()) {
            g.drawString(font, error, gx, fieldY + 84, 0xFFFF5555);
        }
    }
}
