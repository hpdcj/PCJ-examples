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
        boards
    }
    private static final int N = 5;
    private static final int STEPS = 43;
    private final boolean[][][] boards = new boolean[2][N + 2][N + 2];
    private int step = 0;

    @Override
    public void main() throws Throwable {
        init();
        exchange();

        print();
        PCJ.barrier();

        while (step <= STEPS) {
            step();
            exchange();

//            if (PCJ.myId() == 0) {
//                System.out.println("-----------");
//            }
            PCJ.barrier();

            ++step;
//            print();
        }

        if (PCJ.myId() == 0) {
            System.out.println("-----------");
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
        step = -1;
        boolean[][] board = boards[0];
        if (PCJ.myId() == 0) {
            board[2][3] = true;
            board[3][4] = true;
            board[4][2] = true;
            board[4][3] = true;
            board[4][4] = true;
        }
        exchange();

        step = 0;
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

        boolean[][] board = boards[(step + 1) % 2];
        int nextStep = (step + 1) % 2;

        if (!isFirstColumn) {
            for (int row = 1; row <= N; ++row) {
                PCJ.asyncPut(board[row][1], PCJ.myId() - 1, Shared.boards, nextStep, row, N + 1);
            }
        }
        if (!isLastColumn) {
            for (int row = 1; row <= N; ++row) {
                PCJ.asyncPut(board[row][N], PCJ.myId() + 1, Shared.boards, nextStep, row, 0);
            }
        }

        if (!isFirstRow) {
            for (int col = 1; col <= N; ++col) {
                PCJ.asyncPut(board[1][col], PCJ.myId() - n, Shared.boards, nextStep, N + 1, col);
            }
        }
        if (!isLastRow) {
            for (int col = 1; col <= N; ++col) {
                PCJ.asyncPut(board[N][col], PCJ.myId() + n, Shared.boards, nextStep, 0, col);
            }
        }

        if (!isFirstColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][1], PCJ.myId() - n - 1, Shared.boards, nextStep, N + 1, N + 1);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.asyncPut(board[N][1], PCJ.myId() + n - 1, Shared.boards, nextStep, 0, N + 1);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][N], PCJ.myId() - n + 1, Shared.boards, nextStep, N + 1, 0);
        }
        if (!isLastColumn && !isLastRow) {
            PCJ.asyncPut(board[N][N], PCJ.myId() + n + 1, Shared.boards, nextStep, 0, 0);
        }

        if (!isLastColumn) {
            PCJ.waitFor(Shared.boards, N);
        }
        if (!isFirstColumn) {
            PCJ.waitFor(Shared.boards, N);
        }
        if (!isLastRow) {
            PCJ.waitFor(Shared.boards, N);
        }
        if (!isFirstRow) {
            PCJ.waitFor(Shared.boards, N);
        }

        if (!isLastColumn && !isLastRow) {
            PCJ.waitFor(Shared.boards);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.waitFor(Shared.boards);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.waitFor(Shared.boards);
        }
        if (!isFirstColumn && !isFirstRow) {
            PCJ.waitFor(Shared.boards);
        }
    }

    private void print() {
        boolean[][] board = boards[step % 2];
        int n = (int) Math.sqrt(PCJ.threadCount());
        for (int row = 0; row < n * N; ++row) {
            for (int col = 0; col < n * N; ++col) {
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

    private void step() {
        boolean[][] currentBoard = boards[step % 2];
        boolean[][] nextBoard = boards[(step + 1) % 2];
        for (int x = 1; x <= N; ++x) {
            for (int y = 1; y <= N; ++y) {
                int neightbours = countNeightbours(currentBoard, x, y);
                switch (neightbours) {
                    case 2:
                        // survive (if alive)
                        nextBoard[x][y] = currentBoard[x][y];
                        break;
                    case 3:
                        // born (or survive)
                        nextBoard[x][y] = true;
                        break;
                    default:
                        // under- or overpopulated - die
                        nextBoard[x][y] = false;
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
