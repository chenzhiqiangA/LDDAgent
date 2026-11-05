package com.ldd.agent.cursor;

public class CursorAgentException extends RuntimeException {

    private final int status;

    public CursorAgentException(String message, int status) {
        super(message);
        this.status = status;
    }

    public CursorAgentException(String message) {
        this(message, 502);
    }

    public int status() {
        return status;
    }
}
