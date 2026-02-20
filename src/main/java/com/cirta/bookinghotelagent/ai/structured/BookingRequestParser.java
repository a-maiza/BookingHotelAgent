package com.cirta.bookinghotelagent.ai.structured;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.stereotype.Component;

@Component
public class BookingRequestParser {
    private final ChatModel model;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ResponseFormat responseFormat;

    public BookingRequestParser(ChatModel model) {
        this.model = model;
        this.responseFormat = buildResponseFormat();
    }

    public BookingRequestDraft parse(String userMessage) {
        // System prompt très strict pour extraction
        String system = """
        Tu es un extracteur de données.
        Transforme le message utilisateur en un objet JSON correspondant STRICTEMENT au schéma fourni.
        Règles:
        - N'invente pas d'informations.
        - Si une info n'est pas présente, mets la valeur à null.
        - Réponds uniquement avec du JSON conforme au schéma.
        """;

        ChatRequest req = ChatRequest.builder()
                .responseFormat(responseFormat)
                .messages(
                        new SystemMessage(system),
                        new UserMessage(userMessage)
                )
                .build();

        ChatResponse resp = model.chat(req);
        String json = resp.aiMessage().text();

        try {
            return mapper.readValue(json, BookingRequestDraft.class);
        } catch (Exception e) {
            // En prod: log + fallback. Ici: on remonte une erreur claire.
            throw new IllegalStateException("Impossible de parser le JSON du LLM: " + json, e);
        }
    }

    private static ResponseFormat buildResponseFormat() {
        // JSON Schema "fort" (root object + champs connus)
        JsonSchema schema = JsonSchema.builder()
                .name("BookingRequestDraft")
                .rootElement(
                        JsonObjectSchema.builder()
                                .addStringProperty("city")
                                .addStringProperty("checkIn")
                                .addStringProperty("checkOut")
                                .addStringProperty("roomType")
                                .addIntegerProperty("guests")
                                .addNumberProperty("budgetPerNight")
                                .addStringProperty("guestFullName")
                                .addStringProperty("email")
                                .addBooleanProperty("wantsToBookNow")
                                .build()
                )
                .build();

        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(schema)
                .build();
    }
}
