package org.wcpm.miner.parallel;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.Phase0Builder;
import org.wcpm.util.Seqs;
import org.wcpm.util.WCMAP;

import java.util.*;
import java.util.concurrent.*;

/**
 * HP-Compact-SPADE với Phase-0:
 *  - Hàng đợi task là các 1-class [a] đã “đầy” 2-pattern.
 *  - Worker lấy một class, thêm seed 2-pattern vào F, rồi DFS mở rộng sâu hơn trong class đó.
 */
public class HPCompactSpade implements PatternMiner {
    private final int threads;
    public HPCompactSpade(int threads){ this.threads=threads; }
    @Override public String name(){ return "HP-Compact-SPADE("+threads+")"; }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        WCMAP wcmap = WCMAP.build(cdb);
        Phase0Builder.Phase0 p0 = Phase0Builder.buildPhase0(cdb, minWs, wcmap);

        // Task queue: mỗi task = một entry (prefix=[a], patterns=2-pattern trong class)
        BlockingQueue<Map.Entry<List<String>, List<List<String>>>> tasks = new LinkedBlockingQueue<>();
        for (var e : p0.classes2.entrySet()) tasks.add(e);

        ConcurrentLinkedQueue<Set<List<String>>> results = new ConcurrentLinkedQueue<>();
        ExecutorService es = Executors.newFixedThreadPool(threads);

        Runnable worker = () -> {
            try {
                Set<List<String>> local = new LinkedHashSet<>();
                while (true) {
                    var entry = tasks.poll(200, TimeUnit.MILLISECONDS);
                    if (entry == null) break; // hết việc
                    List<String> prefix = entry.getKey();
                    List<List<String>> seed = entry.getValue(); // 2-pattern

                    // thêm seed vào F
                    local.addAll(seed);

                    // DFS mở rộng sâu hơn
                    dfsExtend(prefix, seed, local, cdb, wcmap, minWs);
                }
                results.add(local);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        for (int i=0;i<threads;i++) es.submit(worker);
        es.shutdown();
        try { es.awaitTermination(365, TimeUnit.DAYS); } catch (InterruptedException e) { throw new RuntimeException(e); }

        // Kết quả: F1 ∪ (kết quả từ các worker)
        Set<List<String>> F = new LinkedHashSet<>(p0.F1);
        for (var r: results) F.addAll(r);
        return F;
    }

    private void dfsExtend(List<String> prefix, List<List<String>> patterns,
                           Set<List<String>> F, CDB cdb, WCMAP wcmap, double minWs) {
        Map<List<String>, List<List<String>>> next = new LinkedHashMap<>();
        int n = patterns.size();
        for (int i=0;i<n;i++)for(int j=i;j<n;j++){
            var p1 = patterns.get(i); var p2 = patterns.get(j);
            String a = p1.get(p1.size()-1), b = p2.get(p2.size()-1);
            var cands = (p1.equals(p2)) ? List.of(append(p1,a)) : List.of(append(p1,b), append(p2,a));
            for (var cand : cands) {
                if (cand.size()>=2) {
                    String x=cand.get(cand.size()-2), y=cand.get(cand.size()-1);
                    if (wcmap.get(x,y) < minWs) continue;
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

    private static List<String> append(List<String> p, String x){ var r=new ArrayList<String>(p); r.add(x); return r; }
}
