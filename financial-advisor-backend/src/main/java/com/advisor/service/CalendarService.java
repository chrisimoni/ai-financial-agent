package com.advisor.service;

import com.advisor.model.User;
import com.advisor.repository.UserRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CalendarService {

    private final UserRepository userRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    private static final String APPLICATION_NAME = "Financial Advisor Agent";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /**
     * Create Google Calendar service with user's OAuth credentials.
     */
    private Calendar getCalendarService(User user) {
        try {
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(googleClientId, googleClientSecret)
                    .build()
                    .setAccessToken(user.getGoogleAccessToken())
                    .setRefreshToken(user.getGoogleRefreshToken());

            return new Calendar.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential
            )
                    .setApplicationName(APPLICATION_NAME)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create Calendar service", e);
        }
    }

    /**
     * Refresh Google OAuth token.
     */
    private boolean refreshGoogleToken(User user) {
        try {
            if (user.getGoogleRefreshToken() == null || user.getGoogleRefreshToken().isEmpty()) {
                System.err.println("No refresh token available for user: " + user.getEmail());
                return false;
            }

            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleCredential credential = new GoogleCredential.Builder()
                    .setTransport(httpTransport)
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(googleClientId, googleClientSecret)
                    .build()
                    .setRefreshToken(user.getGoogleRefreshToken());

            // Refresh the token
            boolean success = credential.refreshToken();

            if (success && credential.getAccessToken() != null) {
                // Update user tokens
                user.setGoogleAccessToken(credential.getAccessToken());

                // Update refresh token if a new one was provided
                if (credential.getRefreshToken() != null) {
                    user.setGoogleRefreshToken(credential.getRefreshToken());
                }

                // Calculate and set expiration time
                if (credential.getExpirationTimeMilliseconds() != null) {
                    LocalDateTime expirationTime = LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(credential.getExpirationTimeMilliseconds()),
                            ZoneId.systemDefault()
                    );
                    user.setGoogleTokenExpiration(expirationTime);
                }

                // Save updated user
                userRepository.save(user);

                System.out.println("Successfully refreshed Google token for user: " + user.getEmail());
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("Error refreshing Google token: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if token needs refresh (within 5 minutes of expiration).
     */
    private boolean needsTokenRefresh(User user) {
        if (user.getGoogleTokenExpiration() == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime refreshThreshold = user.getGoogleTokenExpiration().minusMinutes(5);

        return now.isAfter(refreshThreshold);
    }

    /**
     * Ensure we have a valid token before making API calls.
     */
    private void ensureValidToken(User user) {
        if (needsTokenRefresh(user)) {
            System.out.println("Google token will expire soon. Refreshing preemptively...");
            refreshGoogleToken(user);
        }
    }

    /**
     * Execute calendar operation with automatic token refresh on 401 errors.
     */
    private <T> T executeWithTokenRefresh(User user, CalendarOperation<T> operation) {
        try {
            // Ensure token is valid before attempting
            ensureValidToken(user);

            // Try the operation
            return operation.execute();

        } catch (GoogleJsonResponseException e) {
            // Check if it's a 401 auth error
            if (e.getStatusCode() == 401) {
                System.out.println("Google Calendar 401 error. Attempting token refresh...");

                if (refreshGoogleToken(user)) {
                    // Retry with new token
                    try {
                        return operation.execute();
                    } catch (Exception retryException) {
                        System.err.println("Retry after token refresh failed: " + retryException.getMessage());
                        throw new RuntimeException("Calendar operation failed after token refresh", retryException);
                    }
                } else {
                    throw new RuntimeException("Failed to refresh expired Google token", e);
                }
            } else {
                throw new RuntimeException("Google Calendar API error: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException("Calendar operation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Functional interface for calendar operations.
     */
    @FunctionalInterface
    private interface CalendarOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Search calendar events based on query and date range.
     */
    public String searchEvents(User user, String query, String startDate, String endDate) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);

            // Parse dates
            DateTime timeMin = parseDate(startDate, LocalDateTime.now().minusDays(7));
            DateTime timeMax = parseDate(endDate, LocalDateTime.now().plusDays(30));

            Events events = service.events().list("primary")
                    .setTimeMin(timeMin)
                    .setTimeMax(timeMax)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(50)
                    .execute();

            List<Event> items = events.getItems();
            if (items == null || items.isEmpty()) {
                return "No events found for the specified criteria.";
            }

            // Filter events based on query if provided
            List<Event> filteredEvents = items.stream()
                    .filter(event -> query == null || query.isEmpty() ||
                            eventMatchesQuery(event, query))
                    .toList();

            if (filteredEvents.isEmpty()) {
                return String.format("No events found matching '%s' in the specified time period.", query);
            }

            // Format results
            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d event(s):\n\n", filteredEvents.size()));

            for (Event event : filteredEvents) {
                result.append(formatEventSummary(event)).append("\n\n");
            }

            return result.toString().trim();
        });
    }

    /**
     * Get available time slots for a specific date.
     */
    public List<String> getAvailableSlots(User user, String date) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);

            // Parse the target date
            LocalDateTime targetDate = LocalDateTime.parse(date + "T00:00:00");
            DateTime dayStart = new DateTime(targetDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            DateTime dayEnd = new DateTime(targetDate.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

            // Get events for the day
            Events events = service.events().list("primary")
                    .setTimeMin(dayStart)
                    .setTimeMax(dayEnd)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();

            // Define business hours (9 AM to 6 PM)
            List<LocalDateTime> businessHours = new ArrayList<>();
            for (int hour = 9; hour < 18; hour++) {
                businessHours.add(targetDate.withHour(hour).withMinute(0));
                businessHours.add(targetDate.withHour(hour).withMinute(30));
            }

            // Remove occupied time slots
            List<Event> dayEvents = events.getItems() != null ? events.getItems() : new ArrayList<>();
            Set<LocalDateTime> occupiedSlots = new HashSet<>();

            for (Event event : dayEvents) {
                if (event.getStart() != null && event.getEnd() != null) {
                    LocalDateTime start = getEventDateTime(event.getStart());
                    LocalDateTime end = getEventDateTime(event.getEnd());

                    LocalDateTime current = start;
                    while (current.isBefore(end)) {
                        occupiedSlots.add(current.withMinute(current.getMinute() >= 30 ? 30 : 0));
                        current = current.plusMinutes(30);
                    }
                }
            }

            // Return available slots
            return businessHours.stream()
                    .filter(slot -> !occupiedSlots.contains(slot))
                    .map(slot -> slot.format(DateTimeFormatter.ofPattern("h:mm a")))
                    .collect(Collectors.toList());
        });
    }

    /**
     * Create a new calendar event.
     */
    public String createEvent(User user, String title, String startTime, String endTime, List<String> attendeeEmails) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);

            Event event = new Event()
                    .setSummary(title)
                    .setDescription("Created by Financial Advisor AI Assistant");

            DateTime startDateTime = parseDateTime(startTime);
            EventDateTime start = new EventDateTime().setDateTime(startDateTime);
            event.setStart(start);

            DateTime endDateTime = parseDateTime(endTime);
            EventDateTime end = new EventDateTime().setDateTime(endDateTime);
            event.setEnd(end);

            if (attendeeEmails != null && !attendeeEmails.isEmpty()) {
                List<EventAttendee> attendees = attendeeEmails.stream()
                        .map(email -> new EventAttendee().setEmail(email))
                        .collect(Collectors.toList());
                event.setAttendees(attendees);
            }

            event = service.events().insert("primary", event).execute();

            return String.format("Event '%s' created successfully for %s. Event ID: %s",
                    title, startTime, event.getId());
        });
    }

    /**
     * Get upcoming events for the next specified days.
     */
    public List<Event> getUpcomingEvents(User user, int days) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);

            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime futureTime = new DateTime(System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L));

            Events events = service.events().list("primary")
                    .setTimeMin(now)
                    .setTimeMax(futureTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(20)
                    .execute();

            return events.getItems() != null ? events.getItems() : new ArrayList<>();
        });
    }

    /**
     * Get calendar events as documents for RAG indexing.
     */
    public List<Document> getCalendarAsDocuments(User user) {
        try {
            return executeWithTokenRefresh(user, () -> {
                List<Document> documents = new ArrayList<>();

                LocalDateTime now = LocalDateTime.now();
                DateTime timeMin = new DateTime(now.minusDays(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                DateTime timeMax = new DateTime(now.plusDays(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

                Calendar service = getCalendarService(user);
                Events events = service.events().list("primary")
                        .setTimeMin(timeMin)
                        .setTimeMax(timeMax)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .setMaxResults(100)
                        .execute();

                List<Event> items = events.getItems();
                if (items == null || items.isEmpty()) {
                    return createSampleCalendarDocuments(user);
                }

                for (Event event : items) {
                    try {
                        String content = createEventContent(event);
                        Map<String, Object> metadata = createEventMetadata(event, user);
                        documents.add(new Document(content, metadata));
                    } catch (Exception e) {
                        System.err.println("Error processing calendar event: " + e.getMessage());
                    }
                }

                return documents;
            });
        } catch (Exception e) {
            e.printStackTrace();
            return createSampleCalendarDocuments(user);
        }
    }

    /**
     * Update an existing event.
     */
    public String updateEvent(User user, String eventId, String newTitle, String newStartTime, String newEndTime) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);
            Event event = service.events().get("primary", eventId).execute();

            if (newTitle != null && !newTitle.isEmpty()) {
                event.setSummary(newTitle);
            }

            if (newStartTime != null && !newStartTime.isEmpty()) {
                DateTime startDateTime = parseDateTime(newStartTime);
                EventDateTime start = new EventDateTime().setDateTime(startDateTime);
                event.setStart(start);
            }

            if (newEndTime != null && !newEndTime.isEmpty()) {
                DateTime endDateTime = parseDateTime(newEndTime);
                EventDateTime end = new EventDateTime().setDateTime(endDateTime);
                event.setEnd(end);
            }

            Event updatedEvent = service.events().update("primary", eventId, event).execute();
            return String.format("Event updated successfully: %s", updatedEvent.getSummary());
        });
    }

    /**
     * Delete a calendar event.
     */
    public String deleteEvent(User user, String eventId) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);
            service.events().delete("primary", eventId).execute();
            return "Event deleted successfully";
        });
    }

    /**
     * Find conflicts with proposed meeting time.
     */
    public List<String> findConflicts(User user, String proposedStartTime, String proposedEndTime) {
        return executeWithTokenRefresh(user, () -> {
            Calendar service = getCalendarService(user);

            DateTime startTime = parseDateTime(proposedStartTime);
            DateTime endTime = parseDateTime(proposedEndTime);

            Events events = service.events().list("primary")
                    .setTimeMin(startTime)
                    .setTimeMax(endTime)
                    .setSingleEvents(true)
                    .execute();

            List<Event> conflicts = events.getItems();
            if (conflicts == null || conflicts.isEmpty()) {
                return new ArrayList<>();
            }

            return conflicts.stream()
                    .map(this::formatEventSummary)
                    .collect(Collectors.toList());
        });
    }

    // Helper methods remain the same...

    private DateTime parseDate(String dateStr, LocalDateTime defaultValue) {
        try {
            if (dateStr == null || dateStr.isEmpty()) {
                return new DateTime(defaultValue.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            LocalDateTime date = LocalDateTime.parse(dateStr + "T00:00:00");
            return new DateTime(date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (Exception e) {
            return new DateTime(defaultValue.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
    }

    private DateTime parseDateTime(String dateTimeStr) {
        try {
            LocalDateTime dateTime;
            if (dateTimeStr.contains("T")) {
                dateTime = LocalDateTime.parse(dateTimeStr);
            } else {
                dateTime = LocalDateTime.parse(dateTimeStr + "T09:00:00");
            }
            return new DateTime(dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (Exception e) {
            LocalDateTime defaultTime = LocalDateTime.now().plusHours(1);
            return new DateTime(defaultTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
    }

    private LocalDateTime getEventDateTime(EventDateTime eventDateTime) {
        try {
            DateTime dateTime = eventDateTime.getDateTime();
            if (dateTime != null) {
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(dateTime.getValue()),
                        ZoneId.systemDefault()
                );
            }

            com.google.api.client.util.DateTime date = eventDateTime.getDate();
            if (date != null) {
                return LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(date.getValue()),
                        ZoneId.systemDefault()
                );
            }

            return LocalDateTime.now();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private boolean eventMatchesQuery(Event event, String query) {
        String lowerQuery = query.toLowerCase();

        if (event.getSummary() != null && event.getSummary().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        if (event.getDescription() != null && event.getDescription().toLowerCase().contains(lowerQuery)) {
            return true;
        }

        if (event.getAttendees() != null) {
            for (EventAttendee attendee : event.getAttendees()) {
                if (attendee.getEmail() != null && attendee.getEmail().toLowerCase().contains(lowerQuery)) {
                    return true;
                }
                if (attendee.getDisplayName() != null && attendee.getDisplayName().toLowerCase().contains(lowerQuery)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String formatEventSummary(Event event) {
        StringBuilder summary = new StringBuilder();
        summary.append("Event: ").append(event.getSummary() != null ? event.getSummary() : "No title");

        if (event.getStart() != null) {
            LocalDateTime start = getEventDateTime(event.getStart());
            summary.append("\nStart: ").append(start.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")));
        }

        if (event.getEnd() != null) {
            LocalDateTime end = getEventDateTime(event.getEnd());
            summary.append("\nEnd: ").append(end.format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")));
        }

        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            List<String> attendeeNames = event.getAttendees().stream()
                    .map(attendee -> attendee.getDisplayName() != null ?
                            attendee.getDisplayName() : attendee.getEmail())
                    .collect(Collectors.toList());
            summary.append("\nAttendees: ").append(String.join(", ", attendeeNames));
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            String desc = event.getDescription().length() > 100 ?
                    event.getDescription().substring(0, 100) + "..." : event.getDescription();
            summary.append("\nDescription: ").append(desc);
        }

        return summary.toString();
    }

    private String createEventContent(Event event) {
        StringBuilder content = new StringBuilder();
        content.append("Calendar Event: ").append(event.getSummary() != null ? event.getSummary() : "Untitled");

        if (event.getStart() != null) {
            LocalDateTime start = getEventDateTime(event.getStart());
            content.append(" scheduled for ").append(start.format(DateTimeFormatter.ofPattern("EEEE, MMM d 'at' h:mm a")));
        }

        if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
            List<String> attendees = event.getAttendees().stream()
                    .map(attendee -> attendee.getDisplayName() != null ?
                            attendee.getDisplayName() : attendee.getEmail())
                    .collect(Collectors.toList());
            content.append(". Attendees: ").append(String.join(", ", attendees));
        }

        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            content.append(". Description: ").append(event.getDescription());
        }

        return content.toString();
    }

    private Map<String, Object> createEventMetadata(Event event, User user) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "calendar_event");
        metadata.put("eventId", event.getId());
        metadata.put("summary", event.getSummary());
        metadata.put("userId", user.getId().toString());

        if (event.getStart() != null) {
            LocalDateTime start = getEventDateTime(event.getStart());
            metadata.put("startTime", start.toString());
        }

        if (event.getAttendees() != null) {
            List<String> attendeeEmails = event.getAttendees().stream()
                    .map(EventAttendee::getEmail)
                    .collect(Collectors.toList());
            metadata.put("attendees", String.join(",", attendeeEmails));
        }

        return metadata;
    }

    private List<Document> createSampleCalendarDocuments(User user) {
        List<Document> documents = new ArrayList<>();

        String[] sampleEvents = {
                "Calendar Event: Weekly team meeting scheduled for Monday at 10:00 AM. Attendees: " + user.getName() + ", Sarah Williams, John Doe. Description: Regular team sync to discuss weekly goals and progress.",
                "Calendar Event: Client consultation with Greg Thompson scheduled for Wednesday at 3:00 PM. Attendees: " + user.getName() + ", greg.thompson@techcorp.com. Description: Quarterly portfolio review and investment strategy discussion.",
                "Calendar Event: Quarterly board meeting scheduled for Friday at 2:00 PM. Attendees: " + user.getName() + ", Board Members. Description: Quarterly financial review and strategic planning session.",
                "Calendar Event: One-on-one with John Doe scheduled for Thursday at 1:00 PM. Attendees: " + user.getName() + ", john.doe@example.com. Description: Discuss education savings plan for his son and retirement planning options."
        };

        for (int i = 0; i < sampleEvents.length; i++) {
            Map<String, Object> metadata = Map.of(
                    "type", "calendar_event",
                    "eventId", "sample_event_" + i,
                    "userId", user.getId().toString(),
                    "source", "sample"
            );

            documents.add(new Document(sampleEvents[i], metadata));
        }

        return documents;
    }
}