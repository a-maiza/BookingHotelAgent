package com.cirta.bookinghotelagent.rag;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PolicyRetriever {

    private static final List<String> POLICY_KEYWORDS = List.of(
            "politique", "policy", "règle", "regle", "annulation", "remboursement",
            "check-in", "checkin", "check-out", "checkout", "modification", "paiement",
            "garantie", "enfant", "lit", "taxe"
    );

    private final PolicyIngestor ingestor;

    public PolicyRetriever(PolicyIngestor ingestor) {
        this.ingestor = ingestor;
    }

    public boolean isPolicyQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String normalized = normalize(message);
        return POLICY_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    public String retrieve(String question) {
        if (question == null || question.isBlank()) {
            return "Je n'ai pas compris la question liée aux politiques de l'hôtel.";
        }

        String normalizedQuestion = normalize(question);

        return ingestor.chunks().stream()
                .map(chunk -> new ScoredChunk(chunk, score(normalizedQuestion, normalize(chunk))))
                .max(Comparator.comparingInt(ScoredChunk::score))
                .filter(sc -> sc.score() > 0)
                .map(ScoredChunk::chunk)
                .map(chunk -> "Voici la politique la plus pertinente :\n\n" + chunk)
                .orElse("Je n'ai pas trouvé de règle précise dans les policies internes.");
    }

    private int score(String question, String chunk) {
        String[] terms = question.split("\\W+");
        int total = 0;
        for (String term : terms) {
            if (term.length() < 3) {
                continue;
            }
            if (chunk.contains(term)) {
                total++;
            }
        }
        return total;
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

    private record ScoredChunk(String chunk, int score) {}
}
