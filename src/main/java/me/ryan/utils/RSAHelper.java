package me.legit.utils;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class RSAHelper {

    private Cipher cipher;

    public RSAHelper() throws NoSuchPaddingException, NoSuchAlgorithmException {
        this.cipher = Cipher.getInstance("RSA");
    }

    public String encryptText(String msg, PublicKey key) throws Exception {
        this.cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] msgAsBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedMsg = cipher.doFinal(msgAsBytes);
        return new String(encryptedMsg);
    }

    public byte[] encryptBytes(byte[] bytes, PublicKey key) throws Exception {
        this.cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(bytes);
    }

    public byte[] decryptBytes(byte[] msg, PrivateKey key) throws Exception {
        this.cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(msg);
    }

    public String decryptText(String msg, PrivateKey key) throws Exception {
        this.cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] msgAsBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] decryptedMsg = cipher.doFinal(msgAsBytes);
        return new String(decryptedMsg);
    }

    public static PrivateKey getPrivateKey(String file) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Path path = Paths.get(file);
        byte[] keyBytes = Files.readAllBytes(path);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(spec);
    }

    public static PublicKey getPublicKey(String file) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        Path path = Paths.get(file);
        byte[] keyBytes = Files.readAllBytes(path);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    public static PublicKey getPublicKey(byte[] modulusBytes, byte[] exponentBytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
        BigInteger modulus = new BigInteger(1, modulusBytes);
        BigInteger exponent = new BigInteger(1, exponentBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }
}
