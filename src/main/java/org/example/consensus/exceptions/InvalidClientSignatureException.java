package org.example.consensus.exceptions;

public class InvalidClientSignatureException extends Exception {
    private byte[] _invalidSignature;

    public InvalidClientSignatureException(String message) {
        super(message);
    }

    public InvalidClientSignatureException(byte[] invalidSig) {
        _invalidSignature = invalidSig;
    }

    public InvalidClientSignatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidClientSignatureException(Throwable cause) {
        super(cause);
    }
}
