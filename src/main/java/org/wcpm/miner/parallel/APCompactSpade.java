package org.wcpm.miner.parallel;
import org.wcpm.miner.PatternMiner;
import org.wcpm.model.CDB;
import org.wcpm.util.Seqs;

import java.util.*;
import java.util.stream.Collectors;

public class APCompactSpade implements PatternMiner {
    private final int threads; private final double cThreshold; // mặc định 0.70
    public APCompactSpade(int threads){ this(threads, 0.70); }
    public APCompactSpade(int threads, double cThreshold){ this.threads=threads; this.cThreshold=cThreshold; }
    @Override public String name(){ return "AP-Compact-SPADE("+threads+", c="+cThreshold+")"; }

    @Override public Set<List<String>> mine(CDB cdb, double minWs) {
        // Lấy mẫu S theo rule: 1% 1-class, [a] có ws cao nhất phải có trong S, 2 <= |S| <= 50
        List<List<String>> one = cdb.alphabet().stream().map(List::of).collect(Collectors.toList());
        int sSize = Math.max(2, Math.min(50, Math.max(1, (int)Math.ceil(one.size()*0.01))));
        // chọn [a] có ws cao nhất
        one.sort(Comparator.comparingDouble(p -> -Seqs.weightedSupport(p, cdb)));
        Set<List<String>> S = new LinkedHashSet<>();
        for (int i=0;i<Math.min(sSize, one.size()); i++) S.add(one.get(i));

        // Ước lượng SC = (join1 + join2 + join3)/joinS (ở đây dùng proxy: số ứng viên sinh ra ở chiều sâu 1..3)
        // Triển khai DP trên S để đếm join theo mức (đơn giản hoá cho bản chạy thử).
        double SC = estimateSCByDPOnSample(S, cdb, minWs);

        boolean useHorizontal = SC > cThreshold;
        if (useHorizontal) {
            return new HPCompactSpade(threads).mine(cdb, minWs);
        } else {
            return new DPCompactSpade(threads).mine(cdb, minWs);
        }
    }

    private double estimateSCByDPOnSample(Set<List<String>> S, CDB cdb, double minWs){
        // Bản đơn giản: tính số join giữa 1-,2-,3-pattern dựa trên F1 từ S
        // (Bạn có thể thay bằng đếm thực tế trong quá trình mở rộng).
        int join1=0, join2=0, join3=0, joinS=0;
        List<List<String>> list = new ArrayList<>(S);
        // join giữa 1-pattern ~ số cặp (i<=j) trong S
        for (int i=0;i<list.size();i++) for(int j=i;j<list.size();j++){ join1++; joinS++; }
        // proxy thô: giảm trọng số nếu ws thấp để tránh over-estimate
        double adjust = 0.5;
        join2 = (int)(join1 * adjust);
        join3 = (int)(join2 * adjust);
        joinS = Math.max(joinS, join1+join2+join3);
        return (join1+join2+join3) / (double) joinS;
    }
}