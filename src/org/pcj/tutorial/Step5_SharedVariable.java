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
@RegisterStorage(Step5_SharedVariable.Shared.class)
public class Step5_SharedVariable implements StartPoint {

    @Storage(Step5_SharedVariable.class)
    enum Shared {
        rand
    }
    private double rand;

    @Override
    public void main() throws Throwable {
        rand = new Random().nextDouble();
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId());
            }
            PCJ.barrier();
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(Step5_SharedVariable.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost:8090",}));
    }
}
