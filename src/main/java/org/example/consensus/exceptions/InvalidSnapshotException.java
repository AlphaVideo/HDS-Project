package org.example.consensus.exceptions;

public class InvalidSnapshotException extends Exception {
    public InvalidSnapshotException() {
    }

    public InvalidSnapshotException(String message) {
        super(message);
    }

    public InvalidSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidSnapshotException(Throwable cause) {
        super(cause);
    }
}