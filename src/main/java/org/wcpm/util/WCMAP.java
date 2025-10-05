package org.wcpm.util;
import org.wcpm.model.*;
import java.util.*;

public class WCMAP {
    private final Map<String, Double> wc = new HashMap<>(); // key: a+"\u0001"+b
    public static String key(String a, String b){ return a+"\u0001"+b; }
    public double get(String a, String b){ return wc.getOrDefault(key(a,b), 0.0); }

    public static WCMAP build(CDB cdb) {
        Map<String, Double> accum = new HashMap<>();
        double total = cdb.totalWeight();
        for (var r : cdb.rows()) {
            Set<String> pairs = new HashSet<>();
            var s = r.seq();
            for (int i=0;i<s.size();i++)
                for (int j=i+1;j<s.size();j++)
                    pairs.add(key(s.get(i), s.get(j)));
            for (var k: pairs) accum.merge(k, r.weight(), Double::sum);
        }
        WCMAP m = new WCMAP();
        for (var e: accum.entrySet()) m.wc.put(e.getKey(), e.getValue()/total);
        return m;
    }
}