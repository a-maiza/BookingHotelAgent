package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestParser;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.api.AgentResponse;
import com.cirta.bookinghotelagent.api.AgentStatus;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.rag.PolicyRetriever;
import com.cirta.bookinghotelagent.service.BookingIdempotencyStore;
import com.cirta.bookinghotelagent.service.BookingSessionStateStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class AgentOrchestrator {
    private final BookingRequestParser parser;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final BookingService bookingService;
    private final EmailService emailService;
    private final BookingSessionStateStore stateStore;
    private final PolicyRetriever policyRetriever;
    private final BookingIdempotencyStore idempotencyStore;

    public AgentOrchestrator(BookingRequestParser parser,
                             AvailabilityService availabilityService,
                             PricingService pricingService,
                             BookingService bookingService,
                             EmailService emailService,
                             BookingSessionStateStore stateStore,
                             PolicyRetriever policyRetriever,
                             BookingIdempotencyStore idempotencyStore) {
        this.parser = parser;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.bookingService = bookingService;
        this.emailService = emailService;
        this.stateStore = stateStore;
        this.policyRetriever = policyRetriever;
        this.idempotencyStore = idempotencyStore;
    }

    public AgentResponse handle(String sessionId, String userMessage) {
        if (policyRetriever.isPolicyQuestion(userMessage)) {
            String policyAnswer = policyRetriever.retrieve(userMessage);
            return new AgentResponse(sessionId, AgentStatus.POLICY_INFO, null, policyAnswer);
        }

        var draft = parser.parse(userMessage);

        BookingRequestState state = stateStore.loadOrNew(sessionId);
        state.merge(draft);
        stateStore.save(sessionId, state);

        String missing = nextMissingQuestion(state);
        if (missing != null) {
            return new AgentResponse(sessionId, AgentStatus.MISSING_INFO, null, missing);
        }

        if (!state.checkOut.isAfter(state.checkIn)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.INVALID_DATES,
                    null,
                    "La date de départ doit être après la date d’arrivée. Quelle est ta date de départ (YYYY-MM-DD) ?"
            );
        }

        var availability = availabilityService.check(state);
        if (availability.availableRooms() <= 0) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.NO_AVAILABILITY,
                    availability,
                    "Désolé, je n’ai plus de disponibilité pour %s à %s sur ces dates. Tu veux que je propose une SUITE ou d’autres dates ?"
            );
        }

        PricingResult quote = pricingService.quote(state);

        if (!state.wantsToBookNow) {
            return new AgentResponse(sessionId, AgentStatus.QUOTE_READY, quote, "Confirmez-vous la réservation ?");
        }

        if (state.email == null || state.email.isBlank()) {
            return new AgentResponse(sessionId, AgentStatus.EMAIL_REQUIRED, quote, "Veuillez fournir un email pour confirmer.");
        }

        var existingBooking = bookingService.findAlreadyConfirmed(sessionId, quote, state.guestFullName, state.email);
        if (existingBooking.isPresent()) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.BOOKING_CONFIRMED,
                    existingBooking.get(),
                    "Réservation déjà confirmée (idempotence)."
            );
        }

        if (!bookingService.claim(sessionId, quote, state.guestFullName, state.email)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.ERROR,
                    null,
                    "Une demande de réservation identique est déjà en cours de traitement."
            );
        }

        BookingCreateResult bookingCreateResult = bookingService.create(quote, state.guestFullName, state.email);
        emailService.sendBookingConfirmation(bookingCreateResult.booking());
        bookingService.markCompleted(sessionId, quote, state.guestFullName, state.email, bookingCreateResult.booking());

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
