/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import java.io.Serializable;
import java.util.Random;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 * @author faramir
 */
@RegisterStorage(StepB_CustomClass.Shared.class)
public class StepB_CustomClass implements StartPoint {

    private static class Result implements Serializable {

        long hit;
        long nohit;
    }

    @Storage(StepB_CustomClass.class)
    enum Shared {
        array,
        seconds
    }

    private int seconds;
    private final Result[] array = new Result[PCJ.threadCount()];

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            PCJ.broadcast(10, Shared.seconds);
        }
        PCJ.waitFor(Shared.seconds);
        Random random = new Random();
        Result result = new Result();
        long duration = seconds * 1_000_000_000L;
        long startTime = System.nanoTime();
        while ((System.nanoTime() - startTime) < duration) {
            double x = random.nextDouble();
            double y = random.nextDouble();
            if (x * x + y * y < 1) {
                ++result.hit;
            } else {
                ++result.nohit;
            }
        }

        PCJ.put(result, 0, Shared.array, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(Shared.array, PCJ.threadCount());

            long hit = 0;
            long nohit = 0;
            for (Result r : array) {
                hit += r.hit;
                nohit += r.nohit;
            }
            System.out.println("PI = " + (4. * hit / (hit + nohit)));
        }
    }

    public static void main(String[] args) {
        PCJ.executionBuilder(StepB_CustomClass.class)
                .addNode("localhost")
                .addNode("localhost")
                .addNode("localhost:8090")
                .start();
    }
}
