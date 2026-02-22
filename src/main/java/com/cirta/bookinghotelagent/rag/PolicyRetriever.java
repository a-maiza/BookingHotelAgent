package com.cirta.bookinghotelagent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class PolicyRetriever {

    private static final Logger log = LoggerFactory.getLogger(PolicyRetriever.class);
    private static final List<Pattern> POLICY_PATTERNS = List.of(
            Pattern.compile(".*\\b(policy|politique|regle|r[eè]gle)s?\\b.*"),
            Pattern.compile(".*\\bannul\\w*|cancel\\w*|cancellation\\b.*"),
            Pattern.compile(".*\\brembours\\w*|refund\\w*\\b.*"),
            Pattern.compile(".*\\bcheck[-\\s]?in|check[-\\s]?out|arriv\\w*|depart\\w*\\b.*"),
            Pattern.compile(".*\\bmodif\\w*|changer|change\\w*\\b.*"),
            Pattern.compile(".*\\bpaiement|payment|carte|deposit|garantie\\b.*")
    );

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final int topK;
    private final double minScore;

    public PolicyRetriever(EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           @Value("${app.rag.top-k:4}") int topK,
                           @Value("${app.rag.min-score:0.70}") double minScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.topK = topK;
        this.minScore = minScore;
    }

    public boolean isPolicyQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalize(message);
        return POLICY_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).matches());
    }

    public String retrieve(String question) {
        if (question == null || question.isBlank()) {
            return "Je n'ai pas compris la question liée aux politiques de l'hôtel.";
        }

        Embedding questionEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        List<EmbeddingMatch<TextSegment>> matches = result.matches();

        if (matches == null || matches.isEmpty()) {
            return "Je n'ai pas trouvé de règle précise dans les policies internes.";
        }

        matches.forEach(match -> log.debug("Policy match id={}, score={}", match.embeddingId(), match.score()));

        String merged = matches.stream()
                .map(match -> match.embedded().text())
                .distinct()
                .reduce((a, b) -> a + "\n\n---\n\n" + b)
                .orElse("");

        if (merged.isBlank()) {
            return "Je n'ai pas trouvé de règle précise dans les policies internes.";
        }

        return "Voici la politique la plus pertinente :\n\n" + merged;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('é', 'e')
                .replace('è', 'e')
                .replace('ê', 'e')
                .replace('à', 'a')
                .replace('ù', 'u')
                .replace('ô', 'o');
    }
}
