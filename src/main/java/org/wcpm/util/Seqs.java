package org.wcpm.util;
import org.wcpm.model.*;
import java.util.*;

public class Seqs {
    public static boolean isSubsequence(List<String> pat, List<String> seq) {
        int i=0, j=0;
        while (i<pat.size() && j<seq.size()) {
            if (pat.get(i).equals(seq.get(j))) i++;
            j++;
        }
        return i==pat.size();
    }
    public static double weightedSupport(List<String> pat, CDB cdb) {
        double sum = 0.0;
        for (var r : cdb.rows()) if (isSubsequence(pat, r.seq())) sum += r.weight();
        return sum / cdb.totalWeight();
    }
}