package org.pcj.examples;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage(PcjTeraSort.Vars.class)
public class PcjTeraSort implements StartPoint {

    @Storage(PcjTeraSort.class)
    enum Vars {
        sequencer, pivots, buckets
    }

    private boolean sequencer;
    private List<Element> pivots = new ArrayList<>();
    private Element[][][] buckets;

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Parameters: <input-file> <output-file> <pivots-by-thread> <nodes-file>");
            return;
        }
        PCJ.executionBuilder(PcjTeraSort.class)
                .addProperty("inputFile", args[0])
                .addProperty("outputFile", args[1])
                .addProperty("pivotsByThread", args[2])
                .addNodes(new File(args[3]))
                .deploy();
    }

    @Override
    public void main() throws Throwable {
        if (PCJ.myId() == 0) {
            PCJ.put(true, 0, Vars.sequencer);
        }

        String inputFile = PCJ.getProperty("inputFile");
        String outputFile = PCJ.getProperty("outputFile");
        int numberOfPivotsByThread = Integer.parseInt(PCJ.getProperty("pivotsByThread"));

        new File(outputFile).delete();

        long startTime = System.nanoTime();

        try (TeraFileInput input = new TeraFileInput(inputFile)) {
            long totalElements = input.length();

            long localElementsCount = totalElements / PCJ.threadCount();
            long reminderElements = totalElements - localElementsCount * PCJ.threadCount();
            if (PCJ.myId() < reminderElements) {
                ++localElementsCount;
            }

            if (PCJ.myId() == 0) {
                System.out.printf("Total elements to sort: %d%n", totalElements);
                System.out.printf("Each thread reads about: %d%n", localElementsCount);
            }

            // every thread read own portion of input file
            long startElement = PCJ.myId() * (totalElements / PCJ.threadCount()) + Math.min(PCJ.myId(), reminderElements);
            long endElement = startElement + localElementsCount;

            // generate pivots (a unique set of keys at random positions: k0<k1<k2<...<k(n-1))
            for (int i = 0; i < numberOfPivotsByThread; ++i) {
                input.seek(startElement + i * (localElementsCount / numberOfPivotsByThread));
                Element pivot = input.readElement();
                pivots.add(pivot);
            }
            PCJ.barrier();
            if (PCJ.myId() == 0) {
                pivots = PCJ.reduce((left, right) -> {
                    left.addAll(right);
                    return left;
                }, Vars.pivots);

                pivots = pivots.stream().distinct().sorted().collect(Collectors.toList()); // unique, sort

                PCJ.broadcast(pivots, Vars.pivots);
            }

            PCJ.waitFor(Vars.pivots);

            int pivotsCount = ((pivots.size() + 1) + PCJ.threadCount() - (PCJ.myId() + 1)) / PCJ.threadCount();
            buckets = new Element[pivotsCount][PCJ.threadCount()][];
            PCJ.barrier();

            @SuppressWarnings("unchecked")
            List<Element>[] localBuckets = (List<Element>[]) new List[pivots.size() + 1];
            for (int i = 0; i < localBuckets.length; ++i) {
                localBuckets[i] = new LinkedList<>();
            }

            // for each element in own data: put element in proper bucket
            input.seek(startElement);
            for (long i = startElement; i < endElement; ++i) {
                Element e = input.readElement();
                int bucketNo = Collections.binarySearch(pivots, e);
                if (bucketNo < 0) {
                    bucketNo = -(bucketNo + 1);
                }
                localBuckets[bucketNo].add(e);
            }

            // exchange buckets
            int smallPackSize = localBuckets.length / PCJ.threadCount();
            int bigPacks = (localBuckets.length % PCJ.threadCount());
            int bigPackSize = (localBuckets.length + PCJ.threadCount() - 1) / PCJ.threadCount();
            int bigPackLimit = bigPackSize * bigPacks;

            for (int i = 0; i < localBuckets.length; i++) {
                int threadId;
                int packNo;
                if (i < bigPackLimit) {
                    threadId = i / bigPackSize;
                    packNo = i % bigPackSize;
                } else {
                    threadId = bigPacks + (i - bigPackLimit) / smallPackSize;
                    packNo = (i - bigPackLimit) % smallPackSize;
                }

                Element[] bucket = localBuckets[i].toArray(new Element[0]);
                if (threadId != PCJ.myId()) {
                    PCJ.asyncPut(bucket, threadId, Vars.buckets, packNo, PCJ.myId()).get();
                } else {
                    PCJ.putLocal(bucket, Vars.buckets, packNo, PCJ.myId());
                }
            }
        }

        // sort buckets
        PCJ.waitFor(Vars.buckets, PCJ.threadCount() * buckets.length);
        Element[][] sortedBuckets = new Element[buckets.length][];
        for (int i = 0; i < buckets.length; i++) {
            sortedBuckets[i] = Arrays.stream(buckets[i]).flatMap(Arrays::stream).toArray(Element[]::new);
            Arrays.sort(sortedBuckets[i]);
        }
        System.out.printf("Thread %d sorted %d elements%n", PCJ.myId(), Arrays.stream(sortedBuckets).mapToInt(a -> a.length).sum());

        // save into file
        PCJ.waitFor(Vars.sequencer);
        try (TeraFileOutput output = new TeraFileOutput(outputFile)) {
            Arrays.stream(sortedBuckets).flatMap(Arrays::stream).forEach(output::writeElement);
        }
        PCJ.put(true, (PCJ.myId() + 1) % PCJ.threadCount(), Vars.sequencer);

        // display execution time
        if (PCJ.myId() == 0) {
            PCJ.waitFor(Vars.sequencer);
            long stopTime = System.nanoTime();
            System.out.printf(Locale.ENGLISH, "Total execution time: %.7f%n", (stopTime - startTime) / 1e9);
        }
    }

    public static class TeraFileOutput implements AutoCloseable {
        private final BufferedOutputStream output;

        public TeraFileOutput(String outputFile) throws FileNotFoundException {
            output = new BufferedOutputStream(new FileOutputStream(outputFile, true));
        }

        @Override
        public void close() throws Exception {
            output.close();
        }

        public void writeElement(Element element) throws UncheckedIOException {
            try {
                output.write(element.getKey().value);
                output.write(element.getValue().value);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public static class TeraFileInput implements AutoCloseable {
        private static final int recordLength = 100;
        private static final int keyLength = 10;
        private static final int valueLength = recordLength - keyLength;
        private final RandomAccessFile input;
        private final byte[] tempKeyBytes;
        private final byte[] tempValueBytes;

        public TeraFileInput(String inputFile) throws FileNotFoundException {
            input = new RandomAccessFile(inputFile, "r");
            tempKeyBytes = new byte[keyLength];
            tempValueBytes = new byte[valueLength];
        }

        @Override
        public void close() throws Exception {
            input.close();
        }

        public long length() throws IOException {
            return input.length() / recordLength;
        }

        public void seek(long pos) throws IOException {
            input.seek(pos * recordLength);
        }

        public Element readElement() throws IOException {
            input.readFully(tempKeyBytes);
            input.readFully(tempValueBytes);

            return new Element(new Text(tempKeyBytes), new Text(tempValueBytes));
        }
    }

    public static class Text implements Comparable<Text>, Serializable {
        private byte[] value;

        public Text(byte[] value) {
            this.value = value.clone();
        }

        @Override
        public int compareTo(Text other) {
            byte[] buffer1 = this.value;
            byte[] buffer2 = other.value;
            if (buffer1 == buffer2) {
                return 0;
            }

            for (int i = 0, j = 0; i < buffer1.length && j < buffer2.length; ++i, ++j) {
                int a = (buffer1[i] & 0xFF);
                int b = (buffer2[i] & 0xFF);
                if (a != b) {
                    return a - b;
                }
            }
            return buffer1.length - buffer2.length;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Text)) {
                return false;
            }
            Text that = (Text) obj;
            return this.compareTo(that) == 0;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Text{");
            for (byte b : value) {
                if ((b & 0xF0) == 0) sb.append('0');
                sb.append(Integer.toHexString(b & 0xFF));
            }
            sb.append('}');
            return sb.toString();
        }
    }

    public static class Element implements Comparable<Element>, Serializable {
        private Text key;
        private Text value;

        public Element(Text key, Text value) {
            this.key = key;
            this.value = value;
        }

        public Text getKey() {
            return key;
        }

        public Text getValue() {
            return value;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Element)) {
                return false;
            }
            Element element = (Element) obj;
            return key.equals(element.key) &&
                           value.equals(element.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        @Override
        public int compareTo(Element other) {
            int r = key.compareTo(other.key);
            if (r != 0) {
                return r;
            }
            return value.compareTo(other.value);
        }

        @Override
        public String toString() {
            return "Element{" +
                           "key=" + key +
                           ", value=" + value +
                           '}';
        }
    }
}
