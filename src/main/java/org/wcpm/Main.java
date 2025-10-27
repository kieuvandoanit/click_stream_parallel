package org.wcpm;
import org.wcpm.io.CsvReader;
import org.wcpm.model.CDB;
import org.wcpm.miner.PatternMiner;
import org.wcpm.miner.serial.CompactSpadeSerial;
import org.wcpm.miner.parallel.*;

import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
//        Path toy = Path.of("src/main/resources/datasets/toy.csv");
        Path large_clickstream_dataset = Path.of("src/main/resources/datasets/large_clickstream_dataset.csv");
        CDB cdb = CsvReader.readToy(large_clickstream_dataset);
        double minWs = 0.02;
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors()-1);

        List<PatternMiner> miners = List.of(
                new CompactSpadeSerial(),
                new HPCompactSpade(threads),
                new DPCompactSpade(threads),
                new APCompactSpade(threads, 0.70)
        );

        for (PatternMiner m : miners) {
            long t0 = System.nanoTime();
            var F = m.mine(cdb, minWs);
            long t1 = System.nanoTime();
            System.out.printf("[%s] patterns=%d, time=%.2f ms%n", m.name(), F.size(), (t1-t0)/1e6);
            // In vài mẫu để kiểm chứng
//            F.stream().sorted(Comparator.<List<String>>comparingInt(List::size).thenComparing(Object::toString))
//                    .limit(10).forEach(p -> System.out.println("  " + p));
        }
    }
}