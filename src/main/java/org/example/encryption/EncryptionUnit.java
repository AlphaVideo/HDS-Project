package org.example.encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class EncryptionUnit {
    private final Map<Integer, PublicKey> _publicKeys;
    private final PrivateKey _privateKey;
    private final int KEY_COUNT = 13;
    private static final int MAX_SERVERS = 10;
    private final int _own_pid;

    public EncryptionUnit(int processId) {
        _own_pid = processId;
        try {
            _publicKeys = loadPublicKeys(KEY_COUNT);
            _privateKey = loadPrivateKey(processId);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PublicKey getOwnPublicKey() {
        return _publicKeys.get(_own_pid);
    }

    public static boolean is_client(int pid) {
        return pid > MAX_SERVERS;
    }

    public byte[] encrypt(byte[] payload, int destId) {
        PublicKey destPubKey = _publicKeys.get(destId);
        Cipher encryptCipherSpec;
        byte[] cipherText;

        try {
            encryptCipherSpec = Cipher.getInstance("RSA");
            encryptCipherSpec.init(Cipher.ENCRYPT_MODE, destPubKey);
            cipherText = encryptCipherSpec.doFinal(payload);
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | BadPaddingException
                | IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        }
        return cipherText;
    }

    public byte[] decrypt(byte[] cipherText) {
        Cipher decriptCipher;
        byte[] plainText;

        try {
            decriptCipher = Cipher.getInstance("RSA");
            decriptCipher.init(Cipher.DECRYPT_MODE, _privateKey);
            plainText = decriptCipher.doFinal(cipherText);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException
                | BadPaddingException e) {
            throw new RuntimeException(e);
        }
        return plainText;
    }

    public byte[] sign(byte[] payload) {
        Signature signatureSpec;
        byte[] signature;
        try {
            signatureSpec = Signature.getInstance("SHA256withRSA");
            signatureSpec.initSign(_privateKey);
            signatureSpec.update(payload);
            signature = signatureSpec.sign();
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return signature;
    }

    public boolean verifySignature(byte[] payload, byte[] signature, int signerId) {
        return verifySignature(payload, signature, _publicKeys.get(signerId));
    }

    public boolean verifySignature(byte[] payload, byte[] signature, PublicKey signerPubKey) {
        Signature publicSignatureSpec;
        boolean isCorrect;

        try {
            publicSignatureSpec = Signature.getInstance("SHA256withRSA");
            publicSignatureSpec.initVerify(signerPubKey);
            publicSignatureSpec.update(payload);
            isCorrect = publicSignatureSpec.verify(signature);
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return isCorrect;
    }

    private Map<Integer, PublicKey> loadPublicKeys(int keysCount)
            throws InvalidKeySpecException, NoSuchAlgorithmException, IOException {
        Map<Integer, PublicKey> publicKeys = new HashMap<>();

        byte[] publicKeyBytes;
        KeyFactory publicKeyFactory;
        EncodedKeySpec publicKeySpec;

        for (int i = 1; i <= keysCount; i++) {
            publicKeyBytes = getClass().getResourceAsStream("/public/key_" + i + ".pub").readAllBytes();

            publicKeyFactory = KeyFactory.getInstance("RSA");
            publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            publicKeys.put(i, publicKeyFactory.generatePublic(publicKeySpec));
        }
        return publicKeys;
    }

    public PublicKey getPublicKey(int pid) {
        return _publicKeys.getOrDefault(pid, null);
    }

    private PrivateKey loadPrivateKey(int processId)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privateKeyBytes = getClass().getResourceAsStream("/private/key_" + processId + ".key").readAllBytes();

        KeyFactory privateKeyFactory = KeyFactory.getInstance("RSA");
        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);

        return privateKeyFactory.generatePrivate(privateKeySpec);
    }

    public static String public_key_to_base64(PublicKey pk) {
        return Base64.getEncoder().encodeToString(pk.getEncoded());
    }

    public static PublicKey base64_to_public_key(String b64) {
        try {
            KeyFactory publicKeyFactory = KeyFactory.getInstance("RSA");
            return publicKeyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public int getPID(PublicKey pk) {
        for (int pid : _publicKeys.keySet()) {
            if (_publicKeys.get(pid).equals(pk)) {
                return pid;
            }
        }

        throw new RuntimeException("404 public key not found");
    }
}
