package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestParser;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.ai.tools.AvailabilityTool;
import com.cirta.bookinghotelagent.ai.tools.BookingTool;
import com.cirta.bookinghotelagent.ai.tools.EmailTool;
import com.cirta.bookinghotelagent.ai.tools.PricingTool;
import com.cirta.bookinghotelagent.api.AgentResponse;
import com.cirta.bookinghotelagent.api.AgentStatus;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.service.BookingIdempotencyStore;
import com.cirta.bookinghotelagent.service.BookingSessionStateStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AgentOrchestrator {
    private final BookingRequestParser parser;
    private final AvailabilityTool availabilityTool;
    private final PricingTool pricingTool;
    private final BookingTool bookingTool;
    private final EmailTool emailTool;
    private final BookingSessionStateStore stateStore;
    private final BookingIdempotencyStore idempotencyStore;


    public AgentOrchestrator(BookingRequestParser parser,
                             AvailabilityTool availabilityTool,
                             PricingTool pricingTool,
                             BookingTool bookingTool,
                             EmailTool emailTool,
                             BookingSessionStateStore stateStore,
                             BookingIdempotencyStore idempotencyStore) {
        this.parser = parser;
        this.availabilityTool = availabilityTool;
        this.pricingTool = pricingTool;
        this.bookingTool = bookingTool;
        this.emailTool = emailTool;
        this.stateStore = stateStore;
        this.idempotencyStore = idempotencyStore;
    }

    public AgentResponse handle(String sessionId, String userMessage) {
        // 1) parse structuré
        var draft = parser.parse(userMessage);

        // 2) Charger état depuis DB + merge + save
        BookingRequestState state = stateStore.loadOrNew(sessionId);
        state.merge(draft);
        stateStore.save(sessionId, state);

        // 3) validation “métier” progressive
        String missing = nextMissingQuestion(state);
        if (missing != null) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.MISSING_INFO,
                    null,
                    missing
            );
        }

        // 4) check cohérence dates
        if (!state.checkOut.isAfter(state.checkIn)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.INVALID_DATES,
                    null,
                    "La date de départ doit être après la date d’arrivée. Quelle est ta date de départ (YYYY-MM-DD) ?"
            );
        }

        // 5) disponibilité
        var availability = availabilityTool.checkAvailability(
                state.city,
                state.roomType,
                state.checkIn.toString(),
                state.checkOut.toString()
        );

        if (availability.availableRooms() <= 0) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.NO_AVAILABILITY,
                    availability,
                    "Désolé, je n’ai plus de disponibilité pour %s à %s sur ces dates. Tu veux que je propose une SUITE ou d’autres dates ?"
            );
        }

        // 6) devis
        double budget = (state.budgetPerNight != null) ? state.budgetPerNight : 0.0;
        PricingResult quote = pricingTool.quote(
                state.city,
                state.roomType,
                state.guests,
                state.checkIn.toString(),
                state.checkOut.toString(),
                budget
        );

        // 7) confirmation explicite si l'utilisateur n’a pas demandé “réserve”
        if (!state.wantsToBookNow) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.QUOTE_READY,
                    quote,
                    "Confirmez-vous la réservation ?"
            );
        }

        // 8) email requis pour confirmation finale (dans ta spec)
        if (state.email == null || state.email.isBlank()) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.EMAIL_REQUIRED,
                    quote,
                    "Veuillez fournir un email pour confirmer."
            );
        }

        String idempotencyKey = buildBookingIdempotencyKey(sessionId, quote, state.guestFullName, state.email);

        var existingBooking = idempotencyStore.findCompletedBooking(idempotencyKey);
        if (existingBooking.isPresent()) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.BOOKING_CONFIRMED,
                    existingBooking.get(),
                    "Réservation déjà confirmée (idempotence)."
            );
        }

        if (!idempotencyStore.claim(idempotencyKey)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.ERROR,
                    null,
                    "Une demande de réservation identique est déjà en cours de traitement."
            );
        }

        // 9) création réservation
        BookingCreateResult bookingCreateResult = bookingTool.createBooking(quote, state.guestFullName, state.email);

        // 10) envoi email
        emailTool.sendBookingConfirmationEmail(bookingCreateResult.booking());

        idempotencyStore.markCompleted(idempotencyKey, bookingCreateResult.booking());

        // Option: reset state ou le garder
        stateStore.delete(sessionId);

        return new AgentResponse(
                sessionId,
                AgentStatus.BOOKING_CONFIRMED,
                bookingCreateResult.booking(),
                "Réservation confirmée et email envoyé."
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

    private String buildBookingIdempotencyKey(String sessionId, PricingResult quote, String guestFullName, String email) {
        String fingerprint = String.join("|",
                sessionId,
                quote.quote().city(),
                quote.quote().checkIn().toString(),
                quote.quote().checkOut().toString(),
                quote.quote().roomType().name(),
                String.valueOf(quote.quote().guests()),
                String.valueOf(quote.quote().total()),
                guestFullName == null ? "" : guestFullName.trim().toLowerCase(),
                email == null ? "" : email.trim().toLowerCase()
        );

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer la clé d'idempotence", e);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
