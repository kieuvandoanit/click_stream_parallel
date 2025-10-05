package org.wcpm.model;
import java.util.*;

public class CDB {
    private final List<Clickstream> rows;
    private final double totalWeight;
    private final Set<String> alphabet;

    public CDB(List<Clickstream> rows) {
        this.rows = rows;
        this.totalWeight = rows.stream().mapToDouble(Clickstream::weight).sum();
        Set<String> alpha = new TreeSet<>();
        for (var r : rows) alpha.addAll(r.seq());
        this.alphabet = Collections.unmodifiableSet(alpha);
    }
    public List<Clickstream> rows() { return rows; }
    public double totalWeight() { return totalWeight; }
    public Set<String> alphabet() { return alphabet; }
}