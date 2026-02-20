package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestParser;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.ai.tools.AvailabilityTool;
import com.cirta.bookinghotelagent.ai.tools.BookingTool;
import com.cirta.bookinghotelagent.ai.tools.EmailTool;
import com.cirta.bookinghotelagent.ai.tools.PricingTool;
import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.service.BookingSessionStateStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentOrchestrator {
    private final BookingRequestParser parser;
    private final AvailabilityTool availabilityTool;
    private final PricingTool pricingTool;
    private final BookingTool bookingTool;
    private final EmailTool emailTool;
    private final BookingSessionStateStore stateStore;

    // état session en mémoire (on passera DB/Redis plus tard)
    private final Map<String, BookingRequestState> sessions = new ConcurrentHashMap<>();

    public AgentOrchestrator(BookingRequestParser parser,
                             AvailabilityTool availabilityTool,
                             PricingTool pricingTool,
                             BookingTool bookingTool,
                             EmailTool emailTool, BookingSessionStateStore stateStore) {
        this.parser = parser;
        this.availabilityTool = availabilityTool;
        this.pricingTool = pricingTool;
        this.bookingTool = bookingTool;
        this.emailTool = emailTool;
        this.stateStore = stateStore;
    }

    public String handle(String sessionId, String userMessage) {
        // 1) parse structuré
        var draft = parser.parse(userMessage);

        // 2) Charger état depuis DB + merge + save
        BookingRequestState state = stateStore.loadOrNew(sessionId);
        state.merge(draft);
        stateStore.save(sessionId, state);

        // 3) validation “métier” progressive
        String question = nextMissingQuestion(state);
        if (question != null) {
            return question;
        }

        // 4) check cohérence dates
        if (!state.checkOut.isAfter(state.checkIn)) {
            return "La date de départ doit être après la date d’arrivée. Quelle est ta date de départ (YYYY-MM-DD) ?";
        }

        // 5) disponibilité
        var availability = availabilityTool.checkAvailability(
                state.city,
                state.roomType,
                state.checkIn.toString(),
                state.checkOut.toString()
        );

        if (availability.availableRooms() <= 0) {
            return """
            Désolé, je n’ai plus de disponibilité pour %s à %s sur ces dates.
            Tu veux que je propose une SUITE ou d’autres dates ?
            """.formatted(state.roomType, state.city);
        }

        // 6) devis
        double budget = (state.budgetPerNight != null) ? state.budgetPerNight : 0.0;
        Quote quote = pricingTool.quote(
                state.city,
                state.roomType,
                state.guests,
                state.checkIn.toString(),
                state.checkOut.toString(),
                budget
        );

        // 7) confirmation explicite si l'utilisateur n’a pas demandé “réserve”
        if (!state.wantsToBookNow) {
            return """
            J’ai de la disponibilité ✅
            
            Devis:
            - Ville: %s
            - Dates: %s → %s
            - Chambre: %s
            - Voyageurs: %d
            - Prix/nuit: %.2f €
            - Taxes: %.2f €
            - Total: %.2f €
            
            Souhaites-tu que je confirme la réservation ?
            """.formatted(
                    quote.city(),
                    quote.checkIn(),
                    quote.checkOut(),
                    quote.roomType(),
                    quote.guests(),
                    quote.pricePerNight(),
                    quote.taxes(),
                    quote.total()
            );
        }

        // 8) email requis pour confirmation finale (dans ta spec)
        if (state.email == null || state.email.isBlank()) {
            return "Pour confirmer la réservation et t’envoyer l’email, j’ai besoin de ton email. Quelle adresse ?";
        }

        // 9) création réservation
        Booking booking = bookingTool.createBooking(quote, state.guestFullName, state.email);

        // 10) envoi email
        String emailResult = emailTool.sendBookingConfirmationEmail(booking);

        // Option: reset state ou le garder
        stateStore.delete(sessionId);

        return """
        Réservation confirmée ✅
        
        - Référence: %s
        - Ville: %s
        - Dates: %s → %s
        - Chambre: %s
        - Voyageurs: %d
        - Total: %.2f €
        
        %s
        """.formatted(
                booking.bookingRef(),
                booking.city(),
                booking.checkIn(),
                booking.checkOut(),
                booking.roomType(),
                booking.guests(),
                booking.totalPrice(),
                emailResult
        );
    }

    private String nextMissingQuestion(BookingRequestState s) {
        if (isBlank(s.city)) return "Dans quelle ville souhaites-tu réserver ?";
        if (s.checkIn == null) return "Quelle est ta date d’arrivée (YYYY-MM-DD) ?";
        if (s.checkOut == null) return "Quelle est ta date de départ (YYYY-MM-DD) ?";
        if (isBlank(s.roomType)) return "Quel type de chambre veux-tu ? (DOUBLE ou SUITE)";
        if (s.guests == null || s.guests <= 0) return "Pour combien de personnes ?";
        if (isBlank(s.guestFullName)) return "Quel est ton nom complet (pour la réservation) ?";
        return null;
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
