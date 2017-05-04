/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import java.util.Random;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author faramir
 */
@RegisterStorage(StepA_PiBroadcast.Shared.class)
public class StepA_PiBroadcast implements StartPoint {

    @Storage(StepA_PiBroadcast.class)
    enum Shared {
        array,
        points
    }
    private int points;
    private final int[] array = new int[PCJ.threadCount()];

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            PCJ.broadcast(10_000_000, Shared.points);
        }
        PCJ.waitFor(Shared.points);
        Random random = new Random();
        int count = 0;
        for (int i = 0; i < points; ++i) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            if (x * x + y * y < 1) {
                ++count;
            }
        }

        PCJ.put(count, 0, Shared.array, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(Shared.array, PCJ.threadCount());

            long sum = 0;
            for (int r : array) {
                sum += r;
            }
            System.out.println("PI = " + (4. * sum / points / PCJ.threadCount()));
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(StepA_PiBroadcast.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost:8090",}));
    }
}
