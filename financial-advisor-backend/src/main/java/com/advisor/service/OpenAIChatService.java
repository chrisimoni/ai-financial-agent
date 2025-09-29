package com.advisor.service;

import com.advisor.dto.FunctionRequestDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatFunction;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIChatService {

    private final OpenAiService openAiService;
    private final ToolService toolService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Process a chat message with function calling support.
     */
    public String processMessage(String systemPrompt, List<com.advisor.model.ChatMessage> history,
                                 String userMessage, com.advisor.model.User user) {
        try {
            List<ChatMessage> messages = convertMessages(history, systemPrompt, userMessage);

            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .model("gpt-4")
                    .messages(messages)
                    .functions(getFunctionDefinitions())
                    .functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of("auto"))
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();

            ChatCompletionResult result = openAiService.createChatCompletion(request);
            ChatMessage responseMessage = result.getChoices().get(0).getMessage();

            if (responseMessage.getFunctionCall() != null) {
                return handleFunctionCall(responseMessage.getFunctionCall(), messages, user);
            }

            return responseMessage.getContent();

        } catch (Exception e) {
            System.err.println("Error processing chat message: " + e.getMessage());
            e.printStackTrace();
            return "I apologize, but I encountered an error processing your request. Please try again.";
        }
    }

    /**
     * Convert ChatMessage entities to OpenAI ChatMessage format.
     */
    private List<ChatMessage> convertMessages(List<com.advisor.model.ChatMessage> history,
                                              String systemPrompt, String userMessage) {
        List<ChatMessage> messages = new ArrayList<>();

        // Add system message
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt));

        // Add conversation history
        for (com.advisor.model.ChatMessage msg : history) {
            ChatMessageRole role = switch (msg.getRole()) {
                case USER -> ChatMessageRole.USER;
                case ASSISTANT -> ChatMessageRole.ASSISTANT;
                case SYSTEM -> ChatMessageRole.SYSTEM;
            };
            messages.add(new ChatMessage(role.value(), msg.getContent()));
        }

        // Add current user message
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));

        return messages;
    }

    /**
     * Handle function call execution and get final response.
     */
    private String handleFunctionCall(ChatFunctionCall functionCall, List<ChatMessage> messages,
                                      com.advisor.model.User user) {
        try {
            String functionName = functionCall.getName();
            String arguments = String.valueOf(functionCall.getArguments());

            // Parse function arguments
            Map<String, Object> args = objectMapper.readValue(arguments, Map.class);

            // Execute the function - this should return result OR error as JSON string
            String result = executeFunction(functionName, args, user);

            // Add assistant's function call to message history
            messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), null, null, functionCall));

            // Add function result to message history (ALWAYS do this, even on error)
            messages.add(new ChatMessage(ChatMessageRole.FUNCTION.value(), result, functionName));

            // Get final response from OpenAI after function execution
            ChatCompletionRequest followUpRequest = ChatCompletionRequest.builder()
                    .model("gpt-4")
                    .messages(messages)
                    .temperature(0.7)
                    .maxTokens(1000)
                    .build();

            ChatCompletionResult followUpResult = openAiService.createChatCompletion(followUpRequest);
            return followUpResult.getChoices().get(0).getMessage().getContent();

        } catch (Exception e) {
            // If there's an error in the flow itself (not function execution)
            log.error("Error in function call flow: {}", e.getMessage(), e);

            // Still try to inform the AI about the error
            try {
                String errorResult = String.format(
                        "{\"success\": false, \"error\": \"System error during function execution: %s\"}",
                        e.getMessage().replace("\"", "'")
                );

                messages.add(new ChatMessage(ChatMessageRole.ASSISTANT.value(), null, null, functionCall));
                messages.add(new ChatMessage(ChatMessageRole.FUNCTION.value(), errorResult, functionCall.getName()));

                ChatCompletionRequest errorRequest = ChatCompletionRequest.builder()
                        .model("gpt-4")
                        .messages(messages)
                        .temperature(0.7)
                        .maxTokens(1000)
                        .build();

                ChatCompletionResult errorResponse = openAiService.createChatCompletion(errorRequest);
                return errorResponse.getChoices().get(0).getMessage().getContent();

            } catch (Exception innerException) {
                return "I apologize, but I encountered a technical error while processing your request. Please try again.";
            }
        }
    }

    /**
     * Execute the appropriate function based on function name and arguments.
     */
    private String executeFunction(String functionName, Map<String, Object> args,
                                   com.advisor.model.User user) {
        try {
            return switch (functionName) {
                case "sendEmail" ->
                        toolService.sendEmail(
                                user,
                                (String) args.get("to"),
                                (String) args.get("subject"),
                                (String) args.get("body")
                        );

                case "scheduleAppointment" ->
                        toolService.scheduleAppointment(
                                user,
                                (String) args.get("contactName"),
                                (String) args.get("proposedTimes")
                        );

                case "createContact" ->
                        toolService.createContact(
                                user,
                                (String) args.get("name"),
                                (String) args.get("email"),
                                (String) args.get("company"),
                                (String) args.get("notes")
                        );

                case "searchCalendar" ->
                        toolService.searchCalendar(
                                user,
                                (String) args.get("query"),
                                (String) args.get("startDate"),
                                (String) args.get("endDate")
                        );

                case "getAvailableSlots" ->
                        toolService.getAvailableSlots(
                                user,
                                (String) args.get("date")
                        );

                case "createCalendarEvent" ->
                        toolService.createCalendarEvent(
                                user,
                                (String) args.get("title"),
                                (String) args.get("startTime"),
                                (String) args.get("endTime"),
                                (String) args.get("attendees")
                        );

                case "checkCalendarConflicts" ->
                        toolService.checkCalendarConflicts(
                                user,
                                (String) args.get("startTime"),
                                (String) args.get("endTime")
                        );

                case "getUpcomingMeetings" ->
                        toolService.getUpcomingMeetings(
                                user,
                                (String) args.get("days")
                        );

                case "updateCalendarEvent" ->
                        toolService.updateCalendarEvent(
                                user,
                                (String) args.get("eventId"),
                                (String) args.get("newTitle"),
                                (String) args.get("newStartTime"),
                                (String) args.get("newEndTime")
                        );

                case "deleteCalendarEvent" ->
                        toolService.deleteCalendarEvent(
                                user,
                                (String) args.get("eventId")
                        );

                case "searchContacts" ->
                        toolService.searchContacts(
                                user,
                                (String) args.get("query")
                        );

                default -> "Unknown function: " + functionName;
            };
        }  catch (HttpClientErrorException.Unauthorized e) {
            log.error("Authentication failed for function {}: {}", functionName, e.getMessage());
            return createErrorResponse("Authentication failed. Please reconnect your HubSpot account.");

        } catch (Exception e) {
            log.error("Error executing function {}: {}", functionName, e.getMessage(), e);
            return createErrorResponse("Failed to execute " + functionName + ": " + e.getMessage());
        }
    }

    /**
     * Get all function definitions for OpenAI.
     */
    private List<ChatFunction> getFunctionDefinitions() {
        List<ChatFunction> functions = new ArrayList<>();

        // Email functions
        functions.add(createSendEmailFunction());

        // Contact functions
        functions.add(createContactFunction());
        functions.add(createSearchContactsFunction());

        // Calendar functions
        functions.add(createSearchCalendarFunction());
        functions.add(createGetAvailableSlotsFunction());
        functions.add(createCalendarEventFunction());
        functions.add(createCheckConflictsFunction());
        functions.add(createGetUpcomingMeetingsFunction());
        functions.add(createUpdateCalendarEventFunction());
        functions.add(createDeleteCalendarEventFunction());

        // Appointment scheduling
        functions.add(createScheduleAppointmentFunction());

        return functions;
    }

    private ChatFunction createSendEmailFunction() {
        return ChatFunction.builder()
                .name("sendEmail")
                .description("Send an email to a contact")
                .executor(SendEmailRequest.class, request -> null)
                .build();
    }

    private ChatFunction createScheduleAppointmentFunction() {
        return ChatFunction.builder()
                .name("scheduleAppointment")
                .description("Schedule an appointment with a contact by sending them an email with proposed times")
                .executor(ScheduleAppointmentRequest.class, request -> null)
                .build();
    }

    private ChatFunction createContactFunction() {
        return ChatFunction.builder()
                .name("createContact")
                .description("Create a new contact in the CRM system")
                .executor(CreateContactRequest.class, request -> null)
                .build();
    }

    private ChatFunction createSearchCalendarFunction() {
        return ChatFunction.builder()
                .name("searchCalendar")
                .description("Search for calendar events by query, date range, or attendees")
                .executor(SearchCalendarRequest.class, request -> null)
                .build();
    }

    private ChatFunction createGetAvailableSlotsFunction() {
        return ChatFunction.builder()
                .name("getAvailableSlots")
                .description("Get available time slots for a specific date")
                .executor(GetAvailableSlotsRequest.class, request -> null)
                .build();
    }

    private ChatFunction createCalendarEventFunction() {
        return ChatFunction.builder()
                .name("createCalendarEvent")
                .description("Create a new calendar event")
                .executor(CreateCalendarEventRequest.class, request -> null)
                .build();
    }

    private ChatFunction createCheckConflictsFunction() {
        return ChatFunction.builder()
                .name("checkCalendarConflicts")
                .description("Check for calendar conflicts at a proposed time")
                .executor(CheckConflictsRequest.class, request -> null)
                .build();
    }

    private ChatFunction createGetUpcomingMeetingsFunction() {
        return ChatFunction.builder()
                .name("getUpcomingMeetings")
                .description("Get upcoming meetings for the next specified number of days")
                .executor(GetUpcomingMeetingsRequest.class, request -> null)
                .build();
    }

    private ChatFunction createUpdateCalendarEventFunction() {
        return ChatFunction.builder()
                .name("updateCalendarEvent")
                .description("Update an existing calendar event")
                .executor(UpdateCalendarEventRequest.class, request -> null)
                .build();
    }

    private ChatFunction createDeleteCalendarEventFunction() {
        return ChatFunction.builder()
                .name("deleteCalendarEvent")
                .description("Delete a calendar event")
                .executor(DeleteCalendarEventRequest.class, request -> null)
                .build();
    }

    private ChatFunction createSearchContactsFunction() {
        return ChatFunction.builder()
                .name("searchContacts")
                .description("Search for contacts in the CRM system")
                .executor(SearchContactsRequest.class, request -> null)
                .build();
    }

    private String createErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", errorMessage
            ));
        } catch (JsonProcessingException e) {
            return "{\"success\": false, \"error\": \"" + errorMessage.replace("\"", "'") + "\"}";
        }
    }
}