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
@RegisterStorage(Step7_AsyncGet.Shared.class)
public class Step7_AsyncGet implements StartPoint {

    @Storage(Step7_AsyncGet.class)
    enum Shared {
        rand
    }
    private double rand;

    @Override
    public void main() throws Throwable {
        rand = new Random().nextDouble();
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId()
                        + " with value " + rand);
            }
            PCJ.barrier();
        }
        if (PCJ.myId() == 0) {
            PcjFuture<Double>[] futures = new PcjFuture[PCJ.threadCount()];
            for (int i = 0; i < PCJ.threadCount(); i++) {
                futures[i] = PCJ.asyncGet(i, Shared.rand);
            }
            
            double sum = 0;
            for (PcjFuture<Double> future : futures) {
                sum += future.get();
            }
            System.out.println("sum = " + sum);
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(Step7_AsyncGet.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost:8090",}));
    }
}
