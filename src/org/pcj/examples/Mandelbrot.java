package org.pcj.examples;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage
public class Mandelbrot implements StartPoint {
    private final int COLS = 145 - 1;
    private final int ROWS = 45 - 1;
    private final int N = 100;
    private final double xMin = -2.0;
    private final double xMax = 1.0;
    private final double yMin = -1.0;
    private final double yMax = 1.0;

    @Storage
    enum Vars {
        output
    }

    private String[] output = PCJ.myId() == 0 ? new String[ROWS + 1] : null;

    public static void main(String[] args) throws IOException {
        PCJ.executionBuilder(Mandelbrot.class)
                .addNodes(new File("nodes.txt"))
                .deploy();
    }

    @Override
    public void main() throws Throwable {
        for (int y = PCJ.myId(); y <= ROWS; y += PCJ.threadCount()) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x <= COLS; ++x) {
                Complex p = new Complex(
                        (double) x / COLS * (xMax - xMin) + xMin,
                        (double) (ROWS - y) / ROWS * (yMax - yMin) + yMin);
                sb.append(isMandelbrot(p) ? '#' : ' ');
            }
            PCJ.asyncPut(sb.toString(), 0, Vars.output, y);
        }

        if (PCJ.myId() == 0) {
            PCJ.waitFor(Vars.output, ROWS + 1);
            for (String line : output) {
                System.out.println(line);
            }
        }
    }

    private boolean isMandelbrot(Complex p) {
        Complex z = new Complex(0, 0);
        for (int n = 0; n < N; ++n) {
            if (z.abs() >= 2.0) {
                return false;
            }
            z = z.mul(z).add(p);
        }
        return z.abs() < 2.0;
    }
}

record Complex(double re, double im) implements Serializable {
    public Complex add(Complex that) {
        return new Complex( // (a+ib)+(c+id) = (a+c) + i(b+d)
                this.re + that.re,
                this.im + that.im);
    }

    public Complex mul(Complex that) {
        return new Complex( // (a+ib)*(c+id) = ac + iad + ibc - bd = (ac-bd) + i(ad+bc)
                this.re * that.re - this.im * that.im,
                this.re * that.im + this.im * that.re);
    }

    public double abs() {
        return Math.hypot(this.re, this.im);
    }
}
