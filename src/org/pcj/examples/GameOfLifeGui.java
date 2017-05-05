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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import org.pcj.NodesDescription;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

/**
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
@RegisterStorage(GameOfLifeGui.Shared.class)
public class GameOfLifeGui implements StartPoint {

    private final int N = 16;

    private void paintBoards() {
        boolean[][] board = boards[(step + 1) % 2];

        PCJ.asyncPut(board, 0, GuiBoard.guiBoard, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(GuiBoard.guiBoard, PCJ.threadCount());
            panel.repaint();
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException ex) {
//            }
            PCJ.broadcast(control, Shared.control);
        }
    }

    @Storage(GameOfLifeGui.class)
    enum GuiBoard {
        guiBoard
    }
    private final boolean[][][] guiBoard = PCJ.myId() == 0 ? new boolean[PCJ.threadCount()][N + 2][N + 2] : null;
    private JPanel panel;

    @Storage(GameOfLifeGui.class)
    enum Shared {
        boards,
        control
    }

    enum ControlEnum {
        PLAY,
        PAUSE,
        STOP
    }
    private ControlEnum control = ControlEnum.PLAY;
    private final boolean[][][] boards = new boolean[2][N + 2][N + 2];
    /*
     *  0 |  1 |  2 |  3
     *  4 |  5 |  6 |  7
     *  8 |  9 | 10 | 11
     * 12 | 13 | 14 | 15
     */
    private final int threadsPerRow = (int) Math.sqrt(PCJ.threadCount());
    private final boolean isFirstColumn = PCJ.myId() % threadsPerRow == 0;
    private final boolean isLastColumn = PCJ.myId() % threadsPerRow == threadsPerRow - 1;
    private final boolean isFirstRow = PCJ.myId() < threadsPerRow;
    private final boolean isLastRow = PCJ.myId() >= PCJ.threadCount() - threadsPerRow;
    private int step = 0;

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            PCJ.registerStorage(GuiBoard.class, this);

            JFrame frame = new JFrame();
            panel = new JPanel() {

                @Override
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);

                    Graphics2D g2 = (Graphics2D) g.create();

                    int width = this.getWidth();
                    int height = this.getHeight();

                    g2.setColor(Color.gray);
                    g2.fillRect(0, 0, width, height);

                    int n = (int) Math.sqrt(PCJ.threadCount());

                    int cellWidth = width / (N * n);
                    int cellHeight = height / (N * n);

                    if (cellWidth < cellHeight) {
                        cellHeight = cellWidth;
                    } else {
                        cellWidth = cellHeight;
                    }

                    int dx = (width - cellWidth * N * n) / 2;
                    int dy = (height - cellHeight * N * n) / 2;

                    g2.setColor(Color.white);
                    g2.fillRect(dx, dy, width - dx * 2, height - dy * 2);

                    if (control == ControlEnum.PAUSE) {
                        g2.setColor(Color.gray);
                    } else {
                        g2.setColor(Color.black);
                    }
                    for (int row = 0; row < N * n; row++) {
                        for (int col = 0; col < N * n; col++) {
                            int id = row / N * n + col / N;
                            int x = row % N + 1;
                            int y = col % N + 1;
                            if (guiBoard[id][x][y]) {
                                g2.fillRect(dx + row * cellWidth, dy + col * cellHeight, cellWidth, cellHeight);
                            }
                        }
                    }
                    g2.dispose();
                }
            };
            frame.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        frame.dispose();
                        control = ControlEnum.STOP;
                        PCJ.broadcast(control, Shared.control);
                    }
                }
            });

            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        if (!control.equals(ControlEnum.PAUSE)) {
                            control = ControlEnum.PAUSE;
                            panel.repaint();
                            return;
                        }
                    } else {
                        control = ControlEnum.PLAY;
                        PCJ.broadcast(control, Shared.control);
                        return;
                    }

                    JPanel panel = (JPanel) e.getSource();

                    int width = panel.getWidth();
                    int height = panel.getHeight();

                    int n = (int) Math.sqrt(PCJ.threadCount());

                    int cellWidth = width / (N * n);
                    int cellHeight = height / (N * n);

                    if (cellWidth < cellHeight) {
                        cellHeight = cellWidth;
                    } else {
                        cellWidth = cellHeight;
                    }

                    int dx = (width - cellWidth * N * n) / 2;
                    int dy = (height - cellHeight * N * n) / 2;

                    int row = (e.getPoint().x - dx) / cellWidth;
                    int col = (e.getPoint().y - dy) / cellHeight;

                    if (row < 0 || col < 0 || row >= N * n || col >= N * n) {
                        return;
                    }

                    int id = row / N * n + col / N;
                    int x = row % N + 1;
                    int y = col % N + 1;

                    guiBoard[id][x][y] ^= true;
                    PCJ.put(guiBoard[id][x][y], id, Shared.boards, (step) % 2, x, y);
                    PCJ.put(guiBoard[id][x][y], id, Shared.boards, (step + 1) % 2, x, y);
                    panel.repaint();
                }

            });

            frame.setContentPane(panel);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.setSize(600, 600);
            frame.setVisible(true);
        }

        init();
        paintBoards();

        PCJ.barrier();

        while (!control.equals(ControlEnum.STOP)) {
            PCJ.waitFor(Shared.control);
            if (control.equals(ControlEnum.PLAY)) {
                calculate();

                paintBoards();

                exchange();

                PCJ.barrier();

                ++step;
            }
        }
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
                PCJ.asyncPut(board[1][col], PCJ.myId() - threadsPerRow, Shared.boards, nextStep, N + 1, col);
            }
        }
        if (!isLastRow) {
            for (int col = 1; col <= N; ++col) {
                PCJ.asyncPut(board[N][col], PCJ.myId() + threadsPerRow, Shared.boards, nextStep, 0, col);
            }
        }

        if (!isFirstColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][1], PCJ.myId() - threadsPerRow - 1, Shared.boards, nextStep, N + 1, N + 1);
        }
        if (!isFirstColumn && !isLastRow) {
            PCJ.asyncPut(board[N][1], PCJ.myId() + threadsPerRow - 1, Shared.boards, nextStep, 0, N + 1);
        }
        if (!isLastColumn && !isFirstRow) {
            PCJ.asyncPut(board[1][N], PCJ.myId() - threadsPerRow + 1, Shared.boards, nextStep, N + 1, 0);
        }
        if (!isLastColumn && !isLastRow) {
            PCJ.asyncPut(board[N][N], PCJ.myId() + threadsPerRow + 1, Shared.boards, nextStep, 0, 0);
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

    private void calculate() {
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
        PCJ.deploy(GameOfLifeGui.class,
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
}
