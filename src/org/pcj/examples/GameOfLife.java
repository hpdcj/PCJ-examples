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

import java.util.Arrays;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
@RegisterStorage(GameOfLife.Shared.class)
public class GameOfLife implements StartPoint {

    @Storage(GameOfLife.class)
    enum Shared {
        board
    }
    private static final int N = 5;
    private static final int STEPS = 44;
    private final boolean[][] board = new boolean[N + 2][N + 2];

    @Override
    public void main() throws Throwable {
        init();
        print();
        if (PCJ.myId() == 0) {
            System.out.println("---");
        }

        for (int step = 1; step <= STEPS; ++step) {
            exchange();
            step();
            PCJ.barrier();
        }
        print();
    }

    private void init() {
        /*
         * .....
         * ..X..
         * ...X.
         * .XXX.
         * .....
         */
        if (PCJ.myId() == 0) {
            board[2][3] = true;
            board[3][4] = true;
            board[4][2] = true;
            board[4][3] = true;
            board[4][4] = true;
        }
    }

    private void exchange() {
        /*
         *  0 |  1 |  2 |  3
         *  4 |  5 |  6 |  7
         *  8 |  9 | 10 | 11
         * 12 | 13 | 14 | 15
         */
        int n = (int) Math.sqrt(PCJ.threadCount());
        boolean isFirstColumn = PCJ.myId() % n == 0;
        boolean isLastColumn = PCJ.myId() % n == n - 1;
        boolean isFirstRow = PCJ.myId() < n;
        boolean isLastRow = PCJ.myId() >= PCJ.threadCount() - n;

        if (!isFirstColumn) {
            for (int row = 1; row <= N; ++row) {
                PCJ.asyncPut(board[row][1], PCJ.myId() - 1, Shared.board, row, N + 1);
            }
        }
        if (!isLastColumn) {
            for (int row = 1; row <= N; ++row) {
                PCJ.asyncPut(board[row][N], PCJ.myId() + 1, Shared.board, row, 0);
            }
        }

        if (!isFirstRow) {
            for (int col = 1; col <= N; ++col) {
                PCJ.asyncPut(board[1][col], PCJ.myId() - n, Shared.board, N + 1, col);
            }
        }
        if (!isLastRow) {
            for (int col = 1; col <= N; ++col) {
                PCJ.asyncPut(board[N][col], PCJ.myId() + n, Shared.board, 0, col);
            }
        }

        if (!isFirstColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][1], PCJ.myId() - n - 1, Shared.board, N + 1, N + 1);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.asyncPut(board[N][1], PCJ.myId() + n - 1, Shared.board, 0, N + 1);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][N], PCJ.myId() - n + 1, Shared.board, N + 1, 0);
        }
        if (!isLastColumn && !isLastRow) {
            PCJ.asyncPut(board[N][N], PCJ.myId() + n + 1, Shared.board, 0, 0);
        }

        if (!isLastColumn) {
            PCJ.waitFor(Shared.board, N);
        }
        if (!isFirstColumn) {
            PCJ.waitFor(Shared.board, N);
        }
        if (!isLastRow) {
            PCJ.waitFor(Shared.board, N);
        }
        if (!isFirstRow) {
            PCJ.waitFor(Shared.board, N);
        }

        if (!isLastColumn && !isLastRow) {
            PCJ.waitFor(Shared.board);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.waitFor(Shared.board);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.waitFor(Shared.board);
        }
        if (!isFirstColumn && !isFirstRow) {
            PCJ.waitFor(Shared.board);
        }
    }

    private void print() {
        int n = (int) Math.sqrt(PCJ.threadCount());
        for (int row = 0; row < n * N; ++row) {
            for (int col = 0; col < n * N; ++col) {
                PCJ.barrier();
                if (row / N == PCJ.myId() / n && col / N == PCJ.myId() % n) {
                    System.out.print(board[row % N + 1][col % N + 1] ? 'X' : '.');
                    if (col + 1 == n * N) {
                        System.out.println();
                    }
                }
                PCJ.barrier();
            }
        }
    }

    private boolean[][] copy(boolean[][] src) {
        boolean[][] copy = new boolean[src.length][];
        for (int i = 0; i < copy.length; ++i) {
            copy[i] = Arrays.copyOf(src[i], src[i].length);
        }
        return copy;
    }

    private void step() {
        boolean[][] old = copy(board);
        for (int x = 1; x <= N; ++x) {
            for (int y = 1; y <= N; ++y) {
                int neightbours = countNeightbours(old, x, y);
                switch (neightbours) {
                    case 2:
                        // survive (if alive)
                        board[x][y] = old[x][y];
                        break;
                    case 3:
                        // born (or survive)
                        board[x][y] = true;
                        break;
                    default:
                        // under- or overpopulated - die
                        board[x][y] = false;
                        break;
                }
            }
        }
    }

    private int countNeightbours(boolean[][] board, int x, int y) {
        return (board[x - 1][y - 1] ? 1 : 0)
                + (board[x - 1][y] ? 1 : 0)
                + (board[x - 1][y + 1] ? 1 : 0)
                + (board[x][y - 1] ? 1 : 0)
                + (board[x][y + 1] ? 1 : 0)
                + (board[x + 1][y - 1] ? 1 : 0)
                + (board[x + 1][y] ? 1 : 0)
                + (board[x + 1][y + 1] ? 1 : 0);
    }

    public static void main(String[] args) {
        PCJ.deploy(GameOfLife.class, new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",}));
    }
}
