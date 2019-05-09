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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
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

    private final boolean disableGui = Boolean.parseBoolean(System.getProperty("gui.disabled", "false"));
    private ControlEnum control = disableGui ? ControlEnum.PLAY : ControlEnum.PAUSE;
    private final int threadsPerRow = (int) Math.sqrt(PCJ.threadCount());

    private long lastCells = 0;
    private long nowCells = 0;
    private long lastTime = 0;
    private final int sideSize = Integer.parseInt(System.getProperty("sideSize", "600"));
    private final int N = sideSize / threadsPerRow;

    private int panelSize = Math.max(500, sideSize);
    private int sleepTime = disableGui ? 0 : 100;

    @Storage(GameOfLifeGui.class)
    enum GuiBoard {
        guiBoard
    }
    private final boolean[][][] guiBoard = disableGui ? null : (PCJ.myId() == 0 ? new boolean[PCJ.threadCount()][N + 2][N + 2] : null);
    private JPanel panel;
    private JLabel performanceLabel;
    private JLabel panelSizeLabel;
    private JLabel sleepTimeLabel;

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
    private final boolean isFirstColumn = PCJ.myId() % threadsPerRow == 0;
    private final boolean isLastColumn = PCJ.myId() % threadsPerRow == threadsPerRow - 1;
    private final boolean isFirstRow = PCJ.myId() < threadsPerRow;
    private final boolean isLastRow = PCJ.myId() >= PCJ.threadCount() - threadsPerRow;
    private int step = 0;
    private final int maxSteps = Integer.parseInt(System.getProperty("maxSteps", "-1"));

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            System.out.printf("Size = %dx%d\n", sideSize, sideSize);
            System.out.printf("Threads = %d (%d nodes)\n", PCJ.threadCount(), PCJ.getNodeCount());
            System.out.printf("maxSteps = %s\n", maxSteps);
            System.out.printf("DisableGUI = %s\n", disableGui);

            if (!disableGui) {
                System.out.printf("SleepTime = %d\n", sleepTime);
                System.out.printf("PanelSize = %d\n", panelSize);

                PCJ.registerStorage(GuiBoard.class, this);
                JFrame frame = new JFrame();
                panel = new JPanel() {
                    private final Color[] colors;

                    {
                        List<Color> colorList = IntStream.range(0, PCJ.threadCount())
                                .mapToObj(id -> Color.getHSBColor((float) id / PCJ.threadCount(), 0.3f, 1.0f))
                                .collect(Collectors.toList());
                        Collections.shuffle(colorList);
                        colors = colorList.toArray(new Color[0]);
                    }

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

                        for (int row = 0; row < N * threadsPerRow; row++) {
                            for (int col = 0; col < N * threadsPerRow; col++) {
                                int id = row / N * threadsPerRow + col / N;
                                int x = row % N + 1;
                                int y = col % N + 1;
                                if (guiBoard[id][x][y]) {
                                    if (control == ControlEnum.PAUSE) {
                                        g2.setColor(Color.darkGray);
                                    } else {
                                        g2.setColor(Color.black);
                                    }
                                } else {
                                    g2.setColor(colors[id]);
                                }
                                g2.fillRect(dx + row * cellWidth, dy + col * cellHeight, cellWidth, cellHeight);
                            }
                        }
                        g2.dispose();
                    }
                };
                frame.addKeyListener(new KeyAdapter() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        System.out.println("KeyEvent: " + e.paramString());
                        switch (e.getKeyCode()) {
                            case KeyEvent.VK_SPACE:
                                if (!control.equals(ControlEnum.PAUSE)) {
                                    changeControl(ControlEnum.PAUSE);
                                    panel.repaint();
                                    break;
                                } else {
                                    changeControl(ControlEnum.EXCHANGE);
                                    break;
                                }
                            case KeyEvent.VK_ESCAPE:
                                frame.dispose();
                                changeControl(ControlEnum.STOP);
                                break;
                            case KeyEvent.VK_PAGE_UP:
                                panelSize += 100;
                                panel.revalidate();
                                panelSizeLabel.setText("PanelSize = " + panelSize);
                                break;
                            case KeyEvent.VK_PAGE_DOWN:
                                if (panelSize >= sideSize + 100) {
                                    panelSize -= 100;
                                }
                                panel.revalidate();
                                panelSizeLabel.setText("PanelSize = " + panelSize);
                                break;
                            case KeyEvent.VK_MINUS:
                                sleepTime += 100;
                                sleepTimeLabel.setText("SleepTime = " + sleepTime);
                                break;
                            case KeyEvent.VK_EQUALS:
                                if (sleepTime >= 50) {
                                    sleepTime -= 50;
                                }
                                sleepTimeLabel.setText("SleepTime = " + sleepTime);
                                break;
                            default:
                                break;
                        }
                    }
                });

                panel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        System.out.println("MouseEvent: " + e.paramString());
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            if (!control.equals(ControlEnum.PAUSE)) {
                                changeControl(ControlEnum.PAUSE);
                                panel.repaint();
                                return;
                            }
                        } else {
                            if (control.equals(ControlEnum.PAUSE)) {
                                changeControl(ControlEnum.EXCHANGE);
                            }
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

                frame.add(scrollPane, BorderLayout.CENTER);
                JPanel labelPanel = new JPanel();
                labelPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 1));
                labelPanel.add(new JLabel(String.format("Size = %dx%d", sideSize, sideSize)));
                labelPanel.add(new JLabel("|"));

                labelPanel.add(new JLabel(String.format("Threads = %d (%d nodes)", PCJ.threadCount(), PCJ.getNodeCount())));
                labelPanel.add(new JLabel("|"));

                sleepTimeLabel = new JLabel(String.format("SleepTime = %d", sleepTime));
                labelPanel.add(sleepTimeLabel);
                labelPanel.add(new JLabel("|"));

                panelSizeLabel = new JLabel(String.format("PanelSize = %d", panelSize));
                labelPanel.add(panelSizeLabel);
                labelPanel.add(new JLabel("|"));

                performanceLabel = new JLabel("Performance");
                labelPanel.add(performanceLabel);

                frame.add(labelPanel, BorderLayout.PAGE_START);

                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.pack();
                frame.setSize(650, 650);
                frame.setVisible(true);
            }
        }

        init();
        if (!disableGui) {
            paintBoards();
        }

        PCJ.barrier();

        long startTime = lastTime = System.nanoTime();
        while ((maxSteps == -1 || step < maxSteps) && !control.equals(ControlEnum.STOP)) {
            if (!disableGui) {
                PCJ.waitFor(Shared.control);
            }
            if (disableGui || control.equals(ControlEnum.PLAY)) {
                ++step;

                calculate();
                if (!disableGui) {
                    paintBoards();
                    changeControl(control);
                }

                exchange();

                if (PCJ.myId() == 0) {
                    nowCells += (long) N * N * PCJ.threadCount();

                    long nowTime = System.nanoTime();
                    long deltaTime = nowTime - lastTime;

                    if (deltaTime > 1_000_000_000) {
                        long deltaCells = nowCells - lastCells;
                        double rate = (double) deltaCells / (deltaTime / 1e9);
                        if (deltaCells > 0) {
                            lastCells = nowCells;
                            lastTime = nowTime;

                            System.out.printf("%.0f cells/s\n", rate);
                            if (!disableGui) {
                                SwingUtilities.invokeLater(() -> performanceLabel.setText(String.format("%,.0f cells/s", rate)));
                            }
                        }
                    }
                }

                PCJ.barrier();
            } else if (control.equals(ControlEnum.EXCHANGE)) {
                exchange();
                changeControl(ControlEnum.PLAY);
            }
        }

        if (PCJ.myId() == 0) {
            long nowTime = System.nanoTime();
            long deltaTime = nowTime - startTime;

            double rate = (double) nowCells / (deltaTime / 1e9);
            lastTime = nowTime;

            System.out.printf("%d threads on %d nodes processed %d cells in %.3f seconds (%.0f cells/s)\n", PCJ.threadCount(), PCJ.getNodeCount(), nowCells, deltaTime / 1e9, rate);
        }
    }

    private void changeControl(ControlEnum control) {
        if (PCJ.myId() == 0) {
            PCJ.broadcast(control, Shared.control);
        }
    }

    private void init() {
        step = -1;
        boolean[][] board = boards[0];

        Random rand = new Random();
        String seed = System.getProperty("seed");
        if (seed != null) {
            rand.setSeed(Long.parseLong(seed));
        }

        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                board[row][col] = (rand.nextDouble() <= 0.15);
            }
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
            panel.repaint();
            try {
                if (sleepTime > 0) {
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException ex) {
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        PCJ.start(GameOfLifeGui.class,
                //                new NodesDescription("nodes.txt"));
                new NodesDescription(
                        new String[]{
                            "localhost",
                            "localhost",
                            "localhost",
                            "localhost", //                            "localhost",
                        //                            "localhost",
                        //                            "localhost",
                        //                            "localhost",
                        //                            "localhost"
                        }
                ));
    }
}
