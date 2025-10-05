package org.wcpm.model;
import java.util.List;

public record Clickstream(int cid, List<String> seq, double weight) {}