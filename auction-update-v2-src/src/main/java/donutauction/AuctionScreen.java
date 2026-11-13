package donutauction;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Green auction menu: search ANY item in the game, pick a duration, set a starting bid. */
public class AuctionScreen extends Screen {
    private static final int SLOT = 18;
    private static final int COLS = 9;
    private static final int ROWS = 4;
    private static final int PER_PAGE = COLS * ROWS;

    private static final int[] PRESETS = {15, 30, 45, 60};
    private static final String[] PRESET_LABELS = {"15s", "30s", "45s", "1min"};

    private static final int PANEL_BG = 0xEE0B2A12;
    private static final int BORDER = 0xFF2ECC40;
    private static final int GREEN = 0xFF55FF55;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int WHITE = 0xFFFFFFFF;

    /** One searchable item. */
    private static class Entry {
        final ItemStack stack;
        final String name;
        final String searchText;

        Entry(ItemStack stack, String name, String id) {
            this.stack = stack;
            this.name = name;
            this.searchText = (name + " " + id.replace('_', ' ')).toLowerCase(Locale.ROOT);
        }
    }

    private static List<Entry> ALL;

    private static List<Entry> all() {
        if (ALL == null) {
            List<Entry> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ItemStack st = item.getDefaultInstance();
                if (st.isEmpty()) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).getPath();
                list.add(new Entry(st, st.getHoverName().getString(), id));
            }
            ALL = list;
        }
        return ALL;
    }

    private final List<Entry> filtered = new ArrayList<>();
    private final List<Button> presetButtons = new ArrayList<>();
    private String query = "";
    private int page = 0;
    private Entry selected;
    private int durationSec = 60;
    private String error = "";

    private int leftX;
    private int rightX;

    private EditBox searchBox;
    private EditBox bidBox;

    public AuctionScreen() {
        super(Component.literal("Auction Creator"));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        presetButtons.clear();
        int gridW = COLS * SLOT;
        int rightW = 150;
        leftX = width / 2 - (gridW + 14 + rightW) / 2;
        rightX = leftX + gridW + 14;

        // search bar
        searchBox = new EditBox(font, leftX, 36, gridW, 18, Component.literal("Search"));
        searchBox.setMaxLength(40);
        searchBox.setHint(Component.literal("Search item..."));
        searchBox.setValue(query);
        searchBox.setResponder(text -> {
            query = text;
            page = 0;
            refilter();
        });
        addRenderableWidget(searchBox);
        setInitialFocus(searchBox);

        // item grid (invisible click targets, icons are drawn in render)
        for (int i = 0; i < PER_PAGE; i++) {
            final int index = i;
            int x = leftX + (i % COLS) * SLOT;
            int y = 58 + (i / COLS) * SLOT;
            addRenderableWidget(Button.builder(Component.empty(), b -> pick(index))
                    .bounds(x, y, SLOT, SLOT).build());
        }

        // pager
        addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(leftX, 134, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(leftX + gridW - 40, 134, 40, 18).build());

        // duration presets: 15s / 30s / 45s / 1min
        for (int i = 0; i < PRESETS.length; i++) {
            final int seconds = PRESETS[i];
            Button b = Button.builder(Component.literal(PRESET_LABELS[i]), btn -> setDuration(seconds))
                    .bounds(rightX + i * 38, 82, 34, 18).build();
            presetButtons.add(b);
            addRenderableWidget(b);
        }

        // duration stepper: -15s / +15s
        addRenderableWidget(Button.builder(Component.literal("-15s"), b -> setDuration(durationSec - 15))
                .bounds(rightX, 104, 40, 18).build());
        addRenderableWidget(Button.builder(Component.literal("+15s"), b -> setDuration(durationSec + 15))
                .bounds(rightX + rightW - 40, 104, 40, 18).build());

        // starting bid
        bidBox = new EditBox(font, rightX, 136, rightW, 18, Component.literal("Starting bid"));
        bidBox.setMaxLength(16);
        bidBox.setValue("1k");
        addRenderableWidget(bidBox);

        // start / cancel
        addRenderableWidget(Button.builder(
                        Component.literal("Start").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                        b -> start())
                .bounds(rightX, 160, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(rightX + 78, 160, 72, 20).build());

        refilter();
        refreshPresetLabels();
    }

    // ---------------------------------------------------------------- actions

    private void refilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Entry e : all()) {
            if (q.isEmpty() || e.searchText.contains(q)) filtered.add(e);
        }
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    private int pageCount() {
        return Math.max(1, (filtered.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(page + delta, pageCount() - 1));
    }

    private void pick(int slotIndex) {
        int idx = page * PER_PAGE + slotIndex;
        if (idx < filtered.size()) {
            selected = filtered.get(idx);
            error = "";
        }
    }

    private void setDuration(int seconds) {
        durationSec = Math.max(15, Math.min(seconds, 600));
        refreshPresetLabels();
    }

    private void refreshPresetLabels() {
        for (int i = 0; i < presetButtons.size(); i++) {
            ChatFormatting color = PRESETS[i] == durationSec ? ChatFormatting.GREEN : ChatFormatting.WHITE;
            presetButtons.get(i).setMessage(Component.literal(PRESET_LABELS[i]).withStyle(color));
        }
    }

    private void start() {
        if (selected == null) {
            error = "Pick an item first.";
            return;
        }
        Double bid = AuctionMod.parseMoney(bidBox.getValue());
        if (bid == null) {
            error = "Invalid starting bid (e.g. 5000, 10k, 2.5m).";
            return;
        }
        AuctionMod.startAuction(selected.name, selected.stack.copy(), bid, durationSec);
        onClose();
    }

    // -------------------------------------------------------------- rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int x1 = leftX - 10;
        int x2 = rightX + 150 + 10;
        int y1 = 4;
        int y2 = 192;
        g.fill(x1, y1, x2, y2, PANEL_BG);
        g.fill(x1, y1, x2, y1 + 1, BORDER);
        g.fill(x1, y2 - 1, x2, y2, BORDER);
        g.fill(x1, y1, x1 + 1, y2, BORDER);
        g.fill(x2 - 1, y1, x2, y2, BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font,
                Component.literal("AUCTION CREATOR").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                width / 2, 10, WHITE);

        // item icons on the grid
        Entry hovered = null;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= filtered.size()) break;
            Entry e = filtered.get(idx);
            int x = leftX + (i % COLS) * SLOT;
            int y = 58 + (i / COLS) * SLOT;
            boolean isHover = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
            if (e == selected) {
                g.fill(x, y, x + SLOT, y + SLOT, 0x9955FF55);
            } else if (isHover) {
                g.fill(x, y, x + SLOT, y + SLOT, 0x4455FF55);
            }
            g.renderItem(e.stack, x + 1, y + 1);
            if (isHover) hovered = e;
        }

        // hint / hovered name
        String hint = hovered != null ? hovered.name : "Click an item to select it";
        g.drawString(font, hint, leftX, 24, hovered != null ? GREEN : GRAY);

        // pager text
        g.drawCenteredString(font,
                Component.literal("Page " + (page + 1) + "/" + pageCount() + "  (" + filtered.size() + " items)"),
                leftX + COLS * SLOT / 2, 139, WHITE);

        // right column
        g.drawString(font, "Selected:", rightX, 36, GREEN);
        if (selected != null) {
            g.renderItem(selected.stack, rightX, 46);
            String name = selected.name.length() > 20 ? selected.name.substring(0, 19) + "..." : selected.name;
            g.drawString(font, name, rightX + 22, 50, WHITE);
        } else {
            g.drawString(font, "None", rightX + 2, 50, GRAY);
        }

        g.drawString(font, "Duration:", rightX, 72, GREEN);
        g.drawCenteredString(font, Component.literal(AuctionMod.fmtTime(durationSec * 1000L)),
                rightX + 75, 109, WHITE);

        g.drawString(font, "Starting bid:", rightX, 126, GREEN);

        if (!error.isEmpty()) {
            g.drawString(font, error, leftX, 180, 0xFFFF5555);
        }
    }
}
