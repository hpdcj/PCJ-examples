package org.pcj.examples;

import java.io.IOException;
import java.util.Random;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage(LoopOfUpdates.Shared.class)
public class LoopOfUpdates implements StartPoint {

    @Storage(LoopOfUpdates.class)
    enum Shared {
        table
    }

    private long[] table;

    public static void main(String[] args) throws IOException {
        String[] nodes = {
                "localhost",
                "localhost:8001",
//                    "localhost:8002",
//                    "localhost:8003"
        };
        PCJ.executionBuilder(LoopOfUpdates.class)
                .addNodes(nodes)
                .deploy();

    }

    @Override
    public void main() throws Throwable {
        int localN = 1 << 10;
        Random random = new Random(0);

        table = random.longs(localN).toArray();
//        if (PCJ.myId() == 1) {
//            PCJ.put(table, 0, Shared.table);
//        }

        if (PCJ.myId() == 1) {
            System.err.println("waitFor = " + PCJ.waitFor(Shared.table, 0));
        }
        if (PCJ.myId() == 0) {
            System.err.println("localN = " + localN);
        }
        PCJ.barrier();

        try {
            if (PCJ.myId() == 0) {
                for (int i = 0; i < 20000; ++i) {
                    int destinationId = 1;//random.nextInt(PCJ.threadCount());
                    PCJ.asyncPut(random.nextLong(), destinationId, Shared.table, random.nextInt(localN));
                }
            }
            Thread.sleep(5_000);
        } catch (OutOfMemoryError e) {
            System.err.println(PCJ.myId() + " -> " + e);
        }

        PCJ.barrier();
        if (PCJ.myId() == 1) {
            System.err.println("waitFor = " + PCJ.waitFor(Shared.table, 0));
        }
    }
}
