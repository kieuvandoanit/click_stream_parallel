package org.wcpm.miner.parallel;
import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.WCMAP;
import org.wcpm.util.Seqs;

import java.util.*;
import java.util.concurrent.*;

public class DPCompactSpade implements PatternMiner {
    private final int threads; private final int qFactor;
    public DPCompactSpade(int threads){ this(threads, 3); }
    public DPCompactSpade(int threads, int qFactor){ this.threads=threads; this.qFactor=qFactor; }
    @Override public String name(){ return "DP-Compact-SPADE("+threads+")"; }

    @Override public Set<List<String>> mine(CDB cdb, double minWs) {
        WCMAP wcmap = WCMAP.build(cdb);
        int qmax = qFactor * threads;
        LinkedBlockingQueue<List<List<String>>> Q = new LinkedBlockingQueue<>(qmax);
        ForkJoinPool pool = new ForkJoinPool(threads);

        // Seed: 0-class -> danh sách 1-pattern frequent
        List<List<String>> F1 = new ArrayList<>();
        for (String a : cdb.alphabet()) {
            var p = List.of(a);
            if (Seqs.weightedSupport(p, cdb) >= minWs) F1.add(p);
        }
        Set<List<String>> F = ConcurrentHashMap.newKeySet();
        F.addAll(F1);
        Q.offer(F1);

        Runnable balancer = () -> {
            try {
                while (!pool.isQuiescent() || !Q.isEmpty()) {
                    List<List<String>> cls = Q.poll(100, TimeUnit.MILLISECONDS);
                    if (cls != null) pool.execute(new ExpandTask(List.of(), cls, F, cdb, wcmap, minWs, Q, qmax));
                }
            } catch (InterruptedException ignored) {}
        };
        Thread dispatcher = new Thread(balancer); dispatcher.start();
        try { dispatcher.join(); pool.shutdown(); pool.awaitTermination(365, TimeUnit.DAYS); }
        catch (InterruptedException e){ throw new RuntimeException(e); }
        return F;
    }

    // Task đệ quy
    static class ExpandTask extends RecursiveAction {
        private final List<String> prefix;
        private final List<List<String>> patterns;
        private final Set<List<String>> F;
        private final CDB cdb; private final WCMAP wcmap; private final double minWs;
        private final LinkedBlockingQueue<List<List<String>>> Q; private final int qmax;

        ExpandTask(List<String> prefix, List<List<String>> patterns, Set<List<String>> F,
                   CDB cdb, WCMAP wcmap, double minWs, LinkedBlockingQueue<List<List<String>>> Q, int qmax){
            this.prefix=prefix; this.patterns=patterns; this.F=F; this.cdb=cdb; this.wcmap=wcmap; this.minWs=minWs; this.Q=Q; this.qmax=qmax;
        }
        @Override protected void compute() {
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
            List<RecursiveAction> subs = new ArrayList<>();
            for (var e: next.entrySet()) {
                var cls = e.getValue();
                // nếu Q còn slot, chuyển giao; nếu không, xử lý đệ quy tại chỗ
                boolean offloaded = Q.offer(cls);
                if (!offloaded) subs.add(new ExpandTask(e.getKey(), cls, F, cdb, wcmap, minWs, Q, qmax));
            }
            invokeAll(subs);
        }
        private static List<String> append(List<String> p, String x){ var r=new ArrayList<String>(p); r.add(x); return r; }
    }
}