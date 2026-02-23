package com.cirta.bookinghotelagent.service.agent;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.springframework.stereotype.Service;

@Service
public class ServiceMessageFormatter {

    private final ChatModel chatModel;

    public ServiceMessageFormatter(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String format(String intent, String rawData, String fallback) {
        try {
            String prompt = "Intent: " + intent + "\n" +
                    "Données service: " + rawData + "\n" +
                    "Consigne: résume en français en 1-2 phrases, ton clair, orienté action client.";

            return chatModel.chat(ChatRequest.builder()
                            .messages(
                                    new SystemMessage("Tu es un assistant de support hôtelier. Réponds en français, concis, sans inventer."),
                                    new UserMessage(prompt)
                            )
                            .build())
                    .aiMessage()
                    .text();
        } catch (Exception ex) {
            return fallback;
        }
    }
}
