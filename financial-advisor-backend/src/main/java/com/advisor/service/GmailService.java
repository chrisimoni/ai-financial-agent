package com.advisor.service;

import com.advisor.model.User;
import com.advisor.model.Email;
import com.advisor.repository.EmailRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class GmailService {
    private final EmailRepository emailRepository;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private static final String APPLICATION_NAME = "Financial Advisor Agent";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    // Configuration for content chunking
    private static final int MAX_CONTENT_LENGTH = 6000; // Conservative limit in characters
    private static final int CHUNK_SIZE = 4000; // Size of each chunk
    private static final int CHUNK_OVERLAP = 200; // Overlap between chunks

    /**
     * Create Gmail service with user's OAuth credentials.
     */
    private Gmail getGmailService(User user) {
        try {
            Credential credential = new GoogleCredential.Builder()
                    .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                    .setJsonFactory(JSON_FACTORY)
                    .setClientSecrets(clientId, clientSecret)
                    .build()
                    .setAccessToken(user.getGoogleAccessToken())
                    .setRefreshToken(user.getGoogleRefreshToken());

            return new Gmail.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JSON_FACTORY,
                    credential
            )
                    .setApplicationName(APPLICATION_NAME)
                    .build();

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to create Gmail service", e);
        }
    }

    /**
     * Get user's emails and convert to documents for RAG indexing with content chunking.
     */
    public List<Document> getEmailsAsDocuments(User user) {
        try {
            Gmail service = getGmailService(user);
            List<Document> documents = new ArrayList<>();

            // Get list of messages (last 100)
            ListMessagesResponse listResponse = service.users().messages()
                    .list(user.getEmail())
                    .setMaxResults(100L)
                    .execute();

            List<Message> messages = listResponse.getMessages();
            if (messages == null || messages.isEmpty()) {
                return documents;
            }

            // Process each message
            for (Message message : messages) {
                try {
                    Message fullMessage = service.users().messages()
                            .get(user.getEmail(), message.getId())
                            .execute();

                    Email emailEntity = processGmailMessage(user, fullMessage);
                    if (emailEntity != null) {
                        // Create chunked documents for RAG
                        List<Document> emailDocuments = createEmailDocuments(emailEntity);
                        documents.addAll(emailDocuments);
                    }
                } catch (Exception e) {
                    System.err.println("Error processing message " + message.getId() + ": " + e.getMessage());
                }
            }

            return documents;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Create document chunks from an email entity to stay within token limits.
     */
    private List<Document> createEmailDocuments(Email emailEntity) {
        List<Document> documents = new ArrayList<>();

        // Create email header information
        String emailHeader = String.format(
                "Email from %s (%s): Subject: %s",
                emailEntity.getFromName() != null ? emailEntity.getFromName() : emailEntity.getFromEmail(),
                emailEntity.getFromEmail(),
                emailEntity.getSubject()
        );

        // Base metadata for all chunks
        Map<String, Object> baseMetadata = Map.of(
                "type", "email",
                "from", emailEntity.getFromEmail(),
                "subject", emailEntity.getSubject(),
                "date", emailEntity.getReceivedAt().toString(),
                "userId", emailEntity.getUser().getId().toString(),
                "gmailId", emailEntity.getGmailId()
        );

        String emailBody = emailEntity.getBody() != null ? emailEntity.getBody() : "";

        // If the email is short enough, create a single document
        String fullContent = emailHeader + ". Content: " + emailBody;
        if (fullContent.length() <= MAX_CONTENT_LENGTH) {
            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("chunkIndex", 0);
            metadata.put("totalChunks", 1);
            metadata.put("isComplete", true);

            documents.add(new Document(fullContent, metadata));
            return documents;
        }

        // For long emails, create multiple chunks
        if (emailBody.length() > CHUNK_SIZE) {
            List<String> bodyChunks = chunkText(emailBody, CHUNK_SIZE, CHUNK_OVERLAP);

            for (int i = 0; i < bodyChunks.size(); i++) {
                String chunkContent = emailHeader + ". Content (Part " + (i + 1) + "/" + bodyChunks.size() + "): " + bodyChunks.get(i);

                Map<String, Object> metadata = new HashMap<>(baseMetadata);
                metadata.put("chunkIndex", i);
                metadata.put("totalChunks", bodyChunks.size());
                metadata.put("isComplete", false);

                documents.add(new Document(chunkContent, metadata));
            }
        } else {
            // Email body is manageable, but header + body is too long
            // Truncate the body to fit
            int availableSpace = MAX_CONTENT_LENGTH - emailHeader.length() - 12; // 12 for ". Content: "
            String truncatedBody = emailBody.length() > availableSpace ?
                    emailBody.substring(0, availableSpace) + "..." : emailBody;

            String content = emailHeader + ". Content: " + truncatedBody;

            Map<String, Object> metadata = new HashMap<>(baseMetadata);
            metadata.put("chunkIndex", 0);
            metadata.put("totalChunks", 1);
            metadata.put("isComplete", emailBody.length() <= availableSpace);
            metadata.put("truncated", emailBody.length() > availableSpace);

            documents.add(new Document(content, metadata));
        }

        return documents;
    }

    /**
     * Split text into overlapping chunks.
     */
    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // Try to break at word boundaries
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + chunkSize / 2) {
                    end = lastSpace;
                }
            }

            chunks.add(text.substring(start, end));

            if (end >= text.length()) {
                break;
            }

            start = end - overlap;
        }

        return chunks;
    }

    /**
     * Send email using Gmail API.
     */
    public String sendEmail(User user, String to, String subject, String body) {
        try {
            Gmail service = getGmailService(user);

            // Create the email content
            Properties props = new Properties();
            Session session = Session.getDefaultInstance(props, null);

            MimeMessage email = new MimeMessage(session);
            email.setFrom(user.getEmail());
            email.addRecipient(javax.mail.Message.RecipientType.TO, new javax.mail.internet.InternetAddress(to));
            email.setSubject(subject);
            email.setText(body);

            // Encode the email
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            email.writeTo(buffer);
            byte[] bytes = buffer.toByteArray();
            String encodedEmail = Base64.getUrlEncoder().encodeToString(bytes);

            // Create Gmail message
            Message message = new Message();
            message.setRaw(encodedEmail);

            // Send the message
            message = service.users().messages().send(user.getEmail(), message).execute();

            // Store sent email in database
            Email sentEmail = new Email(user, message.getId(), user.getEmail(), subject);
            sentEmail.setToEmail(to);
            sentEmail.setBody(body);
            sentEmail.setSentAt(LocalDateTime.now());
            emailRepository.save(sentEmail);

            return String.format("Email sent successfully to %s with subject '%s'. Message ID: %s",
                    to, subject, message.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return "Failed to send email: " + e.getMessage();
        }
    }

    /**
     * Process Gmail message and save to database.
     */
    private Email processGmailMessage(User user, Message message) {
        try {
            // Check if email already exists
            Optional<Email> existingEmail = emailRepository.findByGmailId(message.getId());
            if (existingEmail.isPresent()) {
                return existingEmail.get();
            }

            MessagePart payload = message.getPayload();
            Map<String, String> headers = new HashMap<>();

            // Extract headers
            if (payload.getHeaders() != null) {
                for (MessagePartHeader header : payload.getHeaders()) {
                    headers.put(header.getName().toLowerCase(), header.getValue());
                }
            }

            // Extract email content
            String subject = headers.getOrDefault("subject", "");
            String from = headers.getOrDefault("from", "");
            String to = headers.getOrDefault("to", "");
            String body = extractEmailBody(payload);

            // Parse from field for name and email
            String fromEmail = extractEmailFromHeader(from);
            String fromName = extractNameFromHeader(from);

            // Create email entity
            Email email = new Email(user, message.getId(), fromEmail, subject);
            email.setFromName(fromName);
            email.setToEmail(to);
            email.setBody(body);
            email.setReceivedAt(LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(message.getInternalDate()),
                    ZoneId.systemDefault()
            ));
            email.setRead(message.getLabelIds() == null || !message.getLabelIds().contains("UNREAD"));

            return emailRepository.save(email);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extract email body from message payload.
     */
    private String extractEmailBody(MessagePart payload) {
        String body = "";

        if (payload.getBody() != null && payload.getBody().getData() != null) {
            body = new String(Base64.getUrlDecoder().decode(payload.getBody().getData()));
        } else if (payload.getParts() != null) {
            for (MessagePart part : payload.getParts()) {
                if ("text/plain".equals(part.getMimeType()) && part.getBody() != null && part.getBody().getData() != null) {
                    body = new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                    break;
                } else if (part.getParts() != null) {
                    // Recursive search for text content
                    body = extractEmailBody(part);
                    if (!body.isEmpty()) break;
                }
            }
        }

        return body;
    }

    /**
     * Extract email address from header field.
     */
    private String extractEmailFromHeader(String header) {
        if (header.contains("<") && header.contains(">")) {
            int start = header.indexOf("<") + 1;
            int end = header.indexOf(">");
            return header.substring(start, end);
        }
        return header.trim();
    }

    /**
     * Extract name from header field.
     */
    private String extractNameFromHeader(String header) {
        if (header.contains("<")) {
            return header.substring(0, header.indexOf("<")).trim().replaceAll("\"", "");
        }
        return null;
    }

    /**
     * Search emails for specific content.
     */
    public List<String> searchEmails(User user, String query) {
        try {
            Gmail service = getGmailService(user);

            // Search using Gmail API query syntax
            ListMessagesResponse response = service.users().messages()
                    .list(user.getEmail())
                    .setQ(query)
                    .setMaxResults(50L)
                    .execute();

            if (response.getMessages() == null) {
                return new ArrayList<>();
            }

            return response.getMessages().stream()
                    .map(message -> {
                        try {
                            Message fullMessage = service.users().messages()
                                    .get(user.getEmail(), message.getId())
                                    .execute();
                            return extractEmailSummary(fullMessage);
                        } catch (Exception e) {
                            return "Error retrieving message: " + e.getMessage();
                        }
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of("Error searching emails: " + e.getMessage());
        }
    }

    /**
     * Extract email summary for search results.
     */
    private String extractEmailSummary(Message message) {
        try {
            Map<String, String> headers = new HashMap<>();
            if (message.getPayload().getHeaders() != null) {
                for (MessagePartHeader header : message.getPayload().getHeaders()) {
                    headers.put(header.getName().toLowerCase(), header.getValue());
                }
            }

            String from = headers.getOrDefault("from", "Unknown");
            String subject = headers.getOrDefault("subject", "No Subject");
            String snippet = message.getSnippet();

            return String.format("From: %s | Subject: %s | Preview: %s", from, subject, snippet);

        } catch (Exception e) {
            return "Error processing message";
        }
    }

    /**
     * Get recent emails for a user.
     */
    public List<Email> getRecentEmails(User user, int limit) {
        return emailRepository.findRecentEmails(user, LocalDateTime.now().minusDays(30))
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Sync new emails from Gmail.
     */
    public void syncEmails(User user) {
        try {
            Gmail service = getGmailService(user);

            // Get latest emails
            ListMessagesResponse response = service.users().messages()
                    .list(user.getEmail())
                    .setMaxResults(20L)
                    .execute();

            if (response.getMessages() != null) {
                for (Message message : response.getMessages()) {
                    try {
                        Message fullMessage = service.users().messages()
                                .get(user.getEmail(), message.getId())
                                .execute();
                        processGmailMessage(user, fullMessage);
                    } catch (Exception e) {
                        System.err.println("Error syncing message " + message.getId());
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}