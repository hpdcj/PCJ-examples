/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.StartPoint;

/**
 *
 * @author faramir
 */
public class Step4_Barrier implements StartPoint {

    @Override
    public void main() throws Throwable {
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Hello from " + PCJ.myId());
            }
            PCJ.barrier();
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(Step4_Barrier.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost:8090",}));
    }
}
