package com.advisor.service;

import com.advisor.model.Contact;
import com.advisor.model.User;
import com.advisor.repository.ContactRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class HubSpotService {

    private final ContactRepository contactRepository;

    @Value("${hubspot.client-id}")
    private String hubspotClientId;

    @Value("${hubspot.client-secret}")
    private String hubspotClientSecret;

    @Value("${hubspot.redirect-uri:http://localhost:3000/auth/hubspot/callback}")
    private String hubspotRedirectUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String HUBSPOT_API_BASE = "https://api.hubapi.com";

    /**
     * Create HTTP headers with HubSpot access token.
     */
    private HttpHeaders createHeaders(User user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(user.getHubspotAccessToken());
        return headers;
    }

    /**
     * Create contact in HubSpot CRM.
     */
    public String createContact(User user, String name, String email, String company, String notes) {
        try {
            if (user.getHubspotAccessToken() == null) {
                return createLocalContact(user, name, email, company, notes);
            }

            // Prepare contact data for HubSpot API
            Map<String, Object> properties = new HashMap<>();

            // Split name into first and last name
            String[] nameParts = name.split(" ", 2);
            properties.put("firstname", nameParts[0]);
            if (nameParts.length > 1) {
                properties.put("lastname", nameParts[1]);
            }

            properties.put("email", email);
            if (company != null && !company.isEmpty()) {
                properties.put("company", company);
            }
            if (notes != null && !notes.isEmpty()) {
                properties.put("notes_last_contacted", notes);
            }

            Map<String, Object> requestBody = Map.of("properties", properties);

            HttpHeaders headers = createHeaders(user);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call HubSpot API
            ResponseEntity<String> response = restTemplate.postForEntity(
                    HUBSPOT_API_BASE + "/crm/v3/objects/contacts",
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String hubspotId = responseJson.get("id").asText();

                // Store contact locally as well
                Contact contact = new Contact(user, name, email);
                contact.setHubspotId(hubspotId);
                contact.setCompany(company);
                contact.setNotes(notes);
                contact.setLastSyncAt(LocalDateTime.now());
                contactRepository.save(contact);

                return String.format("Contact '%s' created successfully in HubSpot with ID: %s", name, hubspotId);
            } else {
                return "Failed to create contact in HubSpot: " + response.getStatusCode();
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to local storage
            return createLocalContact(user, name, email, company, notes);
        }
    }

    /**
     * Create contact locally when HubSpot is not available.
     */
    private String createLocalContact(User user, String name, String email, String company, String notes) {
        try {
            Contact contact = new Contact(user, name, email);
            contact.setCompany(company);
            contact.setNotes(notes);
            contactRepository.save(contact);

            return String.format("Contact '%s' created successfully (stored locally - HubSpot integration pending)", name);
        } catch (Exception e) {
            return "Failed to create contact: " + e.getMessage();
        }
    }

    /**
     * Update contact in HubSpot CRM.
     */
    public String updateContact(User user, String contactId, Map<String, String> updates) {
        try {
            if (user.getHubspotAccessToken() == null) {
                return updateLocalContact(contactId, updates);
            }

            // Prepare update data
            Map<String, Object> properties = new HashMap<>(updates);
            Map<String, Object> requestBody = Map.of("properties", properties);

            HttpHeaders headers = createHeaders(user);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call HubSpot API
            restTemplate.exchange(
                    HUBSPOT_API_BASE + "/crm/v3/objects/contacts/" + contactId,
                    HttpMethod.PATCH,
                    entity,
                    String.class
            );

            // Update local contact as well
            updateLocalContactByHubSpotId(contactId, updates);

            return String.format("Contact %s updated successfully in HubSpot", contactId);

        } catch (Exception e) {
            e.printStackTrace();
            return updateLocalContact(contactId, updates);
        }
    }

    /**
     * Update local contact when HubSpot is not available.
     */
    private String updateLocalContact(String contactId, Map<String, String> updates) {
        try {
            Optional<Contact> contactOpt = contactRepository.findById(Long.parseLong(contactId));
            if (contactOpt.isPresent()) {
                Contact contact = contactOpt.get();

                // Update available fields
                if (updates.containsKey("name")) {
                    contact.setName(updates.get("name"));
                }
                if (updates.containsKey("email")) {
                    contact.setEmail(updates.get("email"));
                }
                if (updates.containsKey("company")) {
                    contact.setCompany(updates.get("company"));
                }
                if (updates.containsKey("notes")) {
                    contact.setNotes(updates.get("notes"));
                }

                contactRepository.save(contact);
                return "Contact updated successfully (locally)";
            } else {
                return "Contact not found";
            }
        } catch (Exception e) {
            return "Failed to update contact: " + e.getMessage();
        }
    }

    /**
     * Update local contact by HubSpot ID.
     */
    private void updateLocalContactByHubSpotId(String hubspotId, Map<String, String> updates) {
        try {
            Optional<Contact> contactOpt = contactRepository.findByHubspotId(hubspotId);
            if (contactOpt.isPresent()) {
                Contact contact = contactOpt.get();

                if (updates.containsKey("notes")) {
                    contact.setNotes(updates.get("notes"));
                }
                if (updates.containsKey("company")) {
                    contact.setCompany(updates.get("company"));
                }

                contact.setLastSyncAt(LocalDateTime.now());
                contactRepository.save(contact);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get contacts as documents for RAG indexing.
     */
    public List<Document> getContactsAsDocuments(User user) {
        try {
            List<Document> documents = new ArrayList<>();

            if (user.getHubspotAccessToken() != null) {
                // Fetch from HubSpot API
                documents.addAll(fetchHubSpotContacts(user));
            }

            // Also include local contacts
            documents.addAll(fetchLocalContacts(user));

            // If no contacts found, add sample data
            if (documents.isEmpty()) {
                documents.addAll(createSampleContactDocuments(user));
            }

            return documents;

        } catch (Exception e) {
            e.printStackTrace();
            return createSampleContactDocuments(user);
        }
    }

    /**
     * Fetch contacts from HubSpot API.
     */
    private List<Document> fetchHubSpotContacts(User user) {
        List<Document> documents = new ArrayList<>();

        try {
            HttpHeaders headers = createHeaders(user);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Fetch contacts from HubSpot
            ResponseEntity<String> response = restTemplate.exchange(
                    HUBSPOT_API_BASE + "/crm/v3/objects/contacts?properties=firstname,lastname,email,company,notes_last_contacted&limit=100",
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                JsonNode results = responseJson.get("results");

                for (JsonNode contact : results) {
                    JsonNode properties = contact.get("properties");

                    String firstName = getPropertyValue(properties, "firstname");
                    String lastName = getPropertyValue(properties, "lastname");
                    String email = getPropertyValue(properties, "email");
                    String company = getPropertyValue(properties, "company");
                    String notes = getPropertyValue(properties, "notes_last_contacted");

                    String fullName = (firstName + " " + lastName).trim();
                    String contactId = contact.get("id").asText();

                    // Create document content
                    String content = String.format(
                            "Contact: %s, Email: %s, Company: %s. Notes: %s",
                            fullName, email, company != null ? company : "Not specified",
                            notes != null ? notes : "No notes available"
                    );

                    Map<String, Object> metadata = Map.of(
                            "type", "contact",
                            "contactId", contactId,
                            "contactName", fullName,
                            "email", email,
                            "company", company != null ? company : "",
                            "userId", user.getId().toString(),
                            "source", "hubspot"
                    );

                    documents.add(new Document(content, metadata));

                    // Also store/update local contact
                    syncLocalContact(user, contactId, fullName, email, company, notes);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return documents;
    }

    /**
     * Get property value from HubSpot contact properties.
     */
    private String getPropertyValue(JsonNode properties, String propertyName) {
        JsonNode property = properties.get(propertyName);
        return property != null && !property.isNull() ? property.asText() : null;
    }

    /**
     * Sync contact with local database.
     */
    private void syncLocalContact(User user, String hubspotId, String name, String email, String company, String notes) {
        try {
            Optional<Contact> existingContact = contactRepository.findByHubspotId(hubspotId);
            Contact contact;

            if (existingContact.isPresent()) {
                contact = existingContact.get();
            } else {
                contact = new Contact(user, name, email);
                contact.setHubspotId(hubspotId);
            }

            contact.setName(name);
            contact.setEmail(email);
            contact.setCompany(company);
            contact.setNotes(notes);
            contact.setLastSyncAt(LocalDateTime.now());

            contactRepository.save(contact);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetch local contacts and convert to documents.
     */
    private List<Document> fetchLocalContacts(User user) {
        List<Document> documents = new ArrayList<>();

        try {
            List<Contact> contacts = contactRepository.findByUserOrderByNameAsc(user);

            for (Contact contact : contacts) {
                String content = String.format(
                        "Contact: %s, Email: %s, Company: %s. Phone: %s. Notes: %s",
                        contact.getName(),
                        contact.getEmail(),
                        contact.getCompany() != null ? contact.getCompany() : "Not specified",
                        contact.getPhone() != null ? contact.getPhone() : "Not provided",
                        contact.getNotes() != null ? contact.getNotes() : "No notes available"
                );

                Map<String, Object> metadata = Map.of(
                        "type", "contact",
                        "contactId", contact.getId().toString(),
                        "contactName", contact.getName(),
                        "email", contact.getEmail(),
                        "company", contact.getCompany() != null ? contact.getCompany() : "",
                        "userId", user.getId().toString(),
                        "source", "local"
                );

                documents.add(new Document(content, metadata));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return documents;
    }

    /**
     * Create sample contact documents for testing.
     */
    private List<Document> createSampleContactDocuments(User user) {
        List<Document> documents = new ArrayList<>();

        // Sample contact data
        String[][] sampleContacts = {
                {"Greg Thompson", "greg.thompson@techcorp.com", "TechCorp Inc", "Interested in selling AAPL stock. Mentioned concerns about tech sector volatility."},
                {"John Doe", "john.doe@example.com", "Family Business LLC", "Family man with son who plays baseball. Looking for education savings plans."},
                {"Sarah Williams", "sarah.williams@consulting.com", "Williams Consulting", "Business owner seeking portfolio diversification and quarterly reviews."},
                {"Michael Johnson", "mjohnson@startup.io", "Johnson Startup", "Young entrepreneur interested in aggressive growth investments."},
                {"Lisa Chen", "lchen@realestate.com", "Chen Realty", "Real estate investor looking for alternative investment strategies."}
        };

        for (String[] contactData : sampleContacts) {
            String name = contactData[0];
            String email = contactData[1];
            String company = contactData[2];
            String notes = contactData[3];

            String content = String.format(
                    "Contact: %s, Email: %s, Company: %s. Notes: %s",
                    name, email, company, notes
            );

            Map<String, Object> metadata = Map.of(
                    "type", "contact",
                    "contactName", name,
                    "email", email,
                    "company", company,
                    "userId", user.getId().toString(),
                    "source", "sample"
            );

            documents.add(new Document(content, metadata));
        }

        return documents;
    }

    /**
     * Search contacts in HubSpot and locally.
     */
    public List<Map<String, Object>> searchContacts(User user, String query) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            // Search local contacts first
            List<Contact> localContacts = contactRepository.searchContacts(user, query);

            for (Contact contact : localContacts) {
                Map<String, Object> contactMap = new HashMap<>();
                contactMap.put("id", contact.getId().toString());
                contactMap.put("hubspotId", contact.getHubspotId());
                contactMap.put("name", contact.getName());
                contactMap.put("email", contact.getEmail());
                contactMap.put("company", contact.getCompany());
                contactMap.put("notes", contact.getNotes());
                contactMap.put("source", "local");
                results.add(contactMap);
            }

            // If HubSpot is connected, search there too
            if (user.getHubspotAccessToken() != null) {
                results.addAll(searchHubSpotContacts(user, query));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Search contacts in HubSpot.
     */
    private List<Map<String, Object>> searchHubSpotContacts(User user, String query) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            HttpHeaders headers = createHeaders(user);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // HubSpot search API
            String searchUrl = String.format(
                    "%s/crm/v3/objects/contacts/search",
                    HUBSPOT_API_BASE
            );

            Map<String, Object> searchRequest = Map.of(
                    "query", query,
                    "properties", List.of("firstname", "lastname", "email", "company", "notes_last_contacted"),
                    "limit", 10
            );

            ResponseEntity<String> response = restTemplate.postForEntity(
                    searchUrl,
                    new HttpEntity<>(searchRequest, headers),
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                JsonNode searchResults = responseJson.get("results");

                for (JsonNode contact : searchResults) {
                    JsonNode properties = contact.get("properties");

                    Map<String, Object> contactMap = new HashMap<>();
                    contactMap.put("id", contact.get("id").asText());
                    contactMap.put("hubspotId", contact.get("id").asText());
                    contactMap.put("name", getPropertyValue(properties, "firstname") + " " + getPropertyValue(properties, "lastname"));
                    contactMap.put("email", getPropertyValue(properties, "email"));
                    contactMap.put("company", getPropertyValue(properties, "company"));
                    contactMap.put("notes", getPropertyValue(properties, "notes_last_contacted"));
                    contactMap.put("source", "hubspot");

                    results.add(contactMap);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Sync all contacts from HubSpot.
     */
    public void syncAllContacts(User user) {
        try {
            if (user.getHubspotAccessToken() == null) {
                System.out.println("HubSpot not connected for user: " + user.getEmail());
                return;
            }

            System.out.println("Syncing contacts from HubSpot for user: " + user.getEmail());

            // Fetch and sync contacts
            fetchHubSpotContacts(user);

            System.out.println("Contact sync completed for user: " + user.getEmail());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get contact statistics.
     */
    public Map<String, Integer> getContactStats(User user) {
        try {
            long totalContacts = contactRepository.countByUser(user);
            long hubspotContacts = contactRepository.findByUserOrderByNameAsc(user)
                    .stream()
                    .mapToLong(c -> c.getHubspotId() != null ? 1 : 0)
                    .sum();

            return Map.of(
                    "total", (int) totalContacts,
                    "hubspot_synced", (int) hubspotContacts,
                    "local_only", (int) (totalContacts - hubspotContacts)
            );

        } catch (Exception e) {
            return Map.of("total", 0, "hubspot_synced", 0, "local_only", 0);
        }
    }

    public String getConnectionUrl() {
        String scopes = "crm.schemas.contacts.write crm.objects.contacts.write crm.schemas.contacts.read crm.schemas.appointments.write crm.objects.contacts.read";

        return "https://app.hubspot.com/oauth/authorize" +
                "?client_id=" + hubspotClientId +
                "&scope=" + java.net.URLEncoder.encode(scopes, java.nio.charset.StandardCharsets.UTF_8) +
                "&redirect_uri=" + java.net.URLEncoder.encode(hubspotRedirectUri, java.nio.charset.StandardCharsets.UTF_8);
    }

    public String exchangeCodeForToken(String code) {
        try {
            String tokenUrl = "https://api.hubapi.com/oauth/v1/token";

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", hubspotClientId);
            params.add("client_secret", hubspotClientSecret);
            params.add("redirect_uri", hubspotRedirectUri);
            params.add("code", code);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                return responseJson.get("access_token").asText();
            } else {
                throw new RuntimeException("Token exchange failed with status: " + response.getStatusCode());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error exchanging code for token: " + e.getMessage(), e);
        }
    }
}
