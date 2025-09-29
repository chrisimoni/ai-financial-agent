package com.advisor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * Data Transfer Objects for OpenAI function calling.
 * These classes define the schema for function parameters.
 */
public class FunctionRequestDtos {

    /**
     * Request to send an email.
     */
    @Data
    public static class SendEmailRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Recipient email address")
        public String to;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Email subject line")
        public String subject;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Email body content")
        public String body;
    }

    /**
     * Request to schedule an appointment.
     */
    @Data
    public static class ScheduleAppointmentRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Name of the contact to schedule with")
        public String contactName;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Proposed meeting times and dates")
        public String proposedTimes;
    }

    /**
     * Request to create a new contact.
     */
    @Data
    public static class CreateContactRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Contact's full name")
        public String name;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Contact's email address")
        public String email;

        @JsonPropertyDescription("Contact's company name")
        public String company;

        @JsonPropertyDescription("Additional notes about the contact")
        public String notes;
    }

    /**
     * Request to search calendar events.
     */
    @Data
    public static class SearchCalendarRequest {
        @JsonPropertyDescription("Search query for calendar events")
        public String query;

        @JsonPropertyDescription("Start date for search (YYYY-MM-DD format)")
        public String startDate;

        @JsonPropertyDescription("End date for search (YYYY-MM-DD format)")
        public String endDate;
    }

    /**
     * Request to get available time slots.
     */
    @Data
    public static class GetAvailableSlotsRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Date to check availability (YYYY-MM-DD format)")
        public String date;
    }

    /**
     * Request to create a calendar event.
     */
    @Data
    public static class CreateCalendarEventRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Event title")
        public String title;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Start time (YYYY-MM-DDTHH:MM:SS format)")
        public String startTime;

        @JsonProperty(required = true)
        @JsonPropertyDescription("End time (YYYY-MM-DDTHH:MM:SS format)")
        public String endTime;

        @JsonPropertyDescription("Comma-separated list of attendee email addresses")
        public String attendees;
    }

    /**
     * Request to check calendar conflicts.
     */
    @Data
    public static class CheckConflictsRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Proposed start time (YYYY-MM-DDTHH:MM:SS format)")
        public String startTime;

        @JsonProperty(required = true)
        @JsonPropertyDescription("Proposed end time (YYYY-MM-DDTHH:MM:SS format)")
        public String endTime;
    }

    /**
     * Request to get upcoming meetings.
     */
    @Data
    public static class GetUpcomingMeetingsRequest {
        @JsonPropertyDescription("Number of days ahead to look (default 7)")
        public String days;
    }

    /**
     * Request to update a calendar event.
     */
    @Data
    public static class UpdateCalendarEventRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Calendar event ID to update")
        public String eventId;

        @JsonPropertyDescription("New event title (optional)")
        public String newTitle;

        @JsonPropertyDescription("New start time in YYYY-MM-DDTHH:MM:SS format (optional)")
        public String newStartTime;

        @JsonPropertyDescription("New end time in YYYY-MM-DDTHH:MM:SS format (optional)")
        public String newEndTime;
    }

    /**
     * Request to delete a calendar event.
     */
    @Data
    public static class DeleteCalendarEventRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Calendar event ID to delete")
        public String eventId;
    }

    /**
     * Request to search contacts.
     */
    @Data
    public static class SearchContactsRequest {
        @JsonProperty(required = true)
        @JsonPropertyDescription("Search query for contacts (name, email, or company)")
        public String query;
    }
}