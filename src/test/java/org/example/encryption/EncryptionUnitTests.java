package org.example.encryption;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EncryptionUnitTests {
    EncryptionUnit unit1 = new EncryptionUnit(1);
    EncryptionUnit unit2 = new EncryptionUnit(2);
    final String MSG = "おはよう";

    @Test
    @DisplayName("Testing RSA encryption")
    public void testEncryption() {
        byte[] cipherText;
        String plainText;

        cipherText = unit1.encrypt(MSG.getBytes(), 2);
        plainText = new String(unit2.decrypt(cipherText));

        assertEquals(MSG, plainText, "Decrypted message should match original");
    }

    @Test
    @DisplayName("Testing RSA signing")
    public void testSigning() {
        byte[] signature;
        final String MSG2 = "おやすみ";

        signature = unit1.sign(MSG.getBytes());

        assertTrue(unit2.verifySignature(MSG.getBytes(), signature, 1));
        assertFalse(unit2.verifySignature(MSG2.getBytes(), signature, 1));
    }
}
