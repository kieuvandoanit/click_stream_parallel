package org.wcpm.miner.serial;

import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.CompactIdList; // Import class mới
import org.wcpm.util.WCMAP;

import java.util.*;

public class CompactSpadeSerial implements PatternMiner {
    @Override
    public String name() {
        return "Serial-Compact-SPADE (Vertical)";
    }

    @Override
    public Set<List<String>> mine(CDB cdb, double minWs) {
        // 1. Xây dựng WCMAP
        WCMAP wcmap = WCMAP.build(cdb);

        // 2. CHUẨN BỊ DỮ LIỆU DỌC (QUÉT CDB 1 LẦN DUY NHẤT)
        Map<Integer, Double> cidWeights = new HashMap<>();
        double totalWeight = cdb.totalWeight();
        // Map thô của F1 (tất cả 1-item và IdList của chúng)
        Map<String, CompactIdList> f1RawMap = new HashMap<>();

        for (var r : cdb.rows()) {
            cidWeights.put(r.cid(), r.weight()); // Giả sử row có r.cid()
            var s = r.seq();
            for (int i = 0; i < s.size(); i++) {
                String item = s.get(i);
                f1RawMap.computeIfAbsent(item, k -> new CompactIdList()).add(r.cid(), i);
            }
        }

        // 3. TÌM F1 (Lọc từ F1 thô)
        // Map F1 phổ biến (key là pattern, value là IdList của nó)
        Map<List<String>, CompactIdList> f1FrequentMap = new LinkedHashMap<>();
        Set<List<String>> F = new LinkedHashSet<>(); // Tập kết quả cuối cùng

        for (var entry : f1RawMap.entrySet()) {
            String item = entry.getKey();
            CompactIdList idList = entry.getValue();
            idList.sort(); // Sắp xếp IdList để chuẩn bị cho join

            double ws = idList.calculateSupport(cidWeights, totalWeight);
            if (ws >= minWs) {
                var p = List.of(item);
                f1FrequentMap.put(p, idList);
                F.add(p);
            }
        }

        // 4. Mở rộng theo lớp (prefix-based), DFS
        // Phân F1 thành các lớp tiền tố. Ở cấp 1, mỗi item là 1 tiền tố.
        Map<List<String>, Map<List<String>, CompactIdList>> initialEqClasses = new LinkedHashMap<>();
        for (var entry : f1FrequentMap.entrySet()) {
            List<String> prefix = List.of(); // Tiền tố rỗng
            // Lớp [a] chứa <a, listA>, Lớp [b] chứa <b, listB>...
            // Nhưng SPADE join các lớp 1-item với nhau.
            // Logic của bạn là join F1 với F1 để ra F2, rồi phân lớp.
            // Chúng ta sẽ tuân theo logic đó.
        }

        // Gọi đệ quy theo logic của bạn: bắt đầu với tiền tố rỗng
        // và danh sách các mẫu F1 phổ biến.
        dfsExtend(f1FrequentMap, F, cidWeights, totalWeight, wcmap, minWs);
        return F;
    }

    private void dfsExtend(Map<List<String>, CompactIdList> currentEqClass,
                           Set<List<String>> F,
                           Map<Integer, Double> cidWeights, double totalWeight,
                           WCMAP wcmap, double minWs) {

        // Map chứa các LỚP tương đương cho bước đệ quy tiếp theo
        // Key: Tiền tố mới (ví dụ: <a, b>)
        // Value: Lớp tương đương của tiền tố đó (ví dụ: Map chứa { <a,b,c>: IdList, <a,b,d>: IdList })
        Map<List<String>, Map<List<String>, CompactIdList>> nextLevelEqClasses = new LinkedHashMap<>();

        // Chuyển Map thành List để truy cập bằng index (i, j)
        List<Map.Entry<List<String>, CompactIdList>> patterns = new ArrayList<>(currentEqClass.entrySet());

        int n = patterns.size();
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                // Lấy mẫu cha và IdList của chúng
                List<String> p1 = patterns.get(i).getKey();
                CompactIdList list1 = patterns.get(i).getValue();

                List<String> p2 = patterns.get(j).getKey();
                CompactIdList list2 = patterns.get(j).getValue();

                String a = p1.get(p1.size() - 1);
                String b = p2.get(p2.size() - 1);

                // Tạo các ứng viên
                List<List<String>> cands = (p1.equals(p2))
                        ? List.of(append(p1, a))
                        : List.of(append(p1, b), append(p2, a));

                for (var cand : cands) {
                    // 1. Cắt tỉa bằng WCMAP (giữ nguyên)
                    if (cand.size() >= 2) {
                        String x = cand.get(cand.size() - 2);
                        String y = cand.get(cand.size() - 1);
                        if (wcmap.get(x, y) < minWs) continue; // prune
                    }

                    // 2. TÍNH SUPPORT BẰNG PHÉP JOIN
                    CompactIdList candList = null;
                    if (p1.equals(p2)) {
                        // Cand = <p1, a> (ví dụ: p1=<ab>, a=b -> cand=<ab,b>)
                        // Đây là I-Step: join list1 với chính nó
                        candList = list1.join(list1, CompactIdList.JoinType.I_STEP);
                    } else if (cand.equals(append(p1, b))) {
                        // Cand = <p1, b> (ví dụ: p1=<ab>, p2=<ac>, b=c -> cand=<ab,c>)
                        // Đây là S-Step: join list1 với list2
                        candList = list1.join(list2, CompactIdList.JoinType.S_STEP);
                    } else { // cand.equals(append(p2, a))
                        // Cand = <p2, a> (ví dụ: p1=<ab>, p2=<ac>, a=b -> cand=<ac,b>)
                        // Đây là S-Step: join list2 với list1
                        candList = list2.join(list1, CompactIdList.JoinType.S_STEP);
                    }

                    // 3. KIỂM TRA SUPPORT TỪ IDLIST MỚI
                    if (candList.size() == 0) continue; // Bỏ qua nếu phép join không ra gì

                    double ws = candList.calculateSupport(cidWeights, totalWeight);
                    if (ws >= minWs) {
                        F.add(cand);

                        // Thêm (cand, candList) vào lớp tương đương của nó
                        // để chuẩn bị cho vòng đệ quy tiếp theo.
                        List<String> newPrefix = cand.subList(0, cand.size() - 1);
                        nextLevelEqClasses.computeIfAbsent(newPrefix, k -> new HashMap<>())
                                .put(cand, candList);
                    }
                }
            }
        }

        // 4. ĐỆ QUY VỚI CÁC LỚP MỚI TÌM ĐƯỢC
        for (var e : nextLevelEqClasses.entrySet()) {
            // e.getKey() là tiền tố (ví dụ: <a, b>)
            // e.getValue() là Map của lớp đó (ví dụ: { <a,b,c>: IdList, ... })
            if (!e.getValue().isEmpty()) {
                dfsExtend(e.getValue(), F, cidWeights, totalWeight, wcmap, minWs);
            }
        }
    }

    private static List<String> append(List<String> p, String x) {
        ArrayList<String> r = new ArrayList<>(p);
        r.add(x);
        return r;
    }
}
