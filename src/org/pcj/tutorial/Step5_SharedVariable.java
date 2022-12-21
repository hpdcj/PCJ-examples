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
@RegisterStorage(Step5_SharedVariable.Shared.class)
public class Step5_SharedVariable implements StartPoint {

    @Storage(Step5_SharedVariable.class)
    enum Shared {
        rand
    }
    private int rand;

    @Override
    public void main() throws Throwable {
        rand = new Random().nextInt(PCJ.threadCount());
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId());
            }
            PCJ.barrier();
        }
    }

    public static void main(String[] args) {
        PCJ.executionBuilder(Step5_SharedVariable.class)
                .addNode("localhost")
                .addNode("localhost")
                .addNode("localhost:8090")
                .deploy();
    }
}
