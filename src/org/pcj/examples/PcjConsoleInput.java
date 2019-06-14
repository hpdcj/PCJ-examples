package org.pcj.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import org.pcj.PCJ;
import org.pcj.StartPoint;
import org.pcj.Storage;
import org.pcj.RegisterStorage;

@RegisterStorage(PcjConsoleInput.Shared.class)
public class PcjConsoleInput implements StartPoint {

    @Storage(PcjConsoleInput.class)
    enum Shared {
        a
    }

    public int a = Integer.MIN_VALUE;

    public static void main(String[] args) throws IOException {
        String[] nodes;
        if (args.length == 0) {
            nodes = new String[]{"localhost","localhost:8099"};
        } else {
            nodes = Files.readAllLines(Paths.get(args[0])).stream().toArray(String[]::new);
        }
        
        PCJ.executionBuilder(PcjConsoleInput.class)
                .addNodes(nodes)
                .deploy();
    }

    @Override
    public void main() throws Throwable {
        Scanner stdin = new Scanner(System.in);

        PCJ.monitor(Shared.a);
        if (PCJ.myId() == 0) {
            int b = stdin.nextInt();
            PCJ.broadcast(b, Shared.a);
        }
        PCJ.waitFor(Shared.a);

        System.out.println("a = " + a);
    }
}
