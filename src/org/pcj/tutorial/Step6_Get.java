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
@RegisterStorage(Step6_Get.Shared.class)
public class Step6_Get implements StartPoint {

    @Storage(Step6_Get.class)
    enum Shared {
        rand
    }
    private int rand;

    @Override
    public void main() throws Throwable {
        rand = new Random().nextInt(PCJ.threadCount());
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId()
                        + " with value " + rand);
            }
            PCJ.barrier();
        }
        if (PCJ.myId() == 0) {
            int sum = 0;
            for (int i = 0; i < PCJ.threadCount(); i++) {
                sum += PCJ.<Integer>get(i, Shared.rand);
            }
            System.out.println("sum = " + sum);
        }
    }

    public static void main(String[] args) {
        PCJ.executionBuilder(Step6_Get.class)
                .addNode("localhost")
                .addNode("localhost")
                .addNode("localhost:8090")
                .start();
    }
}
