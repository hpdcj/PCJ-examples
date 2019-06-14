/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pcj.tutorial;

import org.pcj.PCJ;
import org.pcj.StartPoint;

/**
 *
 * @author faramir
 */
public class Step3_MyId implements StartPoint {

    @Override
    public void main() throws Throwable {
        System.out.println("Hello from " + PCJ.myId());
    }

    public static void main(String[] args) {
        PCJ.executionBuilder(Step3_MyId.class)
                .addNode("localhost")
                .addNode("localhost")
                .addNode("localhost:8090")
                .start();
    }
}
