package org.wcpm.util;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Đại diện cho một WICList (Weighted ID-Compact Value List) như trong bài báo.
 * Nó lưu trữ một danh sách các lần xuất hiện của một pattern dưới dạng (cid, pos).
 * Chúng ta sẽ nén (cid, pos) vào một giá trị 'long' duy nhất để tiết kiệm bộ nhớ.
 */
public class CompactIdList {

    // Danh sách các giá trị nén. Mỗi giá trị là 1 'long' chứa (cid << 32 | pos)
    private final List<Long> idList;
    private double weightedSupport = 0.0;

    // Giả sử CID và POS đều là kiểu int. Chúng ta dùng 32 bit đầu cho CID, 32 bit sau cho POS.
    private static long toCompact(int cid, int pos) {
        return ((long) cid << 32) | (pos & 0xFFFFFFFFL);
    }

    private static int getCid(long compactValue) {
        return (int) (compactValue >> 32);
    }

    private static int getPos(long compactValue) {
        return (int) compactValue;
    }

    public CompactIdList() {
        this.idList = new ArrayList<>();
    }

    public void add(int cid, int pos) {
        this.idList.add(toCompact(cid, pos));
    }

    public int size() {
        return idList.size();
    }

    /**
     * Tính toán weighted support DỰA TRÊN IDLIST, không quét lại CDB.
     */
    public double calculateSupport(Map<Integer, Double> cidWeights, double totalWeight) {
        if (this.weightedSupport > 0) return this.weightedSupport;
        if (idList.isEmpty()) return 0.0;

        Set<Integer> cids = new HashSet<>();
        for (long val : idList) {
            cids.add(getCid(val));
        }

        double sum = 0.0;
        for (int cid : cids) {
            sum += cidWeights.getOrDefault(cid, 0.0);
        }

        this.weightedSupport = sum / totalWeight;
        return this.weightedSupport;
    }

    /**
     * Sắp xếp danh sách (quan trọng cho phép join).
     */
    public void sort() {
        // Sắp xếp theo CID, sau đó theo POS
        this.idList.sort(Comparator.comparingLong(val -> val));
    }

    public enum JoinType {
        /**
         * Join p1 và p2 (khác nhau) để tạo <p1, last(p2)>
         * (p1, pos1) và (p2, pos2) -> pos1 < pos2
         */
        S_STEP, // Sequence-step
        /**
         * Join p1 với chính nó để tạo <p1, last(p1)>
         * (p1, pos1) và (p1, pos2) -> pos1 < pos2
         */
        I_STEP  // Itemset-step (trong bài báo gọi là join P1=P2)
    }

    /**
     * Phép giao (join) cốt lõi của SPADE.
     * Đây là phần thay thế cho Seqs.weightedSupport(cand, cdb).
     */
    public CompactIdList join(CompactIdList other, JoinType type) {
        CompactIdList newList = new CompactIdList();

        if (type == JoinType.I_STEP) {
            // Join một danh sách với chính nó (P1 == P2)
            // Tìm các cặp (cid, pos1) và (cid, pos2) trong CÙNG một danh sách
            // sao cho pos1 < pos2
            for (int i = 0; i < this.idList.size(); i++) {
                long val1 = this.idList.get(i);
                int cid1 = getCid(val1);
                int pos1 = getPos(val1);

                for (int j = i + 1; j < this.idList.size(); j++) {
                    long val2 = this.idList.get(j);
                    if (getCid(val2) != cid1) {
                        break; // Đã sang CID khác
                    }
                    // Cùng CID, pos1 < pos2 (vì danh sách đã sắp xếp)
                    newList.add(cid1, getPos(val2));
                }
            }
        } else {
            // Join hai danh sách khác nhau (P1 != P2)
            // Tìm (cid, pos1) trong 'this' (list1) và (cid, pos2) trong 'other' (list2)
            // sao cho pos1 < pos2
            int i = 0, j = 0;
            while (i < this.idList.size()) {
                long val1 = this.idList.get(i);
                int cid1 = getCid(val1);
                int pos1 = getPos(val1);

                // Di chuyển con trỏ j đến đúng CID
                while (j < other.idList.size() && getCid(other.idList.get(j)) < cid1) {
                    j++;
                }

                // Quét tất cả các entry trong 'other' có cùng CID
                int k = j;
                while (k < other.idList.size() && getCid(other.idList.get(k)) == cid1) {
                    int pos2 = getPos(other.idList.get(k));
                    if (pos1 < pos2) {
                        newList.add(cid1, pos2);
                    }
                    k++;
                }
                i++;
            }
        }
        // Lưu ý: Danh sách mới có thể có trùng lặp, nhưng không ảnh hưởng
        // đến lúc tính support (vì dùng Set<Integer> cids).
        // Nếu cần tối ưu, có thể xử lý trùng lặp ở đây.
        return newList;
    }
}
