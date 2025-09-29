package com.advisor.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageResponse {
    private Long id;
    private String content;
    private String role;
    private LocalDateTime timestamp;
}
