package com.advisor.service;

import com.advisor.dto.ChatSessionResponse;
import com.advisor.model.ChatMessage;
import com.advisor.model.User;
import com.advisor.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final OpenAIChatService openAiChatService;
    private final ChatMessageRepository chatMessageRepository;
    private final RAGService ragService;

    private static final int MAX_HISTORY_MESSAGES = 5;
    private static final int MAX_RAG_CONTEXT_LENGTH = 3000; // characters (~750 tokens)
    private static final int MAX_SYSTEM_PROMPT_LENGTH = 2500; // characters (~625 tokens)

    public String processMessage(User user, String message, String sessionId) {
        try {
            ChatMessage userMessage = new ChatMessage(user, message, ChatMessage.MessageRole.USER, sessionId);
            chatMessageRepository.save(userMessage);

            List<ChatMessage> history = getConversationHistory(user, sessionId);

            String ragContext = ragService.retrieveRelevantContext(user, message);
            ragContext = truncateContext(ragContext, MAX_RAG_CONTEXT_LENGTH);

            String systemPrompt = buildSystemPrompt(user, ragContext);
            systemPrompt = truncateSystemPrompt(systemPrompt, MAX_SYSTEM_PROMPT_LENGTH);

            String response = openAiChatService.processMessage(systemPrompt, history, message, user);

            ChatMessage assistantMessage = new ChatMessage(user, response, ChatMessage.MessageRole.ASSISTANT, sessionId);
            chatMessageRepository.save(assistantMessage);

            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return "I apologize, but I encountered an error processing your request. Please try again.";
        }
    }

    /**
     * Truncate RAG context to prevent token overflow.
     */
    private String truncateContext(String context, int maxLength) {
        if (context == null || context.isEmpty()) {
            return "";
        }

        if (context.length() <= maxLength) {
            return context;
        }

        // Find a good breaking point (end of sentence or paragraph)
        String truncated = context.substring(0, maxLength);
        int lastPeriod = truncated.lastIndexOf('.');
        int lastNewline = truncated.lastIndexOf('\n');

        int breakPoint = Math.max(lastPeriod, lastNewline);
        if (breakPoint > maxLength * 0.8) { // If we can break at 80%+ of max length
            truncated = context.substring(0, breakPoint + 1);
        }

        return truncated + "\n\n[Additional context truncated to fit token limits]";
    }

    /**
     * Truncate system prompt intelligently, keeping the most important sections.
     */
    private String truncateSystemPrompt(String prompt, int maxLength) {
        if (prompt == null || prompt.isEmpty()) {
            return "";
        }

        if (prompt.length() <= maxLength) {
            return prompt;
        }

        // Parse prompt into sections
        StringBuilder truncated = new StringBuilder();
        String[] sections = prompt.split("\n\n");

        int currentLength = 0;
        boolean includedContext = false;

        for (String section : sections) {
            // Priority 1: Always include core sections
            if (section.startsWith("You are an AI assistant") ||
                    section.startsWith("USER INFORMATION:") ||
                    section.startsWith("AVAILABLE TOOLS:") ||
                    section.startsWith("GUIDELINES:")) {

                if (currentLength + section.length() + 2 <= maxLength) {
                    truncated.append(section).append("\n\n");
                    currentLength += section.length() + 2;
                } else {
                    // Even core sections need truncation if too large
                    if (section.startsWith("AVAILABLE TOOLS:")) {
                        // Truncate tools list to essential ones
                        truncated.append("AVAILABLE TOOLS:\n");
                        truncated.append("- sendEmail, scheduleAppointment, searchCalendar, ");
                        truncated.append("createContact, getUpcomingMeetings, searchContacts\n\n");
                        currentLength += 150;
                    } else {
                        int available = maxLength - currentLength - 100; // Reserve 100 for guidelines
                        if (available > 0) {
                            truncated.append(section, 0, Math.min(available, section.length()));
                            truncated.append("...\n\n");
                            currentLength += available;
                        }
                    }
                }
            }
            // Priority 2: Include context if space allows
            else if (section.startsWith("RELEVANT CONTEXT") && !includedContext) {
                int contextSpace = maxLength - currentLength - 500; // Reserve 500 for guidelines
                if (contextSpace > 200) { // Only include if we have reasonable space
                    String contextContent = section.substring(section.indexOf('\n') + 1);
                    if (contextContent.length() <= contextSpace) {
                        truncated.append(section).append("\n\n");
                        currentLength += section.length() + 2;
                    } else {
                        truncated.append("RELEVANT CONTEXT FROM YOUR DATA:\n");
                        truncated.append(contextContent, 0, Math.min(contextSpace, contextContent.length()));
                        truncated.append("\n[Context truncated]\n\n");
                        currentLength += contextSpace;
                    }
                    includedContext = true;
                }
            }
            // Priority 3: Include ongoing instructions if space allows
            else if (section.startsWith("ONGOING INSTRUCTIONS:")) {
                if (currentLength + section.length() + 2 <= maxLength) {
                    truncated.append(section).append("\n\n");
                    currentLength += section.length() + 2;
                }
            }
        }

        // Add truncation notice if we cut anything significant
        if (currentLength >= maxLength * 0.9) {
            truncated.append("[System prompt optimized for token limits]\n");
        }

        return truncated.toString().trim();
    }

    private String buildSystemPrompt(User user, String ragContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an AI assistant for financial advisors. You help manage client relationships, ");
        prompt.append("schedule appointments, and answer questions about clients based on email and CRM data.\n\n");

        prompt.append("USER INFORMATION:\n");
        prompt.append("- Name: ").append(user.getName()).append("\n");
        prompt.append("- Email: ").append(user.getEmail()).append("\n\n");

        prompt.append("AVAILABLE TOOLS:\n");
        prompt.append("- sendEmail(to, subject, body): Send emails to clients\n");
        prompt.append("- scheduleAppointment(contactName, proposedTimes): Schedule appointments with clients\n");
        prompt.append("- createContact(name, email, company, notes): Create new contacts in CRM\n");
        prompt.append("- searchCalendar(query, startDate, endDate): Search calendar events\n");
        prompt.append("- getAvailableSlots(date): Get available time slots for a date\n");
        prompt.append("- createCalendarEvent(title, startTime, endTime, attendees): Create calendar events\n");
        prompt.append("- checkCalendarConflicts(startTime, endTime): Check for scheduling conflicts\n");
        prompt.append("- getUpcomingMeetings(days): Get upcoming meetings\n");
        prompt.append("- searchContacts(query): Search for contacts in CRM\n\n");

        // Include RAG context if available
        if (ragContext != null && !ragContext.trim().isEmpty() && !ragContext.equals("No relevant context found.")) {
            prompt.append("RELEVANT CONTEXT FROM YOUR DATA:\n");
            prompt.append(ragContext);
            prompt.append("\n\n");
        }

        // Include ongoing instructions if set
        if (user.getOngoingInstructions() != null && !user.getOngoingInstructions().isEmpty()) {
            prompt.append("ONGOING INSTRUCTIONS:\n");
            prompt.append("Remember these ongoing instructions for all interactions:\n");
            prompt.append(user.getOngoingInstructions());
            prompt.append("\n\n");
        }

        prompt.append("GUIDELINES:\n");
        prompt.append("- Be helpful, professional, and proactive\n");
        prompt.append("- Use the relevant context to provide specific, personalized responses\n");
        prompt.append("- When scheduling appointments or managing contacts, use the available tools\n");
        prompt.append("- Always confirm actions taken with the user\n");
        prompt.append("- If you mention specific people or events, use the context provided\n");
        prompt.append("- For questions about clients, refer to the relevant context from emails and CRM data\n");

        return prompt.toString();
    }

    private List<ChatMessage> getConversationHistory(User user, String sessionId) {
        return chatMessageRepository.findByUserAndSessionIdOrderByTimestampDesc(user, sessionId)
                .stream()
                .limit(MAX_HISTORY_MESSAGES) // Limit to prevent token overflow
                .collect(Collectors.toList());
    }

    public List<ChatMessage> getChatHistory(User user, String sessionId) {
        return chatMessageRepository.findByUserAndSessionIdOrderByTimestampAsc(user, sessionId);
    }

    public List<ChatSessionResponse> getUserChatSessions(Long userId) {
        List<String> sessionIds = chatMessageRepository.findDistinctSessionIdsByUserId(userId);

        return sessionIds.stream()
                .map(sessionId -> {
                    List<ChatMessage> messages = chatMessageRepository.findByUserIdAndSessionIdOrderByTimestampAsc(userId, sessionId);

                    if (messages.isEmpty()) {
                        return null;
                    }

                    ChatMessage firstMessage = messages.get(0);
                    ChatMessage lastMessage = messages.get(messages.size() - 1);

                    String preview = firstMessage.getContent();
                    if (preview.length() > 60) {
                        preview = preview.substring(0, 60) + "...";
                    }

                    return  ChatSessionResponse.builder()
                            .sessionId(sessionId)
                            .lastMessageAt(lastMessage.getTimestamp())
                            .preview(preview)
                            .messageCount(messages.size())
                            .build();

                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ChatSessionResponse::getLastMessageAt).reversed())
                .collect(Collectors.toList());
    }

    public void clearHistory(User user, String sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findByUserAndSessionIdOrderByTimestampAsc(user, sessionId);
        chatMessageRepository.deleteAll(messages);
    }
}