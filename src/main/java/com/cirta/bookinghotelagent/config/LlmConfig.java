package com.cirta.bookinghotelagent.config;

import com.cirta.bookinghotelagent.ai.agent.BookingAgent;
import com.cirta.bookinghotelagent.ai.tools.AvailabilityTool;
import com.cirta.bookinghotelagent.ai.tools.BookingTool;
import com.cirta.bookinghotelagent.ai.tools.EmailTool;
import com.cirta.bookinghotelagent.ai.tools.PolicyTool;
import com.cirta.bookinghotelagent.ai.tools.PricingTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
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

    @Bean
    BookingAgent bookingAgent(
            ChatModel chatModel,
            AvailabilityTool availabilityTool,
            PricingTool pricingTool,
            BookingTool bookingTool,
            EmailTool emailTool,
            PolicyTool policyTool
    ) {
        return AiServices.builder(BookingAgent.class)
                .chatModel(chatModel)
                .tools(availabilityTool, pricingTool, bookingTool, emailTool, policyTool)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(30))
                .build();
    }
}
