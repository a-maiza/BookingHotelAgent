package com.cirta.bookinghotelagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyRagConfig {

    @Bean
    EmbeddingModel embeddingModel(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.rag.embedding-model:text-embedding-3-small}") String embeddingModelName
    ) {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean
    EmbeddingStore<TextSegment> policyEmbeddingStore(
            @Value("${app.rag.pgvector.host:localhost}") String host,
            @Value("${app.rag.pgvector.port:5432}") int port,
            @Value("${app.rag.pgvector.database:bookinghotel}") String database,
            @Value("${app.rag.pgvector.user:user}") String user,
            @Value("${app.rag.pgvector.password:password}") String password,
            @Value("${app.rag.pgvector.table:policy_embeddings}") String table,
            @Value("${app.rag.embedding-dimension:1536}") int dimension
    ) {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                .build();
    }
}
