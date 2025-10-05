package org.wcpm.util;

import org.wcpm.model.CDB;

import java.util.*;

/**
 * Phase-0: (1) tính F1, (2) sinh toàn bộ 2-pattern từ [£] theo §4.2, (3) build 1-class [a] đã “đầy” (chứa (a,b), (a,c), ...).
 */
public class Phase0Builder {

    public static class Phase0 {
        public final List<List<String>> F1; // các 1-pattern frequent
        public final Map<List<String>, List<List<String>>> classes2; // [a] -> danh sách 2-pattern (a, b)

        public Phase0(List<List<String>> f1,
                      Map<List<String>, List<List<String>>> classes2) {
            this.F1 = f1;
            this.classes2 = classes2;
        }
    }

    public static Phase0 buildPhase0(CDB cdb, double minWs, WCMAP wcmap) {
        // 1) F1
        List<List<String>> F1 = new ArrayList<>();
        for (String a : cdb.alphabet()) {
            var p = List.of(a);
            double ws = Seqs.weightedSupport(p, cdb);
            if (ws >= minWs) F1.add(p);
        }

        // 2) Sinh tất cả 2-pattern từ F1 theo §4.2 (p1 != p2 → (a,b) & (b,a); p1 == p2 → (a,a))
        //    Prune bằng WCMAP tail & kiểm tra ws.
        Set<List<String>> F2 = new LinkedHashSet<>();
        int n = F1.size();
        for (int i = 0; i < n; i++) {
            String a = F1.get(i).get(0);
            for (int j = i; j < n; j++) {
                String b = F1.get(j).get(0);

                List<List<String>> cands;
                if (i == j) {
                    cands = List.of(List.of(a, a));
                } else {
                    cands = List.of(List.of(a, b), List.of(b, a));
                }

                for (var cand : cands) {
                    // prune bằng WCMAP tail (x,y)
                    String x = cand.get(cand.size() - 2);
                    String y = cand.get(cand.size() - 1);
                    if (wcmap.get(x, y) < minWs) continue;

                    double ws = Seqs.weightedSupport(cand, cdb);
                    if (ws >= minWs) F2.add(cand);
                }
            }
        }

        // 3) Build 1-class [a] đã “đầy” với tất cả (a, b)
        Map<List<String>, List<List<String>>> classes2 = new LinkedHashMap<>();
        for (var p2 : F2) {
            String a = p2.get(0);
            classes2.computeIfAbsent(List.of(a), k -> new ArrayList<>()).add(p2);
        }

        // Đảm bảo mọi [a] có key (kể cả khi rỗng) để phân tải nhất quán
        for (var f1 : F1) classes2.computeIfAbsent(f1, k -> new ArrayList<>());

        // Sắp xếp một chút cho ổn định
        for (var e : classes2.values()) {
            e.sort(Comparator.comparing((List<String> p) -> p.get(0))
                    .thenComparing(p -> p.get(1)));
        }
        return new Phase0(F1, classes2);
    }
}
