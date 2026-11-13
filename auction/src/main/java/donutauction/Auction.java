package donutauction;

import java.util.*;
import java.util.regex.*;

/** Speichert alle eingehenden Payments und erkennt sie aus Chat-Nachrichten. */
public class Auction {
    private static final String AMT = "\\$?(?<a>\\d[\\d,]*(?:\\.\\d+)?)\\s*(?<s>[kKmMbB]?)(?![A-Za-z])";

    // Mehrere Nachrichtenformate, damit Payments zuverlässig erkannt werden
    private static final List<Pattern> PATTERNS = List.of(
        // "Name paid you $1.5K"  /  "Name has sent you $500"
        Pattern.compile("(?<p>\\w{3,16}) (?:has )?(?:paid|payed|sent|pays|gave) you " + AMT, Pattern.CASE_INSENSITIVE),
        // "You received $1.5K from Name"
        Pattern.compile("received " + AMT + " from (?<p>\\w{3,16})", Pattern.CASE_INSENSITIVE),
        // "$1.5K from Name"
        Pattern.compile("\\$(?<a>\\d[\\d,]*(?:\\.\\d+)?)\\s*(?<s>[kKmMbB]?)(?![A-Za-z]) from (?<p>\\w{3,16})")
    );

    public record Entry(String player, double amount, long time) {}

    private static final Map<String, Double> totals = new HashMap<>();
    private static final List<Entry> log = new ArrayList<>();
    private static final LinkedList<String> debug = new LinkedList<>();
    private static String lastRaw = "";
    private static long lastTime = 0;

    public static synchronized boolean handleMessage(String raw) {
        long now = System.currentTimeMillis();
        // Doppelte Events (GAME + CHAT) nicht doppelt zählen
        if (raw.equals(lastRaw) && now - lastTime < 500) return false;
        lastRaw = raw;
        lastTime = now;

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
        // Für /auction debug: Nachrichten merken, die nach Geld aussehen, aber nicht erkannt wurden
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
    }

    public static String format(double v) {
        if (v >= 1_000_000_000) return String.format("$%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("$%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("$%.2fK", v / 1_000);
        return String.format("$%.2f", v);
    }
}
