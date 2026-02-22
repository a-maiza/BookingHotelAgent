package com.cirta.bookinghotelagent.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class PolicyIngestor implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestor.class);

    private final ResourceLoader resourceLoader;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;
    private final String namespace;
    private final int chunkSize;
    private final int chunkOverlap;

    public PolicyIngestor(ResourceLoader resourceLoader,
                          EmbeddingModel embeddingModel,
                          EmbeddingStore<TextSegment> embeddingStore,
                          JdbcTemplate jdbcTemplate,
                          org.springframework.core.env.Environment env) {
        this.resourceLoader = resourceLoader;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.jdbcTemplate = jdbcTemplate;
        this.namespace = env.getProperty("app.rag.namespace", "policies");
        this.chunkSize = Integer.parseInt(env.getProperty("app.rag.chunk-size", "700"));
        this.chunkOverlap = Integer.parseInt(env.getProperty("app.rag.chunk-overlap", "100"));
    }

    @Override
    public void run(ApplicationArguments args) {
        ingestIfNeeded();
    }

    public void ingestIfNeeded() {
        String markdown = loadMarkdown();
        if (markdown.isBlank()) {
            log.warn("Aucune policy trouvée pour ingestion vectorielle");
            return;
        }

        String hash = sha256(markdown);
        ensureIngestionTable();

        String existingHash = jdbcTemplate.query(
                "select content_hash from rag_ingestion_state where namespace = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                namespace
        );

        if (hash.equals(existingHash)) {
            log.info("Ingestion policies ignorée: contenu inchangé pour namespace={}", namespace);
            return;
        }

        if (existingHash != null) {
            jdbcTemplate.update("delete from policy_embeddings");
        }

        List<TextSegment> segments = splitMarkdown(markdown).stream()
                .map(TextSegment::from)
                .toList();

        if (segments.isEmpty()) {
            log.warn("Ingestion policies ignorée: aucun segment généré");
            return;
        }

        var embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        jdbcTemplate.update(
                "insert into rag_ingestion_state(namespace, content_hash, updated_at) values (?, ?, now()) " +
                        "on conflict (namespace) do update set content_hash = excluded.content_hash, updated_at = now()",
                namespace,
                hash
        );

        log.info("Ingestion vectorielle terminée: {} segments ingérés pour namespace={}", segments.size(), namespace);
    }

    private void ensureIngestionTable() {
        jdbcTemplate.execute("""
                create table if not exists rag_ingestion_state (
                    namespace text primary key,
                    content_hash text not null,
                    updated_at timestamptz not null
                )
                """);
    }

    private String loadMarkdown() {
        Resource resource = resourceLoader.getResource("classpath:policies/hotel-policies.md");
        if (!resource.exists()) {
            return "";
        }
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger les policies RAG", e);
        }
    }

    private List<String> splitMarkdown(String markdown) {
        List<String> sections = splitByHeaders(markdown);
        if (sections.size() > 1) {
            return sections.stream().filter(s -> !s.isBlank()).toList();
        }
        return splitBySize(markdown);
    }

    private List<String> splitByHeaders(String markdown) {
        String[] lines = markdown.split("\\R");
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (line.matches("^#{1,3}\\s+.*")) {
                if (!current.isEmpty()) {
                    sections.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
            current.append(line).append('\n');
        }

        if (!current.isEmpty()) {
            sections.add(current.toString().trim());
        }

        return sections;
    }

    private List<String> splitBySize(String text) {
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSize - chunkOverlap);
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(text.length(), start + chunkSize);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end == text.length()) {
                break;
            }
        }
        return chunks;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer le hash des policies", e);
        }
    }
}
