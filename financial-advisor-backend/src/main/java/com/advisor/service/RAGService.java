package com.advisor.service;

import com.advisor.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RAGService {

    private final VectorService vectorService;
    private final GmailService gmailService;
    private final HubSpotService hubSpotService;
    private final CalendarService calendarService;


    /**
     * Retrieve relevant context for a user query using vector similarity search.
     */
    public String retrieveRelevantContext(User user, String query) {
        try {
            // Get relevant documents from vector store
            List<String> similarDocuments = vectorService.searchSimilar(query, 5);

            if (similarDocuments.isEmpty()) {
                return "No relevant context found.";
            }

            // Format context
            StringBuilder context = new StringBuilder();
            context.append("RELEVANT INFORMATION:\n\n");

            for (int i = 0; i < similarDocuments.size(); i++) {
                context.append(String.format("%d. %s\n\n", i + 1, similarDocuments.get(i)));
            }

            return context.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Index emails into vector store for RAG search.
     */
    public void indexEmails(User user) {
        try {
            //System.out.println("Starting email indexing for user: " + user.getEmail());

            // Get emails as documents from Gmail service
            List<Document> emailDocuments = gmailService.getEmailsAsDocuments(user);

            if (emailDocuments.isEmpty()) {
                //System.out.println("No emails found to index for user: " + user.getEmail());
                // Add sample data for testing
                addSampleEmailData(user);
                return;
            }

            // Add each email document to vector store
            int indexed = 0;
            for (Document doc : emailDocuments) {
                try {
                    vectorService.addDocument(doc.getFormattedContent(), doc.getMetadata(), user);
                    indexed++;
                } catch (Exception e) {
                    System.err.println("Error indexing email document: " + e.getMessage());
                }
            }

            System.out.println(String.format("Successfully indexed %d emails for user: %s", indexed, user.getEmail()));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to index emails for user: " + user.getEmail());
        }
    }

    /**
     * Index CRM contacts and notes into vector store.
     */
    public void indexCRMData(User user) {
        try {
            System.out.println("Starting CRM data indexing for user: " + user.getEmail());

            // Get contacts as documents from HubSpot service
            List<Document> crmDocuments = hubSpotService.getContactsAsDocuments(user);

            if (crmDocuments.isEmpty()) {
                System.out.println("No CRM data found to index for user: " + user.getEmail());
                // Add sample data for testing
                addSampleCRMData(user);
                return;
            }

            // Add each CRM document to vector store
            int indexed = 0;
            for (Document doc : crmDocuments) {
                try {
                    vectorService.addDocument(doc.getFormattedContent(), doc.getMetadata(), user);
                    indexed++;
                } catch (Exception e) {
                    System.err.println("Error indexing CRM document: " + e.getMessage());
                }
            }

            System.out.println(String.format("Successfully indexed %d CRM records for user: %s", indexed, user.getEmail()));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to index CRM data for user: " + user.getEmail());
        }
    }

    /**
     * Index calendar events into vector store.
     */
    public void indexCalendarData(User user) {
        try {
            System.out.println("Starting calendar data indexing for user: " + user.getEmail());

            // Get calendar events as documents from Calendar service
            List<Document> calendarDocuments = calendarService.getCalendarAsDocuments(user);

            if (calendarDocuments.isEmpty()) {
                System.out.println("No calendar data found to index for user: " + user.getEmail());
                addSampleCalendarData(user);
                return;
            }

            // Add each calendar document to vector store
            int indexed = 0;
            for (Document doc : calendarDocuments) {
                try {
                    vectorService.addDocument(doc.getFormattedContent(), doc.getMetadata(), user);
                    indexed++;
                } catch (Exception e) {
                    System.err.println("Error indexing calendar document: " + e.getMessage());
                }
            }

            System.out.println(String.format("Successfully indexed %d calendar events for user: %s", indexed, user.getEmail()));

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to index calendar data for user: " + user.getEmail());
        }
    }

    /**
     * Perform full data indexing for a user.
     */
    public void performFullIndexing(User user) {
        System.out.println("Starting full data indexing for user: " + user.getEmail());

        try {
            // Index emails first
            indexEmails(user);

            // Then index CRM data
            indexCRMData(user);

            // Finally index calendar events
            indexCalendarData(user);

            System.out.println("Completed full data indexing for user: " + user.getEmail());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to complete full indexing for user: " + user.getEmail());
        }
    }

    /**
     * Search for specific information in indexed data.
     */
    public List<String> searchIndexedData(User user, String query, int limit) {
        return vectorService.searchSimilar(query, limit);
    }

    /**
     * Re-index data for a user (useful after data updates).
     */
    public void reindexUserData(User user) {
        try {
            System.out.println("Re-indexing data for user: " + user.getEmail());
            performFullIndexing(user);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get indexing statistics for a user.
     */
    public Map<String, Integer> getIndexingStats(User user) {
        try {
            // This would return actual counts from vector store
            // For now, returning sample stats
            return Map.of(
                    "emails", 15,
                    "contacts", 8,
                    "calendar_events", 12,
                    "total_documents", 35
            );
        } catch (Exception e) {
            return Map.of();
        }
    }

    // Sample data methods for testing

    private void addSampleEmailData(User user) {
        try {
            // Sample email 1: Baseball mention
            String emailContent1 = String.format(
                    "Email from john.doe@example.com (John Doe): Subject: Weekend Plans. " +
                            "Content: Hi %s, hope you're doing well! My son has a baseball game this Saturday. " +
                            "We might need to reschedule our Monday meeting. Let me know what works for you. Thanks!",
                    user.getName()
            );

            Map<String, Object> metadata1 = Map.of(
                    "type", "email",
                    "from", "john.doe@example.com",
                    "subject", "Weekend Plans",
                    "userId", user.getId().toString(),
                    "keywords", "baseball, son, reschedule, meeting"
            );

            vectorService.addDocument(emailContent1, metadata1, user);

            // Sample email 2: Stock discussion
            String emailContent2 = String.format(
                    "Email from greg.thompson@techcorp.com (Greg Thompson): Subject: AAPL Stock Discussion. " +
                            "Content: Hello %s, I've been thinking about our last conversation. " +
                            "I'm getting concerned about the tech sector volatility and thinking about selling my AAPL stock. " +
                            "What are your thoughts on the current market conditions? Should I hold or sell?",
                    user.getName()
            );

            Map<String, Object> metadata2 = Map.of(
                    "type", "email",
                    "from", "greg.thompson@techcorp.com",
                    "subject", "AAPL Stock Discussion",
                    "userId", user.getId().toString(),
                    "keywords", "AAPL, stock, sell, tech, volatility"
            );

            vectorService.addDocument(emailContent2, metadata2, user);

            // Sample email 3: Meeting request
            String emailContent3 = String.format(
                    "Email from sarah.williams@consulting.com (Sarah Williams): Subject: Quarterly Review Meeting. " +
                            "Content: Dear %s, I hope this email finds you well. " +
                            "I'd like to schedule our quarterly portfolio review meeting. " +
                            "Are you available next week? I have some important updates to discuss regarding our investment strategy.",
                    user.getName()
            );

            Map<String, Object> metadata3 = Map.of(
                    "type", "email",
                    "from", "sarah.williams@consulting.com",
                    "subject", "Quarterly Review Meeting",
                    "userId", user.getId().toString(),
                    "keywords", "quarterly, review, meeting, portfolio, investment"
            );

            vectorService.addDocument(emailContent3, metadata3, user);

            System.out.println("Added sample email data for testing");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addSampleCRMData(User user) {
        try {
            // Sample contact 1: Greg Thompson
            String contactContent1 = String.format(
                    "Contact: Greg Thompson, Email: greg.thompson@techcorp.com, Company: TechCorp Inc. " +
                            "Phone: (555) 123-4567. Notes: Senior software engineer interested in tech stock investments. " +
                            "Previously mentioned concerns about AAPL stock volatility. Risk tolerance: Moderate. " +
                            "Investment goal: Long-term growth with some stability. Last contact: Discussed market conditions."
            );

            Map<String, Object> metadata1 = Map.of(
                    "type", "contact",
                    "contactName", "Greg Thompson",
                    "company", "TechCorp Inc",
                    "email", "greg.thompson@techcorp.com",
                    "userId", user.getId().toString(),
                    "keywords", "tech, software, AAPL, stocks, moderate risk"
            );

            vectorService.addDocument(contactContent1, metadata1, user);

            // Sample contact 2: John Doe
            String contactContent2 = String.format(
                    "Contact: John Doe, Email: john.doe@example.com, Company: Family Business LLC. " +
                            "Phone: (555) 987-6543. Notes: Family man with young son who plays baseball. " +
                            "Looking for education savings plans and family financial planning. " +
                            "Risk tolerance: Conservative. Investment goal: Education funding and retirement planning."
            );

            Map<String, Object> metadata2 = Map.of(
                    "type", "contact",
                    "contactName", "John Doe",
                    "company", "Family Business LLC",
                    "email", "john.doe@example.com",
                    "userId", user.getId().toString(),
                    "keywords", "family, baseball, education, conservative, retirement"
            );

            vectorService.addDocument(contactContent2, metadata2, user);

            // Sample contact 3: Sarah Williams
            String contactContent3 = String.format(
                    "Contact: Sarah Williams, Email: sarah.williams@consulting.com, Company: Williams Consulting. " +
                            "Phone: (555) 456-7890. Notes: Business owner seeking portfolio diversification. " +
                            "Interested in quarterly reviews and active portfolio management. " +
                            "Risk tolerance: Aggressive. Investment goal: Business growth and wealth accumulation."
            );

            Map<String, Object> metadata3 = Map.of(
                    "type", "contact",
                    "contactName", "Sarah Williams",
                    "company", "Williams Consulting",
                    "email", "sarah.williams@consulting.com",
                    "userId", user.getId().toString(),
                    "keywords", "business owner, diversification, aggressive, growth, wealth"
            );

            vectorService.addDocument(contactContent3, metadata3, user);

            System.out.println("Added sample CRM data for testing");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addSampleCalendarData(User user) {
        try {
            // Sample calendar events for testing
            String[] sampleEvents = {
                    "Calendar Event: Weekly team meeting scheduled for Monday at 10:00 AM. Attendees: " + user.getName() + ", Sarah Williams, John Doe. Description: Regular team sync to discuss weekly goals and progress.",
                    "Calendar Event: Client consultation with Greg Thompson scheduled for Wednesday at 3:00 PM. Attendees: " + user.getName() + ", greg.thompson@techcorp.com. Description: Quarterly portfolio review and investment strategy discussion.",
                    "Calendar Event: Quarterly board meeting scheduled for Friday at 2:00 PM. Attendees: " + user.getName() + ", Board Members. Description: Quarterly financial review and strategic planning session.",
                    "Calendar Event: One-on-one with John Doe scheduled for Thursday at 1:00 PM. Attendees: " + user.getName() + ", john.doe@example.com. Description: Discuss education savings plan for his son and retirement planning options.",
                    "Calendar Event: Portfolio review with Sarah Williams scheduled for Tuesday at 11:00 AM. Attendees: " + user.getName() + ", sarah.williams@consulting.com. Description: Quarterly portfolio performance review and rebalancing discussion."
            };

            for (int i = 0; i < sampleEvents.length; i++) {
                Map<String, Object> metadata = Map.of(
                        "type", "calendar_event",
                        "eventId", "sample_event_" + i,
                        "userId", user.getId().toString(),
                        "source", "sample"
                );

                vectorService.addDocument(sampleEvents[i], metadata, user);
            }

            System.out.println("Added sample calendar data for testing");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}