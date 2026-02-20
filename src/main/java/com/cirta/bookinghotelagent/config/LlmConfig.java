package com.cirta.bookinghotelagent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LlmConfig {
    @Bean
    ChatModel chatLanguageModel(
            @Value("${app.openai.api-key}") String apiKey,
            @Value("${app.openai.model}") String modelName
    ) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
