package com.advisor.controller;

import com.advisor.model.User;
import com.advisor.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final UserService userService;
    private final ToolService toolService;
    private final ChatService chatService;
    private final GmailService gmailService;
    private final CalendarService calendarService;
    private final HubSpotService hubSpotService;
    private final RAGService ragService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handle Gmail push notifications
     */
    @PostMapping("/gmail")
    public ResponseEntity<?> handleGmailWebhook(@RequestBody String payload) {
        try {
            log.info("Received Gmail webhook: {}", payload);

            JsonNode webhookData = objectMapper.readTree(payload);
            String emailId = webhookData.get("message").get("messageId").asText();
            String userEmail = extractUserEmailFromGmail(webhookData);

            if (userEmail == null) {
                return ResponseEntity.ok("No user email found");
            }

            User user = userService.findOrCreateUser(userEmail, "");
            if (user == null) {
                return ResponseEntity.ok("User not found");
            }

            // Process the new email proactively
            processNewEmail(user, emailId);

            return ResponseEntity.ok("Gmail webhook processed");

        } catch (Exception e) {
            log.error("Error processing Gmail webhook", e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }

    /**
     * Handle Google Calendar push notifications
     */
    @PostMapping("/calendar")
    public ResponseEntity<?> handleCalendarWebhook(@RequestBody String payload) {
        try {
            log.info("Received Calendar webhook: {}", payload);

            JsonNode webhookData = objectMapper.readTree(payload);
            String eventId = webhookData.get("eventId").asText();
            String eventType = webhookData.get("eventType").asText(); // created, updated, deleted
            String userEmail = extractUserEmailFromCalendar(webhookData);

            if (userEmail == null) {
                return ResponseEntity.ok("No user email found");
            }

            User user = userService.findOrCreateUser(userEmail, "");
            if (user == null) {
                return ResponseEntity.ok("User not found");
            }

            // Process calendar event proactively
            processCalendarEvent(user, eventId, eventType);

            return ResponseEntity.ok("Calendar webhook processed");

        } catch (Exception e) {
            log.error("Error processing Calendar webhook", e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }

    /**
     * Handle HubSpot push notifications
     */
    @PostMapping("/hubspot")
    public ResponseEntity<?> handleHubSpotWebhook(@RequestBody String payload) {
        try {
            log.info("Received HubSpot webhook: {}", payload);

            JsonNode webhookData = objectMapper.readTree(payload);
            String objectType = webhookData.get("objectType").asText(); // contact, deal, etc.
            String eventType = webhookData.get("eventType").asText(); // created, updated, deleted
            String contactId = webhookData.get("objectId").asText();
            String userEmail = extractUserEmailFromHubSpot(webhookData);

            if (userEmail == null) {
                return ResponseEntity.ok("No user email found");
            }

            User user = userService.findOrCreateUser(userEmail, "");
            if (user == null) {
                return ResponseEntity.ok("User not found");
            }

            // Process HubSpot event proactively
            processHubSpotEvent(user, objectType, eventType, contactId);

            return ResponseEntity.ok("HubSpot webhook processed");

        } catch (Exception e) {
            log.error("Error processing HubSpot webhook", e);
            return ResponseEntity.status(500).body("Error processing webhook");
        }
    }

    /**
     * Manual trigger for testing proactive responses
     */
    @PostMapping("/test/email")
    public ResponseEntity<?> testEmailWebhook(@RequestBody Map<String, String> testData) {
        try {
            String userEmail = testData.get("userEmail");
            String fromEmail = testData.get("fromEmail");
            String subject = testData.get("subject");
            String body = testData.get("body");

            User user = userService.findOrCreateUser(userEmail, "Test User");

            String response = toolService.processIncomingEmail(user, fromEmail, subject, body);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "response", response,
                    "timestamp", LocalDateTime.now().toString()
            ));

        } catch (Exception e) {
            log.error("Error testing email webhook", e);
            return ResponseEntity.status(500).body("Error testing webhook");
        }
    }

    private void processNewEmail(User user, String emailId) {
        try {
            // In a real implementation, you'd fetch the email details
            // For this example, we'll simulate processing

            log.info("Processing new email for user: {} with email ID: {}", user.getEmail(), emailId);

            // Check if user has ongoing instructions about emails
            if (user.getOngoingInstructions() != null &&
                    user.getOngoingInstructions().toLowerCase().contains("email")) {

                // Trigger proactive AI response
                String sessionId = "proactive_" + System.currentTimeMillis();
                String proactiveMessage = String.format(
                        "A new email (ID: %s) has arrived. Based on ongoing instructions, should I take any proactive action?",
                        emailId
                );

                String response = chatService.processMessage(user, proactiveMessage, sessionId);
                log.info("Proactive email response: {}", response);
            }

            // Re-index emails for updated RAG context
            ragService.indexEmails(user);

        } catch (Exception e) {
            log.error("Error processing new email for user: {}", user.getEmail(), e);
        }
    }

    private void processCalendarEvent(User user, String eventId, String eventType) {
        try {
            log.info("Processing calendar event for user: {} with event ID: {} and type: {}",
                    user.getEmail(), eventId, eventType);

            // Check if user has ongoing instructions about calendar events
            if (user.getOngoingInstructions() != null &&
                    user.getOngoingInstructions().toLowerCase().contains("calendar")) {

                String action = switch (eventType.toLowerCase()) {
                    case "created" -> "A new calendar event was created";
                    case "updated" -> "A calendar event was updated";
                    case "deleted" -> "A calendar event was deleted";
                    default -> "A calendar event was modified";
                };

                // Check for specific ongoing instructions
                if (user.getOngoingInstructions().contains("send an email to attendees")) {
                    handleCalendarEventEmail(user, eventId, eventType);
                }

                // Trigger proactive AI response
                String sessionId = "proactive_calendar_" + System.currentTimeMillis();
                String proactiveMessage = String.format(
                        "%s (ID: %s). Based on ongoing instructions, should I take any proactive action?",
                        action, eventId
                );

                String response = chatService.processMessage(user, proactiveMessage, sessionId);
                log.info("Proactive calendar response: {}", response);
            }

            // Re-index calendar data for updated RAG context
            ragService.indexCalendarData(user);

        } catch (Exception e) {
            log.error("Error processing calendar event for user: {}", user.getEmail(), e);
        }
    }

    private void processHubSpotEvent(User user, String objectType, String eventType, String contactId) {
        try {
            log.info("Processing HubSpot event for user: {} with object type: {}, event type: {}, ID: {}",
                    user.getEmail(), objectType, eventType, contactId);

            if ("contact".equals(objectType) && "created".equals(eventType)) {
                // Check if user has ongoing instructions about new contacts
                if (user.getOngoingInstructions() != null &&
                        user.getOngoingInstructions().contains("send them an email telling them thank you")) {

                    handleNewContactWelcomeEmail(user, contactId);
                }
            }

            // Trigger proactive AI response
            String sessionId = "proactive_hubspot_" + System.currentTimeMillis();
            String proactiveMessage = String.format(
                    "A %s was %s in HubSpot (ID: %s). Based on ongoing instructions, should I take any proactive action?",
                    objectType, eventType, contactId
            );

            String response = chatService.processMessage(user, proactiveMessage, sessionId);
            log.info("Proactive HubSpot response: {}", response);

            // Re-index CRM data for updated RAG context
            ragService.indexCRMData(user);

        } catch (Exception e) {
            log.error("Error processing HubSpot event for user: {}", user.getEmail(), e);
        }
    }

    private void handleCalendarEventEmail(User user, String eventId, String eventType) {
        try {
            if ("created".equals(eventType)) {
                // Get event details and send email to attendees
                String searchResult = calendarService.searchEvents(user, eventId, null, null);

                // Extract attendees from event details (simplified)
                if (searchResult.contains("Attendees:")) {
                    String attendeesLine = searchResult.substring(searchResult.indexOf("Attendees:"));
                    String[] attendees = attendeesLine.split(",");

                    for (String attendee : attendees) {
                        String email = attendee.trim();
                        if (email.contains("@") && !email.equals(user.getEmail())) {
                            String subject = "Meeting Notification";
                            String body = "A new meeting has been scheduled. Please check your calendar for details.";

                            toolService.sendEmail(user, email, subject, body);
                            log.info("Sent meeting notification to: {}", email);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling calendar event email", e);
        }
    }

    private void handleNewContactWelcomeEmail(User user, String contactId) {
        try {
            // Get contact details
            String contactDetails = toolService.searchContacts(user, contactId);

            if (contactDetails.contains("Email:")) {
                // Extract email from contact details (simplified parsing)
                String[] lines = contactDetails.split("\n");
                String email = null;
                String name = null;

                for (String line : lines) {
                    if (line.startsWith("Email:")) {
                        email = line.substring(6).trim();
                    }
                    if (line.startsWith("Name:")) {
                        name = line.substring(5).trim();
                    }
                }

                if (email != null && !email.equals(user.getEmail())) {
                    String subject = "Welcome - Thank you for being a client!";
                    String body = String.format(
                            "Dear %s,\n\n" +
                                    "Thank you for being a valued client! We're excited to work with you and help you achieve your financial goals.\n\n" +
                                    "If you have any questions or need assistance, please don't hesitate to reach out.\n\n" +
                                    "Best regards,\n" +
                                    "Your Financial Advisor Team",
                            name != null ? name : "Valued Client"
                    );

                    toolService.sendEmail(user, email, subject, body);
                    log.info("Sent welcome email to new contact: {}", email);
                }
            }
        } catch (Exception e) {
            log.error("Error handling new contact welcome email", e);
        }
    }

    // Helper methods to extract user email from webhook payloads
    private String extractUserEmailFromGmail(JsonNode webhookData) {
        try {
            // This would extract the user email from Gmail webhook data
            // Implementation depends on the actual webhook payload structure
            return webhookData.get("emailAddress").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserEmailFromCalendar(JsonNode webhookData) {
        try {
            // This would extract the user email from Calendar webhook data
            return webhookData.get("calendarId").asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserEmailFromHubSpot(JsonNode webhookData) {
        try {
            // This would extract the user email from HubSpot webhook data
            return webhookData.get("portalId").asText();
        } catch (Exception e) {
            return null;
        }
    }
}