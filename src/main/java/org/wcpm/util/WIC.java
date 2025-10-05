package org.wcpm.util;

public class WIC {
    public static int bitsNeeded(int maxPos) {
        int b = 0; while ((1 << b) <= maxPos) b++; return Math.max(1, b);
    }
    public static int pack(int cid, int pos, int dlenbit) { return (cid << dlenbit) | pos; }
    public static int cid(int v, int dlenbit) { return v >>> dlenbit; }
    public static int pos(int v, int dlenbit) { return v & ((1 << dlenbit) - 1); }
}
