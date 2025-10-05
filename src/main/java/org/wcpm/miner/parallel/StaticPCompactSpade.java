package org.wcpm.miner.parallel;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.Phase0Builder;
import org.wcpm.util.Seqs;
import org.wcpm.util.WCMAP;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * StaticP-Compact-SPADE với Phase-0:
 *  - Phase-0 build F1 + các 1-class [a] đã đầy đủ 2-pattern.
 *  - Chia đều các 1-class cho thread; mỗi thread DFS mở rộng sâu hơn trong class của mình.
 */
public class StaticPCompactSpade implements PatternMiner {
    private final int threads;
    public StaticPCompactSpade(int threads){ this.threads=threads; }
    @Override public String name(){ return "StaticP-Compact-SPADE("+threads+")"; }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        WCMAP wcmap = WCMAP.build(cdb);
        Phase0Builder.Phase0 p0 = Phase0Builder.buildPhase0(cdb, minWs, wcmap);

        // Danh sách các 1-class (prefix) & 2-pattern tương ứng
        List<Map.Entry<List<String>, List<List<String>>>> classes =
                new ArrayList<>(p0.classes2.entrySet());

        // Chia class cho thread
        List<List<Map.Entry<List<String>, List<List<String>>>>> parts = new ArrayList<>();
        for (int i=0;i<threads;i++) parts.add(new ArrayList<>());
        for (int i=0;i<classes.size();i++) parts.get(i % threads).add(classes.get(i));

        ExecutorService es = Executors.newFixedThreadPool(threads);
        List<Future<Set<List<String>>>> futures = new ArrayList<>();

        for (var part : parts) {
            futures.add(es.submit(() -> minePartition(cdb, wcmap, minWs, part)));
        }

        // Kết quả cuối: F = F1 ∪ (patterns do các thread tìm được, bao gồm cả 2-pattern ở đầu vào)
        Set<List<String>> F = new LinkedHashSet<>(p0.F1);
        try {
            for (var f : futures) F.addAll(f.get());
        } catch(Exception e){ throw new RuntimeException(e); }
        es.shutdown();
        return F;
    }

    private Set<List<String>> minePartition(
            CDB cdb, WCMAP wcmap, double minWs,
            List<Map.Entry<List<String>, List<List<String>>>> classes
    ) {
        Set<List<String>> local = new LinkedHashSet<>();
        for (var entry : classes) {
            List<String> prefix = entry.getKey();         // [a]
            List<List<String>> patterns = entry.getValue(); // các 2-pattern (a, b)

            // Thêm luôn các 2-pattern đầu vào vào tập kết quả
            local.addAll(patterns);

            // DFS mở rộng từ class [a] với seed = danh sách 2-pattern của class
            dfsExtend(prefix, patterns, local, cdb, wcmap, minWs);
        }
        return local;
    }

    /**
     * patterns: tập (k+1)-patterns cùng prefix, ví dụ: class [a] có seed là các 2-pattern (a, b).
     */
    private void dfsExtend(List<String> prefix, List<List<String>> patterns,
                           Set<List<String>> F, CDB cdb, WCMAP wcmap, double minWs) {
        Map<List<String>, List<List<String>>> next = new LinkedHashMap<>();
        int n = patterns.size();
        for (int i=0;i<n;i++)for(int j=i;j<n;j++){
            var p1 = patterns.get(i); var p2 = patterns.get(j);
            String a = p1.get(p1.size()-1), b = p2.get(p2.size()-1);
            var cands = (p1.equals(p2))
                    ? List.of(append(p1,a))
                    : List.of(append(p1,b), append(p2,a));
            for (var cand : cands) {
                if (cand.size()>=2) {
                    String x=cand.get(cand.size()-2), y=cand.get(cand.size()-1);
                    if (wcmap.get(x,y) < minWs) continue; // prune bởi WCMAP
                }
                double ws = Seqs.weightedSupport(cand, cdb);
                if (ws >= minWs) {
                    F.add(cand);
                    next.computeIfAbsent(cand.subList(0, cand.size()-1), k->new ArrayList<>()).add(cand);
                }
            }
        }
        for (var e: next.entrySet())
            dfsExtend(e.getKey(), e.getValue(), F, cdb, wcmap, minWs);
    }

    private static List<String> append(List<String> p, String x){
        var r=new ArrayList<String>(p); r.add(x); return r;
    }
}
