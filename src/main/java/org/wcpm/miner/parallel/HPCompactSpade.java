package org.wcpm.miner.parallel;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.CompactIdList;
import org.wcpm.util.WCMAP;

import java.util.*;
import java.util.concurrent.*;

/**
 * HP-Compact-SPADE phiên bản Vertical.
 * - Chiến lược song song hóa: Horizontal (chia để trị theo các 1-class).
 * - Thuật toán lõi: Vertical (dùng CompactIdList và phép join).
 */
public class HPCompactSpade implements PatternMiner {
    private final int threads;

    public HPCompactSpade(int threads) { this.threads = threads; }

    @Override
    public String name() { return "HP-Compact-SPADE(" + threads + ")"; }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        // --- GIAI ĐOẠN 1: CHUẨN BỊ (Tuần tự) ---
        // Quét CDB 1 lần duy nhất để xây dựng WCMAP và các CompactIdList cho F1
        WCMAP wcmap = WCMAP.build(cdb);

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

        Map<List<String>, CompactIdList> f1FrequentMap = new LinkedHashMap<>();
        Set<List<String>> F = ConcurrentHashMap.newKeySet(); // Dùng tập an toàn cho thread
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

        // --- GIAI ĐOẠN 2: KHAI PHÁ SONG SONG THEO CHIỀU NGANG ---

        // 2.1. Tạo các Lớp Tương đương 1-item từ F1. Đây là các "đơn vị công việc".
        // Lớp [a] chứa các 2-pattern phổ biến bắt đầu bằng 'a'.
        Map<List<String>, Map<List<String>, CompactIdList>> oneClasses = buildOneClasses(f1FrequentMap, F, cidWeights, totalWeight, wcmap, minWs);

        // 2.2. Đưa các 1-class vào hàng đợi tác vụ.
        BlockingQueue<Map.Entry<List<String>, Map<List<String>, CompactIdList>>> tasks = new LinkedBlockingQueue<>(oneClasses.entrySet());
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        // 2.3. Các worker lấy việc và xử lý độc lập
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                while (true) {
                    var task = tasks.poll();
                    if (task == null) break; // Hết việc

                    Map<List<String>, CompactIdList> eqClass = task.getValue();
                    // Mỗi worker sẽ tự mình đi sâu vào cây con của lớp nó nhận được
                    dfsExtend(eqClass, F, cidWeights, totalWeight, wcmap, minWs);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(365, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        return F;
    }

    /**
     * Hàm này thực hiện phép join trên F1 để tạo ra các Lớp 1-item ban đầu.
     * Mỗi lớp 1-item chứa các 2-pattern phổ biến.
     */
    private Map<List<String>, Map<List<String>, CompactIdList>> buildOneClasses(
            Map<List<String>, CompactIdList> f1FrequentMap, Set<List<String>> F,
            Map<Integer, Double> cidWeights, double totalWeight, WCMAP wcmap, double minWs) {

        Map<List<String>, Map<List<String>, CompactIdList>> oneClasses = new LinkedHashMap<>();
        List<Map.Entry<List<String>, CompactIdList>> patterns = new ArrayList<>(f1FrequentMap.entrySet());
        int n = patterns.size();

        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                var p1Entry = patterns.get(i);
                var p2Entry = patterns.get(j);

                var p1 = p1Entry.getKey();
                var list1 = p1Entry.getValue();
                var p2 = p2Entry.getKey();
                var list2 = p2Entry.getValue();

                var cands = (p1.equals(p2)) ? List.of(append(p1, p1.get(0))) : List.of(append(p1, p2.get(0)), append(p2, p1.get(0)));

                for(var cand : cands){
                    if (wcmap.get(cand.get(0), cand.get(1)) < minWs) continue;

                    CompactIdList candList = (p1.equals(p2))
                            ? list1.join(list1, CompactIdList.JoinType.I_STEP)
                            : (cand.get(0).equals(p1.get(0)) ? list1.join(list2, CompactIdList.JoinType.S_STEP) : list2.join(list1, CompactIdList.JoinType.S_STEP));

                    if(candList.calculateSupport(cidWeights, totalWeight) >= minWs){
                        F.add(cand);
                        List<String> prefix = cand.subList(0, 1); // Prefix là 1-item
                        oneClasses.computeIfAbsent(prefix, k -> new HashMap<>()).put(cand, candList);
                    }
                }
            }
        }
        return oneClasses;
    }


    /**
     * Hàm đệ quy DFS theo chiều dọc, chạy tuần tự bên trong mỗi thread.
     */
    private void dfsExtend(Map<List<String>, CompactIdList> currentEqClass,
                           Set<List<String>> F,
                           Map<Integer, Double> cidWeights, double totalWeight,
                           WCMAP wcmap, double minWs) {

        // Logic bên trong hàm này giống hệt với dfsExtend của DPCompactSpade
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
        for (var e : nextLevelEqClasses.entrySet()) {
            if (!e.getValue().isEmpty()) {
                dfsExtend(e.getValue(), F, cidWeights, totalWeight, wcmap, minWs);
            }
        }
    }

    private static List<String> append(List<String> p, String x) {
        var r = new ArrayList<>(p);
        r.add(x);
        return r;
    }
}
