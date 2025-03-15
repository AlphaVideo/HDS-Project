package org.example.consensus.exceptions;

public class InvalidFeeException extends Exception {
    public InvalidFeeException() {
    }

    public InvalidFeeException(String message) {
        super(message);
    }

    public InvalidFeeException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidFeeException(Throwable cause) {
        super(cause);
    }
}