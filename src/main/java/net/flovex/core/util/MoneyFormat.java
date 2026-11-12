package net.flovex.core.util;

import java.util.Locale;

public final class MoneyFormat {
    private MoneyFormat() {}
    public static String compact(double value) {
        if (!Double.isFinite(value)) return "0";
        double absolute = Math.abs(value);
        String[] suffix = {"", "k", "m", "b", "t", "q", "qq"};
        int tier = 0;
        while (absolute >= 1000.0 && tier < suffix.length - 1) {
            absolute /= 1000.0;
            tier++;
        }
        if (tier == 0) return String.format(Locale.US, "%,.2f", value);
        return String.format(Locale.US, "%.2f%s", Math.copySign(absolute, value), suffix[tier]);
    }
}
