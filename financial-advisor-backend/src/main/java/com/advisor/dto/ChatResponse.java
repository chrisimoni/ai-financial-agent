package com.advisor.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatResponse {
    private String message;
    private String status;
    private Long timestamp;

    public ChatResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ChatResponse(String message, String status) {
        this.message = message;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
}
