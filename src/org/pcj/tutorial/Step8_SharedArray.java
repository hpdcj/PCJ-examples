/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import java.util.Random;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.PcjFuture;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author faramir
 */
@RegisterStorage(Step8_SharedArray.Shared.class)
public class Step8_SharedArray implements StartPoint {

    @Storage(Step8_SharedArray.class)
    enum Shared {
        randArray
    }
    private final int[] randArray = new int[PCJ.threadCount()];

    @Override
    public void main() throws Throwable {
        int rand = new Random().nextInt(PCJ.threadCount());
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId()
                        + " with value " + rand);
            }
            PCJ.barrier();
        }
        PCJ.put(rand, 0, Shared.randArray, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(Shared.randArray, PCJ.threadCount());

            int sum = 0;
            for (int r : randArray) {
                sum += r;
            }
            System.out.println("sum = " + sum);
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(Step8_SharedArray.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost:8090",}));
    }
}
