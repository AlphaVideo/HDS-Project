package org.example.consensus.exceptions;

public class SelfTransferException extends Exception {
    public SelfTransferException() {
    }

    public SelfTransferException(String message) {
        super(message);
    }

    public SelfTransferException(String message, Throwable cause) {
        super(message, cause);
    }

    public SelfTransferException(Throwable cause) {
        super(cause);
    }
}