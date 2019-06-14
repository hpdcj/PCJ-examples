/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import java.util.Random;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author faramir
 */
@RegisterStorage(Step9_Pi.Shared.class)
public class Step9_Pi implements StartPoint {

    @Storage(Step9_Pi.class)
    enum Shared {
        randArray
    }
    private final int[] randArray = new int[PCJ.threadCount()];

    @Override
    public void main() throws Throwable {
        Random random = new Random();
        int count = 0;
        for (int i = 0; i < 1_000_000; ++i) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            if (x * x + y * y < 1) {
                ++count;
            }
        }

        PCJ.put(count, 0, Shared.randArray, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(Shared.randArray, PCJ.threadCount());

            int sum = 0;
            for (int r : randArray) {
                sum += r;
            }
            System.out.println("sum = " + sum);
            System.out.println("PI = " + (4. * sum / 1_000_000 / PCJ.threadCount()));
        }
    }

    public static void main(String[] args) {
        PCJ.executionBuilder(Step9_Pi.class)
                .addNode("localhost")
                .addNode("localhost")
                .addNode("localhost:8090")
                .start();
    }
}
