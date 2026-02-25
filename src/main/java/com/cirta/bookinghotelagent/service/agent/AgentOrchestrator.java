package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.agent.BookingAgent;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestParser;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.api.AgentResponse;
import com.cirta.bookinghotelagent.api.AgentStatus;
import com.cirta.bookinghotelagent.service.BookingSessionStateStore;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {

    private static final String NOT_SET = "non renseigné";

    private final BookingRequestParser parser;
    private final BookingSessionStateStore stateStore;
    private final BookingAgent bookingAgent;

    public AgentOrchestrator(BookingRequestParser parser,
                             BookingSessionStateStore stateStore,
                             BookingAgent bookingAgent) {
        this.parser = parser;
        this.stateStore = stateStore;
        this.bookingAgent = bookingAgent;
    }

    public AgentResponse handle(String sessionId, String userMessage) {
        // Accumulate structured state across turns for tool parameter context
        BookingRequestState state = stateStore.loadOrNew(sessionId);
        state.merge(parser.parse(userMessage));
        stateStore.save(sessionId, state);

        // Build an enriched message that injects the current session state as
        // context so the LLM can pass the right parameters when calling tools
        String enrichedMessage = buildContextMessage(state, userMessage);

        String responseText = bookingAgent.chat(sessionId, enrichedMessage);

        return mapToAgentResponse(sessionId, responseText);
    }

    private String buildContextMessage(BookingRequestState state, String userMessage) {
        return """
                [Contexte session en cours]
                Ville      : %s
                Arrivée    : %s
                Départ     : %s
                Chambre    : %s
                Personnes  : %s
                Nom client : %s
                Email      : %s
                Offre sel. : %s
                [Message utilisateur]
                %s
                """.formatted(
                orEmpty(state.city),
                state.checkIn != null ? state.checkIn.toString() : NOT_SET,
                state.checkOut != null ? state.checkOut.toString() : NOT_SET,
                orEmpty(state.roomType),
                state.guests != null ? state.guests.toString() : NOT_SET,
                orEmpty(state.guestFullName),
                orEmpty(state.email),
                orEmpty(state.selectedOfferId),
                userMessage
        );
    }

    private AgentResponse mapToAgentResponse(String sessionId, String responseText) {
        String lower = responseText.toLowerCase();

        if (lower.contains("bk-")) {
            stateStore.delete(sessionId);
            return new AgentResponse(sessionId, AgentStatus.BOOKING_CONFIRMED, null, responseText);
        }
        if (lower.contains("devis") || lower.contains("total") || lower.contains("€")) {
            return new AgentResponse(sessionId, AgentStatus.QUOTE_READY, null, responseText);
        }
        if (lower.contains("politique") || lower.contains("annulation") || lower.contains("remboursement")) {
            return new AgentResponse(sessionId, AgentStatus.POLICY_INFO, null, responseText);
        }
        if (lower.contains("pas disponible") || lower.contains("indisponible") || lower.contains("complet")) {
            return new AgentResponse(sessionId, AgentStatus.NO_AVAILABILITY, null, responseText);
        }
        if (lower.contains("offre") && lower.contains("id")) {
            return new AgentResponse(sessionId, AgentStatus.OFFER_SELECTION_REQUIRED, null, responseText);
        }
        return new AgentResponse(sessionId, AgentStatus.MISSING_INFO, null, responseText);
    }

    private static String orEmpty(String value) {
        return (value != null && !value.isBlank()) ? value : NOT_SET;
    }
}
