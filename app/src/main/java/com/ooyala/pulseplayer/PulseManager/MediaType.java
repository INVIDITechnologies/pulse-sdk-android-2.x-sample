package com.ooyala.pulseplayer.PulseManager;

public enum MediaType {
    AD("ad"),
    CONTENT("content");

    private final String message;

    MediaType(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
