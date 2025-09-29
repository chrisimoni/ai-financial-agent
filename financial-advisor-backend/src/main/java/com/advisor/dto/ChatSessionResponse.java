package com.advisor.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionResponse {
    private String sessionId;
    private String preview;
    private LocalDateTime lastMessageAt;
    private int messageCount;
}
