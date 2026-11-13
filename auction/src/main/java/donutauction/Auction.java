package donutauction;

import java.util.*;
import java.util.regex.*;

/** Auction-Runde (Item, Anzahl, Timer) + eingehende Payments. */
public class Auction {
    public enum State { IDLE, RUNNING, ENDED }

    private static final String AMT = "\\$?(?<a>\\d[\\d,]*(?:\\.\\d+)?)\\s*(?<s>[kKmMbB]?)(?![A-Za-z])";

    private static final List<Pattern> PATTERNS = List.of(
        Pattern.compile("(?<p>\\w{3,16}) (?:has )?(?:paid|payed|sent|pays|gave) you " + AMT, Pattern.CASE_INSENSITIVE),
        Pattern.compile("received " + AMT + " from (?<p>\\w{3,16})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\$(?<a>\\d[\\d,]*(?:\\.\\d+)?)\\s*(?<s>[kKmMbB]?)(?![A-Za-z]) from (?<p>\\w{3,16})")
    );

    public record Entry(String player, double amount, long time) {}

    private static final Map<String, Double> totals = new HashMap<>();
    private static final List<Entry> log = new ArrayList<>();
    private static final LinkedList<String> debug = new LinkedList<>();
    private static String lastRaw = "";
    private static long lastTime = 0;

    private static State state = State.IDLE;
    private static String itemName = "";
    private static int itemCount = 1;
    private static int durationSec = 60;
    private static long endTime = 0;

    // ---- Runde ----
    public static synchronized void start(String item, int count, int seconds) {
        totals.clear();
        log.clear();
        debug.clear();
        itemName = item;
        itemCount = Math.max(1, count);
        durationSec = Math.max(1, seconds);
        endTime = System.currentTimeMillis() + durationSec * 1000L;
        state = State.RUNNING;
    }

    public static synchronized long remainingMs() {
        return state == State.RUNNING ? Math.max(0, endTime - System.currentTimeMillis()) : 0;
    }

    /** Pro Tick aufrufen. Gibt eine Nachricht zurück, sobald der Timer abgelaufen ist. */
    public static synchronized String tick() {
        if (state == State.RUNNING && System.currentTimeMillis() >= endTime) {
            state = State.ENDED;
            var r = ranking();
            String what = itemCount + "x " + itemName;
            if (r.isEmpty()) return "[Auction] " + what + ": Kein Gebot.";
            return "[Auction] " + what + " geht an " + r.get(0).getKey() + " für " + format(r.get(0).getValue()) + "!";
        }
        return null;
    }

    public static synchronized State state() { return state; }
    public static synchronized String itemName() { return itemName; }
    public static synchronized int itemCount() { return itemCount; }
    public static synchronized int durationSec() { return durationSec; }

    // ---- Payments ----
    public static synchronized boolean handleMessage(String raw) {
        long now = System.currentTimeMillis();
        if (raw.equals(lastRaw) && now - lastTime < 500) return false;
        lastRaw = raw;
        lastTime = now;
        if (state == State.ENDED) return false; // nach Ablauf zählt nichts mehr

        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(raw);
            if (!m.find()) continue;
            double amount;
            try {
                amount = Double.parseDouble(m.group("a").replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            switch (m.group("s").toLowerCase()) {
                case "k" -> amount *= 1_000;
                case "m" -> amount *= 1_000_000;
                case "b" -> amount *= 1_000_000_000;
                default -> {}
            }
            String player = m.group("p");
            totals.merge(player, amount, Double::sum);
            log.add(new Entry(player, amount, now));
            return true;
        }
        String low = raw.toLowerCase();
        if (raw.contains("$") || low.contains("paid") || low.contains("pay")) {
            debug.addFirst(raw);
            while (debug.size() > 8) debug.removeLast();
        }
        return false;
    }

    public static synchronized List<Map.Entry<String, Double>> ranking() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(totals.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public static synchronized List<Entry> recent() {
        List<Entry> copy = new ArrayList<>(log);
        Collections.reverse(copy);
        return copy;
    }

    public static synchronized List<String> unmatched() { return new ArrayList<>(debug); }

    public static synchronized double total() {
        return totals.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public static synchronized int paymentCount() { return log.size(); }

    public static synchronized void reset() {
        totals.clear();
        log.clear();
        debug.clear();
        state = State.IDLE;
    }

    public static String format(double v) {
        if (v >= 1_000_000_000) return String.format("$%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("$%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("$%.2fK", v / 1_000);
        return String.format("$%.2f", v);
    }
}
