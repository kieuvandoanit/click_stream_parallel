package org.wcpm.miner.parallel;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.CompactIdList;
import org.wcpm.util.WCMAP;
import org.wcpm.util.Seqs; // Cần Seqs để lấy F1 cho sample

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class APCompactSpade implements PatternMiner {
    private final int threads;
    private final double cThreshold;

    public APCompactSpade(int threads) { this(threads, 0.70); }
    public APCompactSpade(int threads, double cThreshold) { this.threads = threads; this.cThreshold = cThreshold; }

    @Override
    public String name() { return "AP-Compact-SPADE(" + threads + ", c=" + cThreshold + ")"; }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        // --- GIAI ĐOẠN 1: LẤY MẪU VÀ ƯỚC LƯỢNG SC ---

        // 1.1: Lấy mẫu S theo quy tắc của bài báo
        List<List<String>> allOnePatterns = cdb.alphabet().stream().map(List::of).collect(Collectors.toList());
        int sSize = Math.max(2, Math.min(50, (int) Math.ceil(allOnePatterns.size() * 0.01)));

        // Sắp xếp các 1-pattern theo weighted support giảm dần
        allOnePatterns.sort(Comparator.comparingDouble(p -> -Seqs.weightedSupport(p, cdb)));

        // Tạo map F1 phổ biến CHỈ cho mẫu S
        Map<List<String>, CompactIdList> s_f1FrequentMap = new LinkedHashMap<>();

        // Chuẩn bị dữ liệu dọc cần thiết cho việc chạy trên mẫu S
        Map<Integer, Double> cidWeights = new HashMap<>();
        double totalWeight = cdb.totalWeight();
        for(var r : cdb.rows()) cidWeights.put(r.cid(), r.weight());

        for (int i = 0; i < Math.min(sSize, allOnePatterns.size()); i++) {
            List<String> p = allOnePatterns.get(i);
            // Chúng ta cần CompactIdList cho các pattern trong S
            CompactIdList idList = new CompactIdList();
            for(var r : cdb.rows()){
                for(int pos=0; pos < r.seq().size(); pos++){
                    if(r.seq().get(pos).equals(p.get(0))){
                        idList.add(r.cid(), pos);
                    }
                }
            }
            idList.sort();
            s_f1FrequentMap.put(p, idList);
        }

        // 1.2: Chạy DP-SPADE trên mẫu S và đếm join để tính SC
        double SC = estimateSCByRunningOnSample(s_f1FrequentMap, cdb, minWs, cidWeights, totalWeight);

        // 1.3: Ra quyết định
        boolean useHorizontal = SC > cThreshold;
        System.out.println("Adaptive Choice: SC = " + String.format("%.2f", SC) + ". Using " + (useHorizontal ? "HP-SPADE" : "DP-SPADE"));

        // --- GIAI ĐOẠN 2: CHẠY THUẬT TOÁN ĐÃ CHỌN TRÊN TOÀN BỘ DỮ LIỆU ---
        if (useHorizontal) {
            // HP-SPADE hoạt động tốt hơn với phiên bản quét lại DB (horizontal)
            return new HPCompactSpade(threads).mine(cdb, minWs);
        } else {
            // DP-SPADE hoạt động tốt hơn với phiên bản vertical (dùng CompactIdList)
            return new DPCompactSpade(threads).mine(cdb, minWs);
        }
    }

    /**
     * Chạy một phiên bản thu nhỏ của DP-SPADE trên mẫu S để đếm số lần join thực tế.
     */
    private double estimateSCByRunningOnSample(Map<List<String>, CompactIdList> s_f1Map, CDB cdb, double minWs,
                                               Map<Integer, Double> cidWeights, double totalWeight) {
        if (s_f1Map.isEmpty()) return 0.0;

        WCMAP wcmap = WCMAP.build(cdb); // Vẫn cần WCMAP để cắt tỉa
        ForkJoinPool estimationPool = new ForkJoinPool(threads);

        // Mảng để lưu số join ở các cấp độ: joinCounts[k] = số join tạo ra pattern dài k+1
        long[] joinCounts = new long[100]; // Giả sử pattern không dài quá 100

        JoinCountingTask rootTask = new JoinCountingTask(s_f1Map, wcmap, minWs, cidWeights, totalWeight, joinCounts);
        estimationPool.invoke(rootTask);
        estimationPool.shutdown();

        long join1_to_3 = joinCounts[1] + joinCounts[2]; // join tạo pattern dài 2 và 3
        long totalJoins = 0;
        for (long count : joinCounts) {
            totalJoins += count;
        }

        if (totalJoins == 0) return 0.0;
        return (double) join1_to_3 / totalJoins;
    }

    /**
     * Một RecursiveAction chuyên dụng chỉ để đếm join, không lưu kết quả.
     */
    static class JoinCountingTask extends RecursiveAction {
        private final Map<List<String>, CompactIdList> currentEqClass;
        private final WCMAP wcmap;
        private final double minWs;
        private final Map<Integer, Double> cidWeights;
        private final double totalWeight;
        private final long[] joinCounts; // Mảng chung để các task cùng cập nhật

        JoinCountingTask(Map<List<String>, CompactIdList> eqClass, WCMAP wcmap, double minWs,
                         Map<Integer, Double> cidWeights, double totalWeight, long[] joinCounts) {
            this.currentEqClass = eqClass;
            this.wcmap = wcmap;
            this.minWs = minWs;
            this.cidWeights = cidWeights;
            this.totalWeight = totalWeight;
            this.joinCounts = joinCounts;
        }

        @Override
        protected void compute() {
            if (currentEqClass.isEmpty()) return;

            int currentPatternLength = currentEqClass.keySet().iterator().next().size();
            if (currentPatternLength >= joinCounts.length - 1) return; // Tránh tràn mảng

            Map<List<String>, Map<List<String>, CompactIdList>> nextLevelEqClasses = new LinkedHashMap<>();
            List<Map.Entry<List<String>, CompactIdList>> patterns = new ArrayList<>(currentEqClass.entrySet());
            int n = patterns.size();

            for (int i = 0; i < n; i++) {
                for (int j = i; j < n; j++) {
                    // Mỗi cặp (i, j) là một lần "join" ở cấp độ hiện tại
                    synchronized (joinCounts) {
                        joinCounts[currentPatternLength -1]++;
                    }

                    var p1 = patterns.get(i).getKey();
                    var list1 = patterns.get(i).getValue();
                    var p2 = patterns.get(j).getKey();
                    var list2 = patterns.get(j).getValue();

                    var cands = (p1.equals(p2)) ? List.of(append(p1, p1.get(p1.size()-1))) : List.of(append(p1, p2.get(p2.size()-1)), append(p2, p1.get(p1.size()-1)));

                    for(var cand : cands){
                        if (cand.size() >= 2) {
                            String x = cand.get(cand.size() - 2);
                            String y = cand.get(cand.size() - 1);
                            if (wcmap.get(x, y) < minWs) continue;
                        }
                        CompactIdList candList;
                        if (p1.equals(p2)) candList = list1.join(list1, CompactIdList.JoinType.I_STEP);
                        else if (cand.equals(append(p1, p2.get(p2.size()-1)))) candList = list1.join(list2, CompactIdList.JoinType.S_STEP);
                        else candList = list2.join(list1, CompactIdList.JoinType.S_STEP);

                        if (candList.calculateSupport(cidWeights, totalWeight) >= minWs) {
                            List<String> newPrefix = cand.subList(0, cand.size() - 1);
                            nextLevelEqClasses.computeIfAbsent(newPrefix, k -> new HashMap<>()).put(cand, candList);
                        }
                    }
                }
            }

            if (!nextLevelEqClasses.isEmpty()) {
                List<JoinCountingTask> subTasks = new ArrayList<>();
                for (var nextClass : nextLevelEqClasses.values()) {
                    subTasks.add(new JoinCountingTask(nextClass, wcmap, minWs, cidWeights, totalWeight, joinCounts));
                }
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
