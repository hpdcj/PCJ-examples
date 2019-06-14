package org.pcj.examples;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

@RegisterStorage(PcjDesDecryption.Vars.class)
public class PcjDesDecryption implements StartPoint {

    private byte[] secret;
    private byte[] textEncrypted;
    private boolean found;
    private long maxKey;

    @Storage(PcjDesDecryption.class)
    enum Vars {
        secret,
        textEncrypted,
        found,
        maxKey
    }

    public static void main(String[] args) throws IOException {
        String[] nodes = IntStream.range(0, 4).mapToObj(i -> "localhost").toArray(String[]::new);

        PCJ.executionBuilder(PcjDesDecryption.class)
                .addNodes(nodes)
                .start();
    }

    public void main() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher desCipher = Cipher.getInstance("DES");

        if (PCJ.myId() == 0) {
            String secretText = "1234567890";
            int keyBits = 26;

            PCJ.asyncBroadcast(false, Vars.found);
            PCJ.asyncBroadcast((1L << keyBits) - 1, Vars.maxKey);

            secret = secretText.getBytes();
            PCJ.asyncBroadcast(secret, Vars.secret);

            MyDesKey encryptionKey = new MyDesKey(ThreadLocalRandom.current().nextLong(1L << keyBits));
            desCipher.init(Cipher.ENCRYPT_MODE, encryptionKey);

            PCJ.asyncBroadcast(desCipher.doFinal(secret), Vars.textEncrypted);
        }

        PCJ.waitFor(Vars.found);
        PCJ.waitFor(Vars.secret);
        PCJ.waitFor(Vars.maxKey);
        PCJ.waitFor(Vars.textEncrypted);

        long start = System.nanoTime();

        MyDesKey decryptionKey = new MyDesKey(PCJ.myId());
        for (long value = PCJ.myId(); value < maxKey; value += PCJ.threadCount()) {
            try {
                desCipher.init(Cipher.DECRYPT_MODE, decryptionKey);

                byte[] textDecrypted = desCipher.doFinal(textEncrypted);
                if (Arrays.equals(secret, textDecrypted)) {
                    System.out.println("[" + PCJ.myId() + "] Found key: '" + value + "' -> " + new String(textDecrypted));
                    found = true;
                    PCJ.asyncBroadcast(found, Vars.found);
                }
//                if (found) break;
            } catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException ignored) {
            } finally {
                decryptionKey.addToKey(PCJ.threadCount());
            }
        }

        long stop = System.nanoTime();
        System.out.println("[" + PCJ.myId() + "] Total time = " + (stop - start) / 1e9);
    }

    public static class MyDesKey implements Key {

        private long keyValue;
        private final byte[] key;

        public MyDesKey(long keyValue) {
            this.keyValue = keyValue;
            this.key = new byte[8];

            updateKey();
        }

        private void updateKey() {
            key[0] = (byte) ((keyValue & 0b01111111) << 1);
            key[1] = (byte) (((keyValue >> 7) & 0b01111111) << 1);
            key[2] = (byte) (((keyValue >> 14) & 0b01111111) << 1);
            key[3] = (byte) (((keyValue >> 21) & 0b01111111) << 1);
            key[4] = (byte) (((keyValue >> 28) & 0b01111111) << 1);
            key[5] = (byte) (((keyValue >> 35) & 0b01111111) << 1);
            key[6] = (byte) (((keyValue >> 42) & 0b01111111) << 1);
            key[7] = (byte) (((keyValue >> 49) & 0b01111111) << 1);
        }

        @Override
        public String getAlgorithm() {
            return "DES";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return key.clone();
        }

        public void addToKey(long deltaValue) {
            keyValue += deltaValue;
            updateKey();
        }
    }
}

