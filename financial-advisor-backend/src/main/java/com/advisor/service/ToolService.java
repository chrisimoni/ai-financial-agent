package com.advisor.service;

import com.advisor.model.Task;
import com.advisor.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ToolService {

    private final GmailService gmailService;
    private final CalendarService calendarService;
    private final HubSpotService hubSpotService;
    private final TaskService taskService;
    private final VectorService vectorService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sendEmail(User user, String to, String subject, String body) {
        try {
            return gmailService.sendEmail(user, to, subject, body);
        } catch (Exception e) {
            return "Failed to send email: " + e.getMessage();
        }
    }

    public String scheduleAppointment(User user, String contactName, String proposedTimes) {
        try {
            // Look up contact information first
            List<Map<String, Object>> contacts = hubSpotService.searchContacts(user, contactName);

            if (contacts.isEmpty()) {
                return String.format("Contact '%s' not found. Please create the contact first or provide their email address.", contactName);
            }

            Map<String, Object> contact = contacts.get(0);
            String contactEmail = (String) contact.get("email");

            // Create scheduling task
            Task schedulingTask = new Task(user,
                    "Schedule appointment with " + contactName,
                    "Proposed times: " + proposedTimes,
                    Task.TaskType.SCHEDULE_APPOINTMENT);

            String context = String.format(
                    "{\"contactName\": \"%s\", \"contactEmail\": \"%s\", \"proposedTimes\": \"%s\", \"step\": \"send_invitation\"}",
                    contactName, contactEmail, proposedTimes
            );
            schedulingTask.setContext(context);
            taskService.createTask(schedulingTask);

            // Send initial appointment request email
            String subject = "Meeting Request - Let's Schedule a Time";
            String emailBody = String.format(
                    "Hi %s,\n\n" +
                            "I hope this email finds you well. I'd like to schedule a meeting with you.\n\n" +
                            "I have the following times available:\n%s\n\n" +
                            "Please let me know which time works best for you, or if you'd prefer a different time.\n\n" +
                            "Looking forward to hearing from you!\n\n" +
                            "Best regards",
                    contactName, proposedTimes
            );

            String emailResult = sendEmail(user, contactEmail, subject, emailBody);

            return String.format("Appointment request sent to %s (%s). %s", contactName, contactEmail, emailResult);

        } catch (Exception e) {
            return createErrorResponse("Failed to initiate scheduling: " + e.getMessage());
        }
    }

    public String createContact(User user, String name, String email, String company, String notes) {
        try {
            return hubSpotService.createContact(user, name, email, company, notes);
        } catch (Exception e) {
            return createErrorResponse("Failed to create contact: " + e.getMessage());

        }
    }

    public String searchCalendar(User user, String query, String startDate, String endDate) {
        try {
            return calendarService.searchEvents(user, query, startDate, endDate);
        } catch (Exception e) {
            return createErrorResponse("Failed to search calendar: " + e.getMessage());
        }
    }

    public String getAvailableSlots(User user, String date) {
        try {
            List<String> slots = calendarService.getAvailableSlots(user, date);
            if (slots.isEmpty()) {
                return String.format("No available slots found for %s", date);
            }
            return String.format("Available time slots for %s: %s", date, String.join(", ", slots));
        } catch (Exception e) {
            return createErrorResponse("Failed to get available slots: " + e.getMessage());
        }
    }

    public String createCalendarEvent(User user, String title, String startTime, String endTime, String attendeesStr) {
        try {
            List<String> attendees = attendeesStr != null ?
                    List.of(attendeesStr.split(",\\s*")) : List.of();

            return calendarService.createEvent(user, title, startTime, endTime, attendees);
        } catch (Exception e) {
            return createErrorResponse("Failed to create calendar event: " + e.getMessage());
        }
    }

    public String checkCalendarConflicts(User user, String startTime, String endTime) {
        try {
            List<String> conflicts = calendarService.findConflicts(user, startTime, endTime);
            if (conflicts.isEmpty()) {
                return "No conflicts found for the proposed time.";
            }
            return "Found conflicts:\n" + String.join("\n", conflicts);
        } catch (Exception e) {
            return createErrorResponse("Failed to check conflicts: " + e.getMessage());
        }
    }

    public String getUpcomingMeetings(User user, String days) {
        try {
            int numDays = days != null ? Integer.parseInt(days) : 7;
            return calendarService.searchEvents(user, null,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                    LocalDateTime.now().plusDays(numDays).format(DateTimeFormatter.ISO_LOCAL_DATE));
        } catch (Exception e) {
            return createErrorResponse("Failed to get upcoming meetings: " + e.getMessage());
        }
    }

    public String updateCalendarEvent(User user, String eventId, String newTitle, String newStartTime, String newEndTime) {
        try {
            return calendarService.updateEvent(user, eventId, newTitle, newStartTime, newEndTime);
        } catch (Exception e) {
            return createErrorResponse("Failed to update calendar event: " + e.getMessage());
        }
    }

    public String deleteCalendarEvent(User user, String eventId) {
        try {
            return calendarService.deleteEvent(user, eventId);
        } catch (Exception e) {
            return createErrorResponse("Failed to delete calendar event: " + e.getMessage());
        }
    }

    public String searchContacts(User user, String query) {
        try {
            List<Map<String, Object>> contacts = hubSpotService.searchContacts(user, query);
            if (contacts.isEmpty()) {
                return String.format("No contacts found matching '%s'", query);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d contact(s):\n\n", contacts.size()));

            for (Map<String, Object> contact : contacts) {
                result.append(String.format("Name: %s\n", contact.get("name")));
                result.append(String.format("Email: %s\n", contact.get("email")));
                if (contact.get("company") != null) {
                    result.append(String.format("Company: %s\n", contact.get("company")));
                }
                if (contact.get("notes") != null) {
                    result.append(String.format("Notes: %s\n", contact.get("notes")));
                }
                result.append("\n");
            }

            return result.toString().trim();
        } catch (Exception e) {
            return createErrorResponse("Failed to search contacts: " + e.getMessage());
        }
    }

    /**
     * Handle proactive responses to incoming emails
     */
    public String processIncomingEmail(User user, String fromEmail, String subject, String body) {
        try {
            // Check if this is a response to a scheduling request
            if (isSchedulingResponse(subject, body)) {
                return handleSchedulingResponse(user, fromEmail, subject, body);
            }

            // Check if this is a meeting inquiry
            if (isMeetingInquiry(body)) {
                return handleMeetingInquiry(user, fromEmail, subject, body);
            }

            // Check ongoing instructions
            if (user.getOngoingInstructions() != null &&
                    user.getOngoingInstructions().contains("create a contact in Hubspot")) {

                // Look for sender in contacts
                List<Map<String, Object>> contacts = hubSpotService.searchContacts(user, fromEmail);
                if (contacts.isEmpty()) {
                    // Extract name from email or use email as name
                    String name = extractNameFromEmail(fromEmail);
                    String result = createContact(user, name, fromEmail, "",
                            String.format("Auto-created from email: %s", subject));
                    return "Proactive action taken: " + result;
                }
            }

            return "No proactive action needed for this email.";

        } catch (Exception e) {
            return createErrorResponse("Error processing incoming email: " + e.getMessage());
        }
    }

    private boolean isSchedulingResponse(String subject, String body) {
        String lowerSubject = subject.toLowerCase();
        String lowerBody = body.toLowerCase();

        return (lowerSubject.contains("meeting") || lowerSubject.contains("appointment") ||
                lowerSubject.contains("schedule")) &&
                (lowerBody.contains("works for me") || lowerBody.contains("available") ||
                        lowerBody.contains("good time") || lowerBody.contains("confirm"));
    }

    private boolean isMeetingInquiry(String body) {
        String lowerBody = body.toLowerCase();
        return lowerBody.contains("when is our meeting") ||
                lowerBody.contains("what time is our") ||
                lowerBody.contains("upcoming meeting") ||
                lowerBody.contains("next appointment");
    }

    private String handleSchedulingResponse(User user, String fromEmail, String subject, String body) {
        try {
            // Look up the contact
            List<Map<String, Object>> contacts = hubSpotService.searchContacts(user, fromEmail);
            if (contacts.isEmpty()) {
                return "Could not find contact for email: " + fromEmail;
            }

            String contactName = (String) contacts.get(0).get("name");

            // Parse the response to extract preferred time
            String preferredTime = extractPreferredTime(body);

            if (preferredTime != null) {
                // Create the calendar event
                String eventTitle = "Meeting with " + contactName;
                String startTime = preferredTime;
                String endTime = calculateEndTime(preferredTime);

                String eventResult = createCalendarEvent(user, eventTitle, startTime, endTime, fromEmail);

                // Send confirmation email
                String confirmationSubject = "Meeting Confirmed - " + eventTitle;
                String confirmationBody = String.format(
                        "Hi %s,\n\n" +
                                "Perfect! I've scheduled our meeting for %s.\n\n" +
                                "I've added it to my calendar and look forward to our conversation.\n\n" +
                                "Best regards",
                        contactName, preferredTime
                );

                sendEmail(user, fromEmail, confirmationSubject, confirmationBody);

                return String.format("Meeting scheduled with %s for %s. Confirmation sent.", contactName, preferredTime);
            }

            return "Could not extract preferred time from response.";

        } catch (Exception e) {
            return createErrorResponse("Error handling scheduling response: " + e.getMessage());
        }
    }

    private String handleMeetingInquiry(User user, String fromEmail, String subject, String body) {
        try {
            // Search for upcoming meetings with this contact
            String searchResult = calendarService.searchEvents(user, fromEmail,
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                    LocalDateTime.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE));

            String responseBody;
            if (searchResult.contains("No events found")) {
                responseBody = "I don't see any upcoming meetings scheduled between us. Would you like to schedule one?";
            } else {
                responseBody = "Here are our upcoming meetings:\n\n" + searchResult;
            }

            sendEmail(user, fromEmail, "Re: " + subject, responseBody);

            return "Responded to meeting inquiry from " + fromEmail;

        } catch (Exception e) {
            return createErrorResponse("Error handling meeting inquiry: " + e.getMessage());
        }
    }

    private String extractNameFromEmail(String email) {
        // Simple name extraction from email
        String localPart = email.substring(0, email.indexOf('@'));
        return localPart.replace('.', ' ').replace('_', ' ');
    }

    private String extractPreferredTime(String body) {
        // Simple time extraction - in a real implementation, we'll use NLP
        if (body.toLowerCase().contains("monday")) return "2024-01-15T10:00:00";
        if (body.toLowerCase().contains("tuesday")) return "2024-01-16T10:00:00";
        if (body.toLowerCase().contains("wednesday")) return "2024-01-17T10:00:00";
        if (body.toLowerCase().contains("thursday")) return "2024-01-18T10:00:00";
        if (body.toLowerCase().contains("friday")) return "2024-01-19T10:00:00";

        // Try to extract time patterns
        if (body.contains("10:00") || body.contains("10 AM")) return "2024-01-15T10:00:00";
        if (body.contains("2:00") || body.contains("2 PM")) return "2024-01-15T14:00:00";
        if (body.contains("3:00") || body.contains("3 PM")) return "2024-01-15T15:00:00";

        return null;
    }

    private String calculateEndTime(String startTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            return start.plusHours(1).toString();
        } catch (Exception e) {
            return startTime; // Fallback to same time
        }
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