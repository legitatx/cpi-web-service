package me.legit.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public class CPIEncryptor {

    private Cipher cipher;
    private SecretKey secretKey;
    private static byte[] tempBytes = new byte[16];

    public CPIEncryptor(byte[] key) throws Exception {
        if (key.length != 32) {
            throw new IllegalArgumentException("Invalid key: " + new String(key) + ". This must be 32 bytes long!");
        }
        this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        this.secretKey = new SecretKeySpec(key, "AES");
    }

    public byte[] encrypt(byte[] bytes) throws Exception {
        SecureRandom random = new SecureRandom();
        random.nextBytes(tempBytes);
        IvParameterSpec iv = new IvParameterSpec(tempBytes);
        this.cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
        byte[] encrypyedBytes = cipher.doFinal(bytes, 0, bytes.length);
        byte[] encryptedBytes2 = new byte[16 + encrypyedBytes.length];
        System.arraycopy(tempBytes, 0, encryptedBytes2, 0, 16);
        System.arraycopy(encrypyedBytes, 0, encryptedBytes2, 16, encrypyedBytes.length);
        return encryptedBytes2;
    }

    public byte[] decrypt(byte[] bytes) throws Exception {
        if (bytes.length <= 16) {
            throw new IllegalArgumentException("Invalid byte array: " + new String(bytes) + ". This must be over 16 bytes long!");
        }
        System.arraycopy(bytes, 0, tempBytes, 0, 16);
        IvParameterSpec iv = new IvParameterSpec(tempBytes);
        this.cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);
        return cipher.doFinal(bytes, 16, bytes.length - 16);
    }
}
