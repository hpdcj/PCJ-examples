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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
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

    private int panelSize = 600;
    private int sleepTime = 100;
    private ControlEnum control = ControlEnum.PAUSE;

    private final int N = 64;

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
        STOP,
        EXCHANGE
    }
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
                public Dimension getPreferredSize() {
                    return new Dimension(panelSize, panelSize);
                }

                @Override
                public void paintComponent(Graphics g) {
                    super.paintComponent(g);

                    Graphics2D g2 = (Graphics2D) g.create();

                    int width = this.getWidth();
                    int height = this.getHeight();

                    g2.setColor(Color.lightGray);
                    g2.fillRect(0, 0, width, height);

                    int cellWidth = width / (N * threadsPerRow);
                    int cellHeight = height / (N * threadsPerRow);

                    if (cellWidth < cellHeight) {
                        cellHeight = cellWidth;
                    } else {
                        cellWidth = cellHeight;
                    }

                    int dx = (width - cellWidth * N * threadsPerRow) / 2;
                    int dy = (height - cellHeight * N * threadsPerRow) / 2;

                    g2.setColor(Color.white);
                    g2.fillRect(dx, dy, width - dx * 2, height - dy * 2);

                    if (control == ControlEnum.PAUSE) {
                        g2.setColor(Color.darkGray);
                    } else {
                        g2.setColor(Color.black);
                    }
                    for (int row = 0; row < N * threadsPerRow; row++) {
                        for (int col = 0; col < N * threadsPerRow; col++) {
                            int id = row / N * threadsPerRow + col / N;
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
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_ESCAPE:
                            frame.dispose();
                            changeControl(ControlEnum.STOP);
                            break;
                        case KeyEvent.VK_PAGE_UP:
                            panelSize += 100;
                            panel.revalidate();
                            break;
                        case KeyEvent.VK_PAGE_DOWN:
                            if (panelSize > 200) {
                                panelSize -= 100;
                            }
                            panel.revalidate();
                            break;
                        case KeyEvent.VK_MINUS:
                            sleepTime += 100;
                            break;
                        case KeyEvent.VK_EQUALS:
                            if (sleepTime > 0) {
                                sleepTime -= 50;
                            }
                            break;
                        default:
                            break;
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
                        changeControl(ControlEnum.EXCHANGE);
                        return;
                    }

                    JPanel panel = (JPanel) e.getSource();

                    int width = panel.getWidth();
                    int height = panel.getHeight();

                    int cellWidth = width / (N * threadsPerRow);
                    int cellHeight = height / (N * threadsPerRow);

                    if (cellWidth < cellHeight) {
                        cellHeight = cellWidth;
                    } else {
                        cellWidth = cellHeight;
                    }

                    int dx = (width - cellWidth * N * threadsPerRow) / 2;
                    int dy = (height - cellHeight * N * threadsPerRow) / 2;

                    int row = (e.getPoint().x - dx) / cellWidth;
                    int col = (e.getPoint().y - dy) / cellHeight;

                    if (row < 0 || col < 0 || row >= N * threadsPerRow || col >= N * threadsPerRow) {
                        return;
                    }

                    int id = row / N * threadsPerRow + col / N;
                    int x = row % N + 1;
                    int y = col % N + 1;

                    guiBoard[id][x][y] ^= true;
                    PCJ.put(guiBoard[id][x][y], id, Shared.boards, (step + 1) % 2, x, y);
                    panel.repaint();
                }

            });

            JScrollPane scrollPane = new JScrollPane(panel,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            frame.setContentPane(scrollPane);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            frame.setSize(650, 650);
            frame.setVisible(true);
        }

        init();
        paintBoards();

        PCJ.barrier();

        while (!control.equals(ControlEnum.STOP)) {
            PCJ.waitFor(Shared.control);
            if (control.equals(ControlEnum.PLAY)) {
                ++step;

                calculate();

                paintBoards();

                exchange();

                PCJ.barrier();
            } else if (control.equals(ControlEnum.EXCHANGE)) {
                exchange();
                changeControl(ControlEnum.PLAY);
            }
        }
    }

    private void changeControl(ControlEnum control) {
        if (PCJ.myId() == 0) {
            PCJ.broadcast(control, Shared.control);
        }
    }

    private void init() {
        /*
         * {
         * ".....",
         * "..X..",
         * "...X.",
         * ".XXX.",
         * "....."}
        
         * {
         *        "......X.",
         *        "XX......",
         *        ".X...XXX",
         */
        step = -1;
        boolean[][] board = boards[0];
        if (PCJ.myId() == 0) {

            String[] plansza = {
                "......X.",
                "....X.XX",
                "....X.X.",
                "....X...",
                "..X.....",
                "X.X.....",};
//            String[] plansza = {
//                "XXX..X",
//                "X.....",
//                "....XX",
//                ".XX..X",
//                "X.X..X"
//            };
//            String[] plansza = {
//                ".X.....",
//                "...X...",
//                "XX..XXX"
//            };

            for (int y = 0; y < plansza.length; y++) {
                for (int x = 0; x < plansza[y].length(); x++) {
                    if (plansza[y].charAt(x) != '.') {
                        board[N - plansza[y].length() + x][N - plansza.length + y] = true;
                    }
                }

            }
//            board[2][3] = true;
//            board[3][4] = true;
//            board[4][2] = true;
//            board[4][3] = true;
//            board[4][4] = true;
        }
        exchange();
    }

    private void exchange() {
        int nextStep = (step + 1) % 2;
        boolean[][] board = boards[nextStep];

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

    private void paintBoards() {
        boolean[][] board = boards[(step + 1) % 2];

        PCJ.asyncPut(board, 0, GuiBoard.guiBoard, PCJ.myId());
        if (PCJ.myId() == 0) {
            PCJ.waitFor(GuiBoard.guiBoard, PCJ.threadCount());
            panel.repaint();
            try {
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException ex) {
            }
            PCJ.broadcast(control, Shared.control);
        }
    }

    public static void main(String[] args) {
        PCJ.deploy(GameOfLifeGui.class,
                new NodesDescription(new String[]{
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost",
            "localhost"
        }));
    }
}
