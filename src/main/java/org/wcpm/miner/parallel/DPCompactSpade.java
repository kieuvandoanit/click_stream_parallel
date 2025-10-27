package org.wcpm.miner.parallel;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.CompactIdList;
import org.wcpm.util.WCMAP;

import java.util.*;
import java.util.concurrent.*;

public class DPCompactSpade implements PatternMiner {
    private final int threads;
    private final int qFactor;

    public DPCompactSpade(int threads) { this(threads, 3); }
    public DPCompactSpade(int threads, int qFactor) { this.threads = threads; this.qFactor = qFactor; }

    @Override
    public String name() { return "DP-Compact-SPADE(" + threads + ")"; }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        // --- GIAI ĐOẠN 1: CHUẨN BỊ (Vẫn chạy tuần tự) ---
        WCMAP wcmap = WCMAP.build(cdb);

        // Quét CDB 1 lần duy nhất để lấy dữ liệu dọc
        Map<Integer, Double> cidWeights = new HashMap<>();
        double totalWeight = cdb.totalWeight();
        Map<String, CompactIdList> f1RawMap = new HashMap<>();
        for (var r : cdb.rows()) {
            cidWeights.put(r.cid(), r.weight());
            var s = r.seq();
            for (int i = 0; i < s.size(); i++) {
                String item = s.get(i);
                f1RawMap.computeIfAbsent(item, k -> new CompactIdList()).add(r.cid(), i);
            }
        }

        // Tìm F1 phổ biến
        Map<List<String>, CompactIdList> f1FrequentMap = new LinkedHashMap<>();
        // Dùng tập hợp an toàn cho thread để lưu kết quả cuối cùng
        Set<List<String>> F = ConcurrentHashMap.newKeySet();

        for (var entry : f1RawMap.entrySet()) {
            CompactIdList idList = entry.getValue();
            idList.sort();
            double ws = idList.calculateSupport(cidWeights, totalWeight);
            if (ws >= minWs) {
                var p = List.of(entry.getKey());
                f1FrequentMap.put(p, idList);
                F.add(p);
            }
        }

        // --- GIAI ĐOẠN 2: KHAI PHÁ SONG SONG ---
        int qmax = qFactor * threads;
        // Hàng đợi bây giờ chứa các "Lớp Tương đương", là Map<Pattern, IdList>
        LinkedBlockingQueue<Map<List<String>, CompactIdList>> Q = new LinkedBlockingQueue<>(qmax);
        ForkJoinPool pool = new ForkJoinPool(threads);

        // Đưa tác vụ đầu tiên (toàn bộ F1) vào hàng đợi
        Q.offer(f1FrequentMap);

        // Balancer thread để điều phối tác vụ
        Runnable balancer = () -> {
            try {
                // Chạy cho đến khi pool không còn task nào hoạt động VÀ hàng đợi trống
                while (!pool.isQuiescent() || !Q.isEmpty()) {
                    Map<List<String>, CompactIdList> eqClass = Q.poll(100, TimeUnit.MILLISECONDS);
                    if (eqClass != null && !eqClass.isEmpty()) {
                        pool.execute(new ExpandTask(eqClass, F, cidWeights, totalWeight, wcmap, minWs, Q));
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };

        Thread dispatcher = new Thread(balancer);
        dispatcher.start();
        try {
            dispatcher.join();
            pool.shutdown();
            pool.awaitTermination(365, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return F;
    }

    // Task đệ quy, bây giờ sử dụng CompactIdList
    static class ExpandTask extends RecursiveAction {
        private final Map<List<String>, CompactIdList> currentEqClass;
        private final Set<List<String>> F;
        private final Map<Integer, Double> cidWeights;
        private final double totalWeight;
        private final WCMAP wcmap;
        private final double minWs;
        private final LinkedBlockingQueue<Map<List<String>, CompactIdList>> Q;

        ExpandTask(Map<List<String>, CompactIdList> currentEqClass, Set<List<String>> F,
                   Map<Integer, Double> cidWeights, double totalWeight, WCMAP wcmap, double minWs,
                   LinkedBlockingQueue<Map<List<String>, CompactIdList>> Q) {
            this.currentEqClass = currentEqClass;
            this.F = F;
            this.cidWeights = cidWeights;
            this.totalWeight = totalWeight;
            this.wcmap = wcmap;
            this.minWs = minWs;
            this.Q = Q;
        }

        @Override
        protected void compute() {
            Map<List<String>, Map<List<String>, CompactIdList>> nextLevelEqClasses = new LinkedHashMap<>();
            List<Map.Entry<List<String>, CompactIdList>> patterns = new ArrayList<>(currentEqClass.entrySet());
            int n = patterns.size();

            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    var p1 = patterns.get(i).getKey();
                    var list1 = patterns.get(i).getValue();
                    var p2 = patterns.get(j).getKey();
                    var list2 = patterns.get(j).getValue();

                    String a = p1.get(p1.size() - 1);
                    String b = p2.get(p2.size() - 1);

                    var cands = (p1.equals(p2)) ? List.of(append(p1, a)) : List.of(append(p1, b), append(p2, a));

                    for (var cand : cands) {
                        if (cand.size() >= 2) {
                            String x = cand.get(cand.size() - 2);
                            String y = cand.get(cand.size() - 1);
                            if (wcmap.get(x, y) < minWs) continue;
                        }

                        CompactIdList candList;
                        if (p1.equals(p2)) {
                            candList = list1.join(list1, CompactIdList.JoinType.I_STEP);
                        } else if (cand.equals(append(p1, b))) {
                            candList = list1.join(list2, CompactIdList.JoinType.S_STEP);
                        } else {
                            candList = list2.join(list1, CompactIdList.JoinType.S_STEP);
                        }

                        if (candList.size() == 0) continue;

                        double ws = candList.calculateSupport(cidWeights, totalWeight);
                        if (ws >= minWs) {
                            F.add(cand);
                            List<String> newPrefix = cand.subList(0, cand.size() - 1);
                            nextLevelEqClasses.computeIfAbsent(newPrefix, k -> new HashMap<>()).put(cand, candList);
                        }
                    }
                }
            }

            List<RecursiveAction> subTasks = new ArrayList<>();
            for (var entry : nextLevelEqClasses.entrySet()) {
                var nextClass = entry.getValue();
                if (nextClass.isEmpty()) continue;

                // Cố gắng chuyển giao tác vụ. Nếu không được, tự xử lý.
                boolean offloaded = Q.offer(nextClass);
                if (!offloaded) {
                    subTasks.add(new ExpandTask(nextClass, F, cidWeights, totalWeight, wcmap, minWs, Q));
                }
            }
            if (!subTasks.isEmpty()) {
                invokeAll(subTasks);
            }
        }

        private static List<String> append(List<String> p, String x) {
            var r = new ArrayList<>(p);
            r.add(x);
            return r;
        }
    }
}
