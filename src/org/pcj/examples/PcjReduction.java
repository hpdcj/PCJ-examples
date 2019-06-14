package org.pcj.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.IntStream;
import org.pcj.PCJ;
import org.pcj.PcjFuture;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage(PcjReduction.Shared.class)
public class PcjReduction implements StartPoint {

    @Storage(PcjReduction.class)
    enum Shared {
        a
    }

    public long a;

    public static void main(String[] args) throws IOException {
        String[] nodes;
        if (args.length == 0) {
            nodes = IntStream.rangeClosed(1, 4).mapToObj(i -> "localhost").toArray(String[]::new);
        } else {
            nodes = Files.readAllLines(Paths.get(args[0])).stream().toArray(String[]::new);
        }
        PCJ.executionBuilder(PcjReduction.class)
                .addNodes(nodes)
                .deploy();
    }

    @Override
    public void main() throws Throwable {
        a = PCJ.myId() + 10;
        PCJ.barrier();

        PcjFuture aL[] = new PcjFuture[PCJ.threadCount()];

        long a0 = 0;
        if (PCJ.myId() == 0) {
            for (int p = 0; p < PCJ.threadCount(); p++) {
                aL[p] = PCJ.asyncGet(p, Shared.a);
            }
            for (int p = 0; p < PCJ.threadCount(); p++) {
                a0 = a0 + (long) aL[p].get();
            }
        }
        System.out.println("a0=" + a0);
    }
}
