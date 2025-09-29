package com.advisor.service;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final OpenAiService openAiService;

    @Value("${openai.embedding.model:text-embedding-ada-002}")
    private String embeddingModel;

    // Conservative token limits
    private static final int MAX_TOKENS_TEXT_EMBEDDING_ADA_002 = 8000; // Leave some buffer
    private static final int MAX_TOKENS_TEXT_EMBEDDING_3_SMALL = 8000;
    private static final int MAX_TOKENS_TEXT_EMBEDDING_3_LARGE = 8000;

    // Rough token estimation (1 token ≈ 4 characters for English text)
    private static final double CHARS_PER_TOKEN = 4.0;

    /**
     * Generate embedding for text with token validation.
     */
    public List<Double> generateEmbedding(String text) {
        try {
            // Validate and potentially truncate text
            String processedText = validateAndProcessText(text);

            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(embeddingModel)
                    .input(List.of(processedText))
                    .build();

            return openAiService.createEmbeddings(request)
                    .getData()
                    .get(0)
                    .getEmbedding();

        } catch (OpenAiHttpException e) {
            if (e.getMessage().contains("maximum context length")) {
                // If we still hit the limit, try with more aggressive truncation
                System.err.println("Hit token limit even after processing. Applying emergency truncation.");
                String emergencyText = truncateToTokenLimit(text, getMaxTokensForModel() / 2);
                return generateEmbeddingWithFallback(emergencyText);
            } else {
                throw e;
            }
        } catch (Exception e) {
            System.err.println("Error generating embedding: " + e.getMessage());
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Validate text length and process if necessary.
     */
    private String validateAndProcessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return ""; // Return empty string for null/empty input
        }

        int estimatedTokens = estimateTokenCount(text);
        int maxTokens = getMaxTokensForModel();

        if (estimatedTokens <= maxTokens) {
            return text;
        }

        System.out.println(String.format(
                "Text too long for embedding model. Estimated tokens: %d, Max: %d. Truncating...",
                estimatedTokens, maxTokens
        ));

        return truncateToTokenLimit(text, maxTokens);
    }

    /**
     * Estimate token count for text.
     */
    private int estimateTokenCount(String text) {
        // More sophisticated token estimation
        // Remove extra whitespace and count
        String cleanText = text.replaceAll("\\s+", " ").trim();

        // Rough estimation: 1 token ≈ 4 characters for English
        // Add some buffer for special tokens
        return (int) Math.ceil(cleanText.length() / CHARS_PER_TOKEN) + 10;
    }

    /**
     * Get maximum tokens for the current embedding model.
     */
    private int getMaxTokensForModel() {
        return switch (embeddingModel.toLowerCase()) {
            case "text-embedding-ada-002" -> MAX_TOKENS_TEXT_EMBEDDING_ADA_002;
            case "text-embedding-3-small" -> MAX_TOKENS_TEXT_EMBEDDING_3_SMALL;
            case "text-embedding-3-large" -> MAX_TOKENS_TEXT_EMBEDDING_3_LARGE;
            default -> MAX_TOKENS_TEXT_EMBEDDING_ADA_002; // Default fallback
        };
    }

    /**
     * Truncate text to stay within token limit.
     */
    private String truncateToTokenLimit(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Calculate safe character limit (with buffer)
        int safeCharLimit = (int) (maxTokens * CHARS_PER_TOKEN * 0.8); // 20% buffer

        if (text.length() <= safeCharLimit) {
            return text;
        }

        // Truncate at word boundary if possible
        String truncated = text.substring(0, safeCharLimit);
        int lastSpace = truncated.lastIndexOf(' ');

        if (lastSpace > safeCharLimit * 0.8) { // If we can find a reasonable word boundary
            truncated = truncated.substring(0, lastSpace);
        }

        return truncated + "...";
    }

    /**
     * Fallback method for emergency cases.
     */
    private List<Double> generateEmbeddingWithFallback(String text) {
        try {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .model(embeddingModel)
                    .input(List.of(text))
                    .build();

            return openAiService.createEmbeddings(request)
                    .getData()
                    .get(0)
                    .getEmbedding();

        } catch (Exception e) {
            System.err.println("Emergency embedding generation failed: " + e.getMessage());
            throw new RuntimeException("Failed to generate embedding even with truncation", e);
        }
    }

    /**
     * Batch process multiple texts with token validation.
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        return texts.stream()
                .map(this::generateEmbedding)
                .toList();
    }

    /**
     * Check if text is within token limits without processing.
     */
    public boolean isWithinTokenLimit(String text) {
        return estimateTokenCount(text) <= getMaxTokensForModel();
    }

    /**
     * Get recommended chunk size for the current model.
     */
    public int getRecommendedChunkSize() {
        int maxTokens = getMaxTokensForModel();
        // Return 80% of max tokens converted to characters for safety
        return (int) (maxTokens * 0.8 * CHARS_PER_TOKEN);
    }
}