package donutauction;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Eine laufende Auktion: Item, Startgebot, Endzeit und die Summe der Zahlungen pro Spieler. */
public class Auction {
    public final String item;
    public final ItemStack icon; // darf leer sein
    public final double startBid;
    public final long endTimeMs;
    public final Map<String, Double> totals = new LinkedHashMap<>();

    public Auction(String item, ItemStack icon, double startBid, long durationMs) {
        this.item = item;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.startBid = startBid;
        this.endTimeMs = System.currentTimeMillis() + durationMs;
    }

    public void addPayment(String player, double amount) {
        totals.merge(player, amount, Double::sum);
    }

    public Map.Entry<String, Double> leader() {
        Map.Entry<String, Double> best = null;
        for (Map.Entry<String, Double> e : totals.entrySet()) {
            if (best == null || e.getValue() > best.getValue()) best = e;
        }
        return best;
    }

    /** Gewinner nur, wenn das Startgebot erreicht wurde. */
    public Map.Entry<String, Double> validLeader() {
        Map.Entry<String, Double> l = leader();
        return (l != null && l.getValue() >= startBid) ? l : null;
    }

    public long remainingMs() {
        return Math.max(0, endTimeMs - System.currentTimeMillis());
    }

    public boolean isOver() {
        return remainingMs() == 0;
    }
}
