package donutauction;

import java.util.*;
import java.util.regex.*;

/** Speichert alle eingehenden Payments und erkennt sie aus Chat-Nachrichten. */
public class Auction {
    // Passt z.B. auf: "Name paid you $1.5K." / "Name has paid you $500"
    private static final Pattern PAY = Pattern.compile(
        "(\\w{3,16}) (?:has )?(?:paid|payed|sent) you \\$?(\\d[\\d,]*(?:\\.\\d+)?)\\s*([kKmMbB]?)");

    public record Entry(String player, double amount, long time) {}

    private static final Map<String, Double> totals = new HashMap<>();
    private static final List<Entry> log = new ArrayList<>();

    public static synchronized boolean handleMessage(String raw) {
        Matcher m = PAY.matcher(raw);
        if (!m.find()) return false;
        double amount;
        try {
            amount = Double.parseDouble(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return false;
        }
        switch (m.group(3).toLowerCase()) {
            case "k" -> amount *= 1_000;
            case "m" -> amount *= 1_000_000;
            case "b" -> amount *= 1_000_000_000;
            default -> {}
        }
        String player = m.group(1);
        totals.merge(player, amount, Double::sum);
        log.add(new Entry(player, amount, System.currentTimeMillis()));
        return true;
    }

    public static synchronized List<Map.Entry<String, Double>> ranking() {
        List<Map.Entry<String, Double>> list = new ArrayList<>(totals.entrySet());
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return list;
    }

    public static synchronized double total() {
        return totals.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public static synchronized int paymentCount() { return log.size(); }

    public static synchronized void reset() {
        totals.clear();
        log.clear();
    }

    public static String format(double v) {
        if (v >= 1_000_000_000) return String.format("$%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000) return String.format("$%.2fM", v / 1_000_000);
        if (v >= 1_000) return String.format("$%.2fK", v / 1_000);
        return String.format("$%.2f", v);
    }
}
