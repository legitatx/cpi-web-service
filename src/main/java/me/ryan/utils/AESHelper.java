package me.legit.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class AESHelper {

    public static void encrypt(String text, String key) throws Exception {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        KeySpec spec = new PBEKeySpec(key.toCharArray(), generateRandomBytes(32), 1000, 128);
        SecretKey tmp = factory.generateSecret(spec);
        SecretKeySpec secret = new SecretKeySpec(tmp.getEncoded(), "AES");
        System.out.println("Key:" + Base64.getEncoder().encodeToString(secret.getEncoded()));

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secret);
        AlgorithmParameters params = cipher.getParameters();
        byte[] iv = params.getParameterSpec(IvParameterSpec.class).getIV();
        byte[] result = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        System.out.println("IV:" + Base64.getEncoder().encodeToString(iv));
        System.out.println("Cipher text:" + Base64.getEncoder().encodeToString(result));
    }

    private static byte[] generateRandomBytes(int size) throws NoSuchAlgorithmException {
        byte[] salt = new byte[size];
        SecureRandom.getInstanceStrong().nextBytes(salt);
        return salt;
    }
}
