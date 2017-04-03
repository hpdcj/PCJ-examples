package org.pcj.examples;


import org.pcj.*;

@RegisterStorage(App.Shared.class)
public class App implements StartPoint {

    @Storage(App.class)
    enum Shared { tablica }
    
    private int[] tablica = new int[PCJ.threadCount()];
    private double avg;
    
    @Override
    public void main() {
        System.out.println("Hello from " + PCJ.myId());
    }
    
    public static void main(String[] args) {
        String[] nodes = new String[]{"host0", "host0",
            "host1", "host1", "host2", "host2"};
        PCJ.deploy(App.class, new NodesDescription(nodes));
    }
}
