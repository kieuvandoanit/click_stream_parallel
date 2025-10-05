package org.wcpm.miner;
import org.wcpm.model.CDB;
import java.util.*;

public interface PatternMiner {
    Set<List<String>> mine(CDB cdb, double minWs);
    String name();
}
