package org.example.consensus.exceptions;

public class BrokenBlockchainException extends Exception {
    private byte[] _missingHash;

    public BrokenBlockchainException() {
    }

    public BrokenBlockchainException(byte[] missing) {
        _missingHash = missing;
    }

    public BrokenBlockchainException(String message) {
        super(message);
    }

    public BrokenBlockchainException(String message, Throwable cause) {
        super(message, cause);
    }

    public BrokenBlockchainException(Throwable cause) {
        super(cause);
    }

    public byte[] getMissingHash() {
        return _missingHash;
    }
}