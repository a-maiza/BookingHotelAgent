package com.cirta.bookinghotelagent.rag;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class PolicyIngestor {

    private final List<String> chunks;

    public PolicyIngestor(ResourceLoader resourceLoader) {
        this.chunks = Collections.unmodifiableList(loadChunks(resourceLoader));
    }

    public List<String> chunks() {
        return chunks;
    }

    private List<String> loadChunks(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:policies/hotel-policies.md");
        if (!resource.exists()) {
            return List.of();
        }

        try {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return splitBySection(content);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger les policies RAG", e);
        }
    }

    private List<String> splitBySection(String markdown) {
        String[] lines = markdown.split("\\R");
        List<String> sections = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("# ")) {
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

        return sections.stream().filter(s -> !s.isBlank()).toList();
    }
}
