package org.wcpm.io;
import org.wcpm.model.*;
import java.nio.file.*; import java.util.*;
public class CsvReader {
    public static CDB readToy(Path path) throws Exception {
        List<Clickstream> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            line = line.trim(); if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split(",", 3);
            int cid = Integer.parseInt(parts[0].trim());
            double w = Double.parseDouble(parts[1].trim());
            List<String> seq = Arrays.asList(parts[2].trim().split("\\s+"));
            rows.add(new Clickstream(cid, seq, w));
        }
        return new CDB(rows);
    }
}