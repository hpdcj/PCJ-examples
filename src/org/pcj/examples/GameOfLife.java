/* 
 * Copyright (c) 2017, Marek Nowicki
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.pcj.examples;

import java.util.BitSet;
import java.util.Random;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.PcjFuture;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
@RegisterStorage(GameOfLife.Shared.class)
public class GameOfLife implements StartPoint {

    private static final int STEPS = Integer.parseInt(System.getProperty("maxSteps", "10"));
    private static final int COLS = Integer.parseInt(System.getProperty("cols", "8000"));
    private static final int ROWS = Integer.parseInt(System.getProperty("rows", "6000"));
    private static final String SEED = System.getProperty("seed");

    private final int threadsPerRow;
    private final int threadsPerCol;

    {
        if (Boolean.parseBoolean(System.getProperty("onlyRows", "false"))) {
            threadsPerRow = 1;
            threadsPerCol = PCJ.threadCount();
        } else {
            int midPoint;
            for (midPoint = (int) Math.sqrt(PCJ.threadCount()); midPoint > 1; midPoint--) {
                if (PCJ.threadCount() % midPoint == 0) {
                    break;
                }
            }
            int factor = PCJ.threadCount() / midPoint;
            if (COLS % midPoint == 0 && ROWS % factor == 0) {
                threadsPerRow = midPoint;
                threadsPerCol = factor;
            } else if (COLS % factor == 0 && ROWS % midPoint == 0) {
                threadsPerRow = factor;
                threadsPerCol = midPoint;
            } else if (COLS % midPoint == 0) {
                threadsPerRow = midPoint;
                threadsPerCol = factor;
            } else {
                threadsPerRow = factor;
                threadsPerCol = midPoint;
            }
        }
    }

    private final int rowsPerThread = ROWS / threadsPerCol;
    private final int colsPerThread = COLS / threadsPerRow;
    private final boolean isFirstColumn = PCJ.myId() % threadsPerRow == 0;
    private final boolean isLastColumn = PCJ.myId() % threadsPerRow == threadsPerRow - 1;
    private final boolean isFirstRow = PCJ.myId() < threadsPerRow;
    private final boolean isLastRow = PCJ.myId() >= PCJ.threadCount() - threadsPerRow;
    private final Board[] boards = new Board[2];

    {
        for (int i = 0; i < boards.length; ++i) {
            boards[i] = new Board(rowsPerThread, colsPerThread);
        }
    }

    @Storage(GameOfLife.SharedClass.class)
    enum Shared {
        N, S, E, W, NE, NW, SE, SW
    }

    private static class SharedClass {

        public SharedClass() {
        }

        BitSet N;
        BitSet S;
        BitSet E;
        BitSet W;
        boolean NE;
        boolean NW;
        boolean SE;
        boolean SW;
    }

    private final SharedClass recvShared = (SharedClass) PCJ.getStorageObject(Shared.class);
    private final SharedClass sendShared;

    {
        sendShared = new SharedClass();
        sendShared.N = new BitSet(colsPerThread);
        sendShared.S = new BitSet(colsPerThread);
        sendShared.E = new BitSet(rowsPerThread);
        sendShared.W = new BitSet(rowsPerThread);
    }

    private int step;

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            System.out.printf("Size = %dx%d (%dx%d)\n", COLS, ROWS, colsPerThread, rowsPerThread);
            System.out.printf("Threads = %d (%d nodes)\n", PCJ.threadCount(), PCJ.getNodeCount());
            System.out.printf("ThreadsPerRow = %d\n", threadsPerRow);
            System.out.printf("ThreadsPerCol = %d\n", threadsPerCol);
            System.out.printf("MaxSteps = %d\n", STEPS);
            System.out.printf("Seed = %s\n", SEED);
        }

        init();

        PcjFuture<Void> barrier = PCJ.asyncBarrier();

        long cellsPerStep = (long) rowsPerThread * colsPerThread * PCJ.threadCount();
        long lastTime;
        long lastCells = 0;
        long nowCells = 0;
        long startTime = lastTime = System.nanoTime();
        for (step = 0; step <= STEPS; ++step) {
            step();

            barrier.get();
            exchange();

            barrier = PCJ.asyncBarrier();

            if (PCJ.myId() == 0) {
                nowCells += cellsPerStep;

                long nowTime = System.nanoTime();
                long deltaTime = nowTime - lastTime;

                if (deltaTime > 1_000_000_000) {
                    long deltaCells = nowCells - lastCells;
                    double rate = (double) deltaCells / (deltaTime / 1e9);
                    if (deltaCells > 0) {
                        lastCells = nowCells;
                        lastTime = nowTime;

                        System.out.printf("%.0f cells/s\n", rate);
                    }
                }

            }
        }

        if (PCJ.myId() == 0) {
            long nowTime = System.nanoTime();
            long deltaTime = nowTime - startTime;

            double rate = (double) nowCells / (deltaTime / 1e9);

            System.out.printf("%d threads on %d nodes processed %d cells in %.3f seconds (%.0f cells/s)\n", PCJ.threadCount(), PCJ.getNodeCount(), nowCells, deltaTime / 1e9, rate);
        }
    }

    private void init() {
        step = -1;

        Board board = boards[0];

//        if (PCJ.myId() == 0) {
//            String[] plansza = {
//                ".X.",
//                "..X",
//                "XXX"
//            };
//            for (int y = 0; y < plansza.length; y++) {
//                for (int x = 0; x < plansza[y].length(); x++) {
//                    if (plansza[y].charAt(x) != '.') {
//                        board.set(1 + colsPerThread - plansza[y].length() + x, 1 + rowsPerThread - plansza.length + y, true);
//                    }
//                }
//            }
//        }
        Random rand = new Random();
        if (SEED != null) {
            rand.setSeed(Long.parseLong(SEED));
        }

        for (int y = 1; y <= rowsPerThread; y++) {
            for (int x = 1; x <= colsPerThread; x++) {
                board.set(x, y, rand.nextDouble() <= 0.15);
            }
        }

        exchange();
        step = 0;
    }

    private void exchange() {
        int nextStep = (step + 1) % 2;
        Board board = boards[nextStep];

        /* sending */
        if (!isFirstColumn) {
            for (int row = 1; row <= rowsPerThread; ++row) {
                sendShared.W.set(row - 1, board.get(1, row));
            }
            PCJ.asyncPut(sendShared.W, PCJ.myId() - 1, Shared.E);
        }

        if (!isLastColumn) {
            for (int row = 1; row <= rowsPerThread; ++row) {
                sendShared.E.set(row - 1, board.get(colsPerThread, row));
            }
            PCJ.asyncPut(sendShared.E, PCJ.myId() + 1, Shared.W);
        }

        if (!isFirstRow) {
            for (int col = 1; col <= colsPerThread; ++col) {
                sendShared.N.set(col - 1, board.get(col, 1));
            }
            PCJ.asyncPut(sendShared.N, PCJ.myId() - threadsPerRow, Shared.S);
        }
        if (!isLastRow) {
            for (int col = 1; col <= colsPerThread; ++col) {
                sendShared.S.set(col - 1, board.get(col, rowsPerThread));
            }
            PCJ.asyncPut(sendShared.S, PCJ.myId() + threadsPerRow, Shared.N);
        }

        if (!isFirstColumn && !isFirstRow) {
            sendShared.NW = board.get(1, 1);
            PCJ.asyncPut(sendShared.NW, PCJ.myId() - threadsPerRow - 1, Shared.SE);
        }
        if (!isFirstColumn && !isLastRow) {
            sendShared.SW = board.get(1, rowsPerThread);
            PCJ.asyncPut(sendShared.SW, PCJ.myId() + threadsPerRow - 1, Shared.NE);
        }
        if (!isLastColumn && !isFirstRow) {
            sendShared.NE = board.get(colsPerThread, 1);
            PCJ.asyncPut(sendShared.NE, PCJ.myId() - threadsPerRow + 1, Shared.SW);
        }
        if (!isLastColumn && !isLastRow) {
            sendShared.SE = board.get(colsPerThread, rowsPerThread);
            PCJ.asyncPut(sendShared.SE, PCJ.myId() + threadsPerRow + 1, Shared.NW);
        }

        /* receiving */
        if (!isLastColumn) {
            PCJ.waitFor(Shared.E);
            for (int row = 1; row <= rowsPerThread; ++row) {
                board.set(colsPerThread + 1, row, recvShared.E.get(row - 1));
            }
        }
        if (!isFirstColumn) {
            PCJ.waitFor(Shared.W);
            for (int row = 1; row <= rowsPerThread; ++row) {
                board.set(0, row, recvShared.W.get(row - 1));
            }
        }
        if (!isLastRow) {
            PCJ.waitFor(Shared.S);
            for (int col = 1; col <= colsPerThread; ++col) {
                board.set(col, rowsPerThread + 1, recvShared.S.get(col - 1));
            }
        }
        if (!isFirstRow) {
            PCJ.waitFor(Shared.N);
            for (int col = 1; col <= colsPerThread; ++col) {
                board.set(col, 0, recvShared.N.get(col - 1));
            }
        }

        if (!isLastColumn && !isLastRow) {
            PCJ.waitFor(Shared.SE);
            board.set(colsPerThread + 1, rowsPerThread + 1, recvShared.SE);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.waitFor(Shared.NE);
            board.set(colsPerThread + 1, 0, recvShared.NE);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.waitFor(Shared.SW);
            board.set(0, rowsPerThread + 1, recvShared.SW);
        }
        if (!isFirstColumn && !isFirstRow) {
            PCJ.waitFor(Shared.NW);
            board.set(0, 0, recvShared.NW);
        }
    }

    private void printBoards() {
        Board board = boards[step % 2];
        for (int i = 0; i < PCJ.threadCount(); i++) {
            if (PCJ.myId() == i) {
                System.out.println("Id: " + PCJ.myId());
                for (int row = 0; row <= rowsPerThread + 1; row++) {
                    for (int col = 0; col <= colsPerThread + 1; col++) {
                        System.out.print(board.get(col, row) ? 'X' : '.');
                    }
                    System.out.println();
                }
                System.out.println();
            }
            PCJ.barrier();
        }
    }

    private void printWholeBoard() {
        Board board = boards[step % 2];

        for (int globalRow = 0; globalRow < rowsPerThread * threadsPerCol; globalRow++) {
            for (int colThread = 0; colThread < threadsPerRow; colThread++) {
                if (PCJ.myId() / threadsPerCol == globalRow / rowsPerThread && colThread == PCJ.myId() % threadsPerRow) {
                    for (int col = 1; col <= colsPerThread; col++) {
                        System.out.print(board.get(col, globalRow % rowsPerThread + 1) ? 'X' : '.');
                    }
                    if (isLastColumn) {
                        System.out.println();
                    }
                }
                PCJ.barrier();
            }
        }

    }

    private void step() {
        Board currentBoard = boards[step % 2];
        Board nextBoard = boards[(step + 1) % 2];

        for (int x = 1; x <= colsPerThread; ++x) {
            for (int y = 1; y <= rowsPerThread; ++y) {
                int neightbours = countNeightbours(currentBoard, x, y);
                switch (neightbours) {
                    case 2:
                        // survive (if alive)
                        nextBoard.set(x, y, currentBoard.get(x, y));
                        break;
                    case 3:
                        // born (or survive)
                        nextBoard.set(x, y, true);
                        break;
                    default:
                        // under- or overpopulated - die
                        nextBoard.set(x, y, false);
                        break;
                }
            }
        }
    }

    private int countNeightbours(Board board, int x, int y) {
        return (board.get(x - 1, y - 1) ? 1 : 0)
                + (board.get(x - 1, y) ? 1 : 0)
                + (board.get(x - 1, y + 1) ? 1 : 0)
                + (board.get(x, y - 1) ? 1 : 0)
                + (board.get(x, y + 1) ? 1 : 0)
                + (board.get(x + 1, y - 1) ? 1 : 0)
                + (board.get(x + 1, y) ? 1 : 0)
                + (board.get(x + 1, y + 1) ? 1 : 0);
    }

    public static void main(String[] args) throws Throwable {
//        PCJ.start(GameOfLife.class, new NodesDescription("nodes.txt"));
        PCJ.deploy(GameOfLife.class,
                new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost",
            "localhost", //            "localhost",
        //            "localhost",
        //            "localhost",
        //            "localhost",
        //            "localhost",
        }));
    }

    private static class Board {

        private final BitSet[] rows;

        public Board(int rowCount, int colCount) {
            rows = new BitSet[rowCount + 2];
            for (int i = 0; i < rows.length; i++) {
                rows[i] = new BitSet(colCount + 2);
            }
        }

        public boolean get(int x, int y) {
            return rows[y].get(x);
        }

        public void flip(int x, int y) {
            rows[y].flip(x);
        }

        public void set(int x, int y, boolean v) {
            rows[y].set(x, v);
        }
    }
}
