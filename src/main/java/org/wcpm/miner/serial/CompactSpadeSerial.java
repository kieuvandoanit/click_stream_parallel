package org.wcpm.miner.serial;
import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.WCMAP;
import org.wcpm.util.Seqs;

import java.util.*;

public class CompactSpadeSerial implements PatternMiner {
    @Override public String name(){ return "Serial-Compact-SPADE"; }

    @Override public Set<List<String>> mine(CDB cdb, double minWs) {
        WCMAP wcmap = WCMAP.build(cdb);
        // F1
        List<List<String>> F1 = new ArrayList<>();
        for (String a : cdb.alphabet()) {
            var p = List.of(a);
            double ws = Seqs.weightedSupport(p, cdb);
            if (ws >= minWs) F1.add(p);
        }
        Set<List<String>> F = new LinkedHashSet<>(F1);

        // Mở rộng theo lớp (prefix-based), DFS
        dfsExtend(List.of(), F1, F, cdb, wcmap, minWs);
        return F;
    }

    private void dfsExtend(List<String> prefix, List<List<String>> patterns,
                           Set<List<String>> F, CDB cdb, WCMAP wcmap, double minWs) {
        Map<List<String>, List<List<String>>> next = new LinkedHashMap<>();
        int n = patterns.size();
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                List<String> p1 = patterns.get(i), p2 = patterns.get(j);
                String a = p1.get(p1.size() - 1), b = p2.get(p2.size() - 1);
                List<List<String>> cands = (p1.equals(p2))
                        ? List.of(append(p1, a))
                        : List.of(append(p1, b), append(p2, a));
                for (var cand : cands) {
                    if (cand.size() >= 2) {
                        String x = cand.get(cand.size() - 2), y = cand.get(cand.size() - 1);
                        if (wcmap.get(x, y) < minWs) continue; // prune
                    }
                    double ws = Seqs.weightedSupport(cand, cdb);
                    if (ws >= minWs) {
                        F.add(cand);
                        next.computeIfAbsent(cand.subList(0, cand.size() - 1), k -> new ArrayList<>()).add(cand);
                    }
                }
            }
            for (var e : next.entrySet()) {
                dfsExtend(e.getKey(), e.getValue(), F, cdb, wcmap, minWs);
            }
        }
    }

    private static List<String> append(List<String> p, String x){
        ArrayList<String> r = new ArrayList<>(p);
        r.add(x);
        return r;
    }
}