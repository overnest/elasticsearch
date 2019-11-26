package org.elasticsearch.index.translog;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CtrCipher {
    public static String AES_ALGORITHM = "AES";
    public static String CTR_TRANSFORM = "AES/CTR/NoPadding";

    public static SecretKey AES_KEY = new SecretKeySpec(
        Base64.getDecoder().decode("4tZ9S+gRYX2F3fm+BIWDDvkcXbkKYXBmB27hixPvSjU="), AES_ALGORITHM);
    public static IvParameterSpec AES_IV = new IvParameterSpec(
        Base64.getDecoder().decode("fTJyaJjBv7cXL/oxVcLFBQ=="));

    private byte[] encryptOrDecrypt(int operationMode, byte[] bytes, long offset) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance(CTR_TRANSFORM);

            // org.apache.commons.crypto.stream.CtrCryptoInputStream.java#getCounter
            long counter = offset / cipher.getBlockSize();
            byte[] iv = AES_IV.getIV().clone();
            calculateIV(AES_IV.getIV(), counter, iv);

            cipher.init(operationMode, AES_KEY, new IvParameterSpec(iv));

            // org.apache.commons.crypto.stream.CtrCryptoInputStream.java#getPadding
            byte padding = (byte) (offset % cipher.getBlockSize());
            byte[] paddedBytes = padBytes(bytes, padding);

            byte[] result = new byte[paddedBytes.length];
            int n = cipher.update(paddedBytes, 0, paddedBytes.length, result, 0);

            return Arrays.copyOfRange(result, padding, n);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | ShortBufferException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public byte[] decrypt(byte[] bytes) throws IOException {
        return decrypt(bytes, 0);
    }

    public byte[] decrypt(ByteBuffer bb) throws IOException {
        return decrypt(readBytes(bb), 0);
    }

    public byte[] decrypt(ByteBuffer bb, long offset) throws IOException {
        return decrypt(readBytes(bb), offset);
    }

    public byte[] decrypt(byte[] bytes, long offset) throws IOException {
        return encryptOrDecrypt(Cipher.DECRYPT_MODE, bytes, offset);
    }

    public byte[] encrypt(byte[] bytes) throws IOException {
        return encrypt(bytes, 0);
    }

    public byte[] encrypt(ByteBuffer bb) throws IOException {
        return encrypt(readBytes(bb), 0);
    }

    public byte[] encrypt(ByteBuffer bb, long offset) throws IOException {
        return encrypt(readBytes(bb), offset);
    }

    public byte[] encrypt(byte[] bytes, long offset) throws IOException {
        return encryptOrDecrypt(Cipher.ENCRYPT_MODE, bytes, offset);
    }

    private byte[] padBytes(byte[] arr, byte padding) {
        // pads from start
        if (padding == 0) return arr;
        byte[] padded = new byte[padding + arr.length];
        System.arraycopy(arr, 0, padded, padding, arr.length);
        return padded;
    }

    private byte[] readBytes(ByteBuffer bb) {
        byte[] bytes = new byte[bb.limit() - bb.position()];
        bb.get(bytes);
        return bytes;
    }

    private void calculateIV(byte[] initIV, long counter, byte[] IV) {
        int i = IV.length; // IV length
        int j = 0; // counter bytes index
        int sum = 0;
        while (i-- > 0) {
            // (sum >>> Byte.SIZE) is the carry for addition
            sum = (initIV[i] & 0xff) + (sum >>> Byte.SIZE); // NOPMD
            if (j++ < 8) { // Big-endian, and long is 8 bytes length
                sum += (byte) counter & 0xff;
                counter >>>= 8;
            }
            IV[i] = (byte) sum;
        }
    }

}
