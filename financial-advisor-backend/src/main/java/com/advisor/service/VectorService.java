package com.advisor.service;

import com.advisor.model.User;
import com.advisor.model.VectorStore;
import com.advisor.repository.VectorStoreRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VectorService {

    private final VectorStoreRepository vectorStoreRepository;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Add a document with its embedding to the vector store.
     */
    public void addDocument(String content, Map<String, Object> metadata, User user) {
        try {
            // Generate embedding for the content
            List<Double> embedding = embeddingService.generateEmbedding(content);

            String embeddingJson = objectMapper.writeValueAsString(embedding);

            String metadataJson = objectMapper.writeValueAsString(metadata);

            VectorStore vectorStore = new VectorStore();
            vectorStore.setContent(content);
            vectorStore.setMetadata(metadataJson);
            vectorStore.setEmbedding(embeddingJson);
            vectorStore.setUser(user);

            vectorStoreRepository.save(vectorStore);

        } catch (Exception e) {
            System.err.println("Error adding document to vector store: " + e.getMessage());
            throw new RuntimeException("Failed to add document to vector store", e);
        }
    }

    /**
     * Search for similar documents using cosine similarity.
     * This implementation works without pgvector extension by calculating similarity in Java.
     */
    public List<String> searchSimilar(String query, int limit) {
        try {
            // Generate embedding for the query
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);

            // Fetch all documents (or implement pagination for large datasets)
            List<VectorStore> allDocuments = vectorStoreRepository.findAll();

            if (allDocuments.isEmpty()) {
                return new ArrayList<>();
            }

            // Calculate similarity scores for all documents
            List<DocumentSimilarity> similarities = allDocuments.stream()
                    .map(doc -> {
                        try {
                            List<Double> docEmbedding = parseEmbedding(doc.getEmbedding());
                            double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                            return new DocumentSimilarity(doc.getContent(), similarity);
                        } catch (Exception e) {
                            System.err.println("Error parsing embedding for document: " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(DocumentSimilarity::getSimilarity).reversed())
                    .limit(limit)
                    .toList();

            return similarities.stream()
                    .map(DocumentSimilarity::getContent)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error searching similar documents: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Search similar documents with pgvector extension (requires proper setup).
     * Only use this if you have pgvector extension installed and configured.
     */
    public List<String> searchSimilarWithPgVector(String query, int limit) {
        try {
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);

            // Convert embedding to PostgreSQL array format
            String embeddingArray = convertToPostgresArray(queryEmbedding);

            // Use pgvector's cosine distance operator
            // Note: This requires the vector column to be of type 'vector' not 'text'
            String sql = """
                SELECT content, (embedding <=> ?::vector) as distance
                FROM vector_store
                ORDER BY distance
                LIMIT ?
                """;

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> rs.getString("content"),
                    embeddingArray, limit);

        } catch (Exception e) {
            System.err.println("Error with pgvector search: " + e.getMessage());
            // Fallback to Java-based similarity search
            return searchSimilar(query, limit);
        }
    }

    /**
     * Search for documents by user.
     */
    public List<String> searchSimilarByUser(User user, String query, int limit) {
        try {
            List<Double> queryEmbedding = embeddingService.generateEmbedding(query);

            // Fetch user's documents only
            List<VectorStore> userDocuments = vectorStoreRepository.findByUser(user);

            if (userDocuments.isEmpty()) {
                return new ArrayList<>();
            }

            // Calculate similarity scores
            List<DocumentSimilarity> similarities = userDocuments.stream()
                    .map(doc -> {
                        try {
                            List<Double> docEmbedding = parseEmbedding(doc.getEmbedding());
                            double similarity = cosineSimilarity(queryEmbedding, docEmbedding);
                            return new DocumentSimilarity(doc.getContent(), similarity);
                        } catch (Exception e) {
                            System.err.println("Error parsing embedding: " + e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingDouble(DocumentSimilarity::getSimilarity).reversed())
                    .limit(limit)
                    .toList();

            return similarities.stream()
                    .map(DocumentSimilarity::getContent)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            System.err.println("Error searching user documents: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse embedding from JSON string.
     */
    private List<Double> parseEmbedding(String embeddingJson) throws JsonProcessingException {
        return objectMapper.readValue(embeddingJson,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
    }

    /**
     * Calculate cosine similarity between two vectors.
     */
    private double cosineSimilarity(List<Double> vector1, List<Double> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("Vectors must have the same dimensions");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * Convert embedding list to PostgreSQL array format.
     */
    private String convertToPostgresArray(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Get document count for a user.
     */
    public long getDocumentCount(User user) {
        return vectorStoreRepository.findByUser(user).size();
    }

    /**
     * Delete all documents for a user.
     */
    public void deleteAllDocuments(User user) {
        List<VectorStore> userDocuments = vectorStoreRepository.findByUser(user);
        vectorStoreRepository.deleteAll(userDocuments);
    }


    @Getter
    private static class DocumentSimilarity {
        private final String content;
        private final double similarity;

        public DocumentSimilarity(String content, double similarity) {
            this.content = content;
            this.similarity = similarity;
        }

    }
}