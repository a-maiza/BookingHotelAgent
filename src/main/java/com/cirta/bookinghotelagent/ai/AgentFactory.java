package com.cirta.bookinghotelagent.ai;

import com.cirta.bookinghotelagent.ai.memory.SessionMemoryStore;
import com.cirta.bookinghotelagent.ai.tools.AvailabilityTool;
import com.cirta.bookinghotelagent.ai.tools.BookingTool;
import com.cirta.bookinghotelagent.ai.tools.EmailTool;
import com.cirta.bookinghotelagent.ai.tools.PricingTool;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

@Component
public class AgentFactory {
    private final ChatModel model;
    private final AvailabilityTool availabilityTool;
    private final PricingTool pricingTool;
    private final BookingTool bookingTool;
    private final EmailTool emailTool;

    private final SessionMemoryStore memoryStore = new SessionMemoryStore();

    public AgentFactory(ChatModel model,
                        AvailabilityTool availabilityTool,
                        PricingTool pricingTool,
                        BookingTool bookingTool,
                        EmailTool emailTool) {
        this.model = model;
        this.availabilityTool = availabilityTool;
        this.pricingTool = pricingTool;
        this.bookingTool = bookingTool;
        this.emailTool = emailTool;
    }

    public HotelAgent forSession(String sessionId) {
        var memory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(30)
                .chatMemoryStore(memoryStore)
                .build();

        return AiServices.builder(HotelAgent.class)
                .chatModel(model)
                .chatMemory(memory)
                .tools(availabilityTool, pricingTool, bookingTool, emailTool)
                .build();
    }

}
