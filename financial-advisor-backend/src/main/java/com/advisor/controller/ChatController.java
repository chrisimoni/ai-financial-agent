package com.advisor.controller;

import com.advisor.dto.ChatMessageResponse;
import com.advisor.dto.ChatRequest;
import com.advisor.dto.ChatResponse;
import com.advisor.dto.ChatSessionResponse;
import com.advisor.model.ChatMessage;
import com.advisor.model.User;
import com.advisor.service.ChatService;
import com.advisor.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    private final UserService userService;

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request, Authentication auth) {
        try {
            User user = userService.getCurrentUser(auth);
            String response = chatService.processMessage(user, request.getMessage(), request.getSessionId());

            return ResponseEntity.ok(new ChatResponse(response, "success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Error processing message", "error"));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<?> getUserChatSessions(Authentication authentication) {
        try {
            User user = userService.getCurrentUser(authentication);
            List<ChatSessionResponse> sessions = chatService.getUserChatSessions(user.getId());
            return ResponseEntity.ok(sessions);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to load chat history"));
        }
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessageResponse>> getChatHistory(@PathVariable String sessionId, Authentication auth) {
        try {
            User user = userService.getCurrentUser(auth);
            List<ChatMessage> chatHistory = chatService.getChatHistory(user, sessionId);
            List<ChatMessageResponse> dtoList = chatHistory.stream()
                    .map(msg -> ChatMessageResponse.builder()
                            .id(msg.getId())
                            .content(msg.getContent())
                            .role(msg.getRole() != null ? msg.getRole().toString() : "USER")
                            .timestamp(msg.getTimestamp())
                            .build())
                    .collect(Collectors.toList());
            return ResponseEntity.ok().body(dtoList);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/history/{sessionId}/clear")
    public ResponseEntity<Void> clearChatHistory(@PathVariable String sessionId, Authentication auth) {
        try {
            User user = userService.getCurrentUser(auth);
            chatService.clearHistory(user, sessionId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/instructions")
    public ResponseEntity<String> updateInstructions(@RequestBody Map<String, String> request, Authentication auth) {
        try {
            User user = userService.getCurrentUser(auth);
            user.setOngoingInstructions(request.get("instructions"));
            userService.saveUser(user);

            return ResponseEntity.ok("Instructions updated successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating instructions");
        }
    }
}
