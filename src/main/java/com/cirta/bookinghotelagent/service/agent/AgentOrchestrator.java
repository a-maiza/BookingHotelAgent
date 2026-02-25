package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestParser;
import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.api.AgentResponse;
import com.cirta.bookinghotelagent.api.AgentStatus;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.EmailSendResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.rag.PolicyRetriever;
import com.cirta.bookinghotelagent.service.BookingSessionStateStore;
import org.springframework.stereotype.Service;

@Service
public class AgentOrchestrator {
    private final BookingRequestParser parser;
    private final AvailabilityService availabilityService;
    private final PricingService pricingService;
    private final BookingService bookingService;
    private final EmailService emailService;
    private final BookingSessionStateStore stateStore;
    private final PolicyRetriever policyRetriever;
    private final ServiceMessageFormatter serviceMessageFormatter;

    public AgentOrchestrator(BookingRequestParser parser,
                             AvailabilityService availabilityService,
                             PricingService pricingService,
                             BookingService bookingService,
                             EmailService emailService,
                             BookingSessionStateStore stateStore,
                             PolicyRetriever policyRetriever,
                             ServiceMessageFormatter serviceMessageFormatter) {
        this.parser = parser;
        this.availabilityService = availabilityService;
        this.pricingService = pricingService;
        this.bookingService = bookingService;
        this.emailService = emailService;
        this.stateStore = stateStore;
        this.policyRetriever = policyRetriever;
        this.serviceMessageFormatter = serviceMessageFormatter;
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
        Integer availableRooms = availability.availableRooms();
        if (availableRooms == null || availableRooms <= 0) {
            String msg = serviceMessageFormatter.format(
                    "availability_no",
                    availability.toString(),
                    "Désolé, je n’ai plus de disponibilité pour ces dates. Voulez-vous d'autres options ?"
            );
            return new AgentResponse(sessionId, AgentStatus.NO_AVAILABILITY, availability, msg);
        }


        if (needsOfferSelection(state, availability)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.OFFER_SELECTION_REQUIRED,
                    availability,
                    "J'ai trouvé des offres Amadeus. Donnez-moi l'offerId qui vous convient (ex: OFF123)."
            );
        }

        PricingResult quote = pricingService.quote(state);

        if (!state.wantsToBookNow) {
            String msg = serviceMessageFormatter.format(
                    "quote_ready",
                    quote.toString(),
                    "Voici votre devis. Confirmez-vous la réservation ?"
            );
            return new AgentResponse(sessionId, AgentStatus.QUOTE_READY, quote, msg);
        }

        if (state.email == null || state.email.isBlank()) {
            return new AgentResponse(sessionId, AgentStatus.EMAIL_REQUIRED, quote, "Veuillez fournir un email pour confirmer.");
        }

        PricingResult bookingQuote = quote;
        if (bookingQuote.status() != PricingResult.Status.OK || bookingQuote.quote() == null) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.ERROR,
                    quote,
                    "Impossible de confirmer la réservation maintenant. Merci de relancer un devis puis de réessayer. Détail: " + bookingQuote.message()
            );
        }

        var existingBooking = bookingService.findAlreadyConfirmed(sessionId, bookingQuote, state.guestFullName, state.email);
        if (existingBooking.isPresent()) {
            String msg = serviceMessageFormatter.format(
                    "booking_already_confirmed",
                    existingBooking.get().toString(),
                    "Réservation déjà confirmée (idempotence)."
            );
            return new AgentResponse(sessionId, AgentStatus.BOOKING_CONFIRMED, existingBooking.get(), msg);
        }

        if (!bookingService.claim(sessionId, bookingQuote, state.guestFullName, state.email)) {
            return new AgentResponse(
                    sessionId,
                    AgentStatus.ERROR,
                    null,
                    "Une demande de réservation identique est déjà en cours de traitement."
            );
        }

        BookingCreateResult bookingCreateResult = bookingService.create(bookingQuote, state.guestFullName, state.email);
        if (bookingCreateResult.status() != BookingCreateResult.Status.OK || bookingCreateResult.booking() == null) {
            bookingService.releaseClaim(sessionId, bookingQuote, state.guestFullName, state.email);
            return new AgentResponse(
                    sessionId,
                    AgentStatus.ERROR,
                    bookingQuote,
                    "Réservation non confirmée. " + bookingCreateResult.message() + " Si vous utilisez Amadeus, il peut être nécessaire de refaire un devis juste avant la confirmation."
            );
        }

        EmailSendResult emailSendResult = emailService.sendBookingConfirmation(bookingCreateResult.booking());
        bookingService.markCompleted(sessionId, bookingQuote, state.guestFullName, state.email, bookingCreateResult.booking());

        stateStore.delete(sessionId);

        String fallback = emailSendResult.status() == EmailSendResult.Status.OK
                ? "Réservation confirmée et email envoyé."
                : "Réservation confirmée, mais l'email de confirmation n'a pas pu être envoyé: " + emailSendResult.message();

        String msg = serviceMessageFormatter.format(
                "booking_confirmed",
                bookingCreateResult.booking().toString(),
                fallback
        );
        return new AgentResponse(sessionId, AgentStatus.BOOKING_CONFIRMED, bookingCreateResult.booking(), msg);
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

    private boolean needsOfferSelection(BookingRequestState state, com.cirta.bookinghotelagent.domain.result.AvailabilityCheckResult availability) {
        return (state.selectedOfferId == null || state.selectedOfferId.isBlank())
                && availability.alternatives() != null
                && availability.alternatives().stream().anyMatch(o -> o.offerId() != null && !o.offerId().isBlank());
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }
}
