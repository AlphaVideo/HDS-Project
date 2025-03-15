package org.example.consensus.exceptions;

public class InvalidBlockException extends Exception {
    public InvalidBlockException() {
    }

    public InvalidBlockException(String message) {
        super(message);
    }

    public InvalidBlockException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidBlockException(Throwable cause) {
        super(cause);
    }
}