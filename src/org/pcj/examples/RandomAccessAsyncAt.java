package org.pcj.examples;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.pcj.AsyncTask;
import org.pcj.PCJ;
import org.pcj.PcjFuture;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage(RandomAccessAsyncAt.Shared.class)
public class RandomAccessAsyncAt implements StartPoint {

    @Storage(RandomAccessAsyncAt.class)
    enum Shared {

        table, receivedUpdates, test, okCells, executed, updatesShared
    }

    //shared variables
    private long[] table;
    private ArrayList<Long>[][] receivedUpdates;
    Integer test;
    private int okCells;
    private int executed;
    List<Long> updatesShared[];

    public static final int BUFFERED_UPDATES = 1024;
    public static final long POISON_PILL = -1;

    public static void main(String[] args) throws IOException {
        String[] nodes = {
                "localhost",
                "localhost",
                "localhost",
                "localhost"
        };
        PCJ.executionBuilder(RandomAccessAsyncAt.class)
                .addNodes(nodes)
                .deploy();
    }

    int logN;
    int globalN;
    int logLocalN;
    int localN;
    int logNumProcs;
    long localUpdates;
    int threadCount;
    int myId;

    RandomForRA random;

    long timeBoundSeconds = Long.MAX_VALUE;
    boolean shutDown = false;
    int preparedLocally = 0;

    @Override
    public void main() throws Throwable {

        String[] rounds = {"Warmup", "After warmup"};
        for (String round : rounds) {

            if (PCJ.myId() == 0) {
                System.out.println(round + " round");
            }

            initializeData();
            PCJ.barrier();
            double start = System.currentTimeMillis();
            performRandomAccess();

            PCJ.barrier();
            double stop = System.currentTimeMillis();

            PCJ.barrier();
            if (PCJ.myId() == 0) {
                System.out.println("Thread 0 verifying");
            }
            verifyResultsLocally();
            thread0VerifyAll();
            if (myId == 0) {
                double seconds = (stop - start) * 1e-3;
                int updatesPerformedGlobally = 0;
                for (int PE = 0; PE < PCJ.threadCount(); PE++) {
                    updatesPerformedGlobally += (int) PCJ.get(PE, Shared.executed);
                }
                double gups = updatesPerformedGlobally * 1e-9 / seconds;
                System.out.println("Time: " + seconds + " s, global size = " + this.globalN + " updates = " + updatesPerformedGlobally + ", performance: " + gups);
            }
            PCJ.barrier();

        }
    }

    private void initializeData() throws FileNotFoundException {

        try (Scanner in = new Scanner(new File("ra.config"))) {
            logN = in.nextInt();
            if (in.hasNextLong()) {
                this.timeBoundSeconds = in.nextLong();
            }
        } catch (IOException ex) {
            logN = 20;
            this.timeBoundSeconds = 60;
        }
        threadCount = PCJ.threadCount();
        myId = PCJ.myId();
        logNumProcs = (int) (Math.log(threadCount) / Math.log(2));
        logLocalN = logN - logNumProcs;
        localN = 1 << logLocalN;
        globalN = localN * threadCount;
        localUpdates = 4 * localN;
        long[] table = new long[localN];
        for (int i = 0; i < localN; i++) {
            table[i] = i + myId * localN;
        }

        PCJ.putLocal(table, Shared.table);
        PCJ.putLocal(0, Shared.executed);
        PCJ.putLocal(0, Shared.okCells);
        PCJ.putLocal(new ArrayList<?>[(int) (localUpdates / BUFFERED_UPDATES)][logNumProcs + 1], Shared.receivedUpdates);

        PCJ.monitor(Shared.receivedUpdates);
        PCJ.monitor(Shared.executed);
        PCJ.monitor(Shared.okCells);
        PCJ.monitor(Shared.table);
        random = new RandomForRA(myId);
        this.preparedLocally = 0;
        this.shutDown = false;
    }

    private int randomNumberToLocalPosition(long rand) {
        return (int) (rand & (localN - 1));
    }

    private long generateRandomUpdate() {
        return random.nextLong();
    }

    boolean isTimeBound() {
        return timeBoundSeconds != Long.MAX_VALUE;
    }

    private void performRandomAccess() {
        long timeBoundStart = System.currentTimeMillis();
        int executedInThread = 0;

        int futuresIndex = 0;
        PcjFuture[] futures = new PcjFuture[400];

        for (int update = 0; update < localUpdates; update++) {
            final long randomLocation = generateRandomUpdate();

            if (isTimeBound()) {
                shutDown = System.currentTimeMillis() - timeBoundStart > timeBoundSeconds * 1e3;
            }

            final int finalLocalN = localN;

            PcjFuture<Void> future = PCJ.asyncAt(whichPE(randomLocation), new UpdateTask(randomLocation, finalLocalN));

            futures[futuresIndex++] = future;
            if (futuresIndex == futures.length) {
                for (PcjFuture f : futures) {
                    f.get();
                }
                futuresIndex = 0;
            }

            executedInThread++;

            if (isTimeBound()) {
                if (shutDown) {
                    break;
                }
            }
        }
        PCJ.putLocal(executedInThread, Shared.executed);
    }

    int whichPE(long pos) {
        return (int) (pos >> logLocalN) & (threadCount - 1);
    }

    private void updateSingleCell(long update) throws ClassCastException {
        int localOffset = randomNumberToLocalPosition(update);
        long val = PCJ.getLocal(Shared.table, localOffset);
        val ^= update;
        PCJ.putLocal(val, Shared.table, localOffset);
    }

    private void verifyResultsLocally() {
        PCJ.barrier();

        for (int PE = 0; PE < threadCount; PE++) {
            RandomForRA random = new RandomForRA(PE);

            int executedInThread = PCJ.get(PE, Shared.executed);
            for (int update = 0; update < executedInThread; update++) {
                long val = random.nextLong();
                if (whichPE(val) == myId) {

                    updateSingleCell(val);
                }
            }
        }

        long[] table = PCJ.getLocal(Shared.table);
        int ok = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] == i + myId * localN) {
                ok++;
            } else {
                //PCJ.log("Cell #" + i + " is " + table[i] + ", should be: " + (i + myId * localN));
            }
        }
        PCJ.putLocal(ok, Shared.okCells);
        PCJ.waitFor(Shared.okCells);
        PCJ.barrier();
    }

    private void thread0VerifyAll() {
        if (myId == 0) {
            for (int i = 0; i < threadCount; i++) {
                int remoteOk = PCJ.get(i, Shared.okCells);
                if (remoteOk != localN) {
                    System.out.println("Verification failed for thread #" + i + " notOKCells = " + remoteOk);
                }
            }
        }
    }

    private static class RandomForRA {

        public static final long POLY = 0x0000000000000007;
        public static final long PERIOD = 1317624576693539401L;
        private long rand;

        public RandomForRA(int initialVal) {
            long n = initialVal * 4;
            int i, j;
            /* unsigned */
            long[] m2 = new /* unsigned */ long[64];
            /* unsigned */
            long temp, ran;

            while (n < 0) {
                n += PERIOD;
            }
            while (n > PERIOD) {
                n -= PERIOD;
            }
            if (n == 0) {
                rand = 0x1;
            }

            temp = 0x1;
            for (i = 0; i < 64; i++) {
                m2[i] = temp;
                temp = (temp << 1) ^ ((long) temp < 0 ? POLY : 0);
                temp = (temp << 1) ^ ((long) temp < 0 ? POLY : 0);
            }

            for (i = 62; i >= 0; i--) {
                if (((n >> i) & 1) == 1) {
                    break;
                }
            }

            ran = 0x2;
            while (i > 0) {
                temp = 0;
                for (j = 0; j < 64; j++) {
                    if (((ran >> j) & 1) == 1) {
                        temp ^= m2[j];
                    }
                }
                ran = temp;
                i -= 1;
                if (((n >> i) & 1) == 1) {
                    ran = (ran << 1) ^ ((long) ran < 0 ? POLY : 0);
                }
            }

            rand = ran;
        }

        public long nextLong() {

            rand = (rand << 1) ^ ((long) rand < 0L ? POLY : 0L);
            // System.out.println(rand);
            return rand;
        }
    }

    private static class UpdateTask implements AsyncTask.VoidTask {

        private final long randomLocation;
        private final int finalLocalN;

        UpdateTask(long randomLocation, int finalLocalN) {
            this.randomLocation = randomLocation;
            this.finalLocalN = finalLocalN;
        }

        @Override
        public void run() {
            int localOffset = (int) (randomLocation & (finalLocalN - 1));
            long[] table = PCJ.getLocal(Shared.table);
            table[localOffset] ^= randomLocation;
        }
    }
}
