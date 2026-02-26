package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.Guest;
import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.integration.AmadeusClient;
import com.cirta.bookinghotelagent.service.BookingIdempotencyStore;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BookingTool {
    private final AmadeusClient amadeusClient;
    private final BookingIdempotencyStore idempotencyStore;
    private final ConcurrentMap<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingTool(AmadeusClient amadeusClient, BookingIdempotencyStore idempotencyStore) {
        this.amadeusClient = amadeusClient;
        this.idempotencyStore = idempotencyStore;
    }

    @Tool("""
    Crée une réservation à partir d'un devis (quote) et d'un email.
    IMPORTANT: le Quote doit provenir d'un PricingToolResult avec status OK.
    Retourne BookingCreateResult. Ne pas inventer de bookingRef.
    Gère automatiquement l'idempotence pour éviter les doubles réservations.
    """)
    public BookingCreateResult createBooking(PricingResult pricingResult, String guestFullName, String guestEmail) {
        if (guestEmail == null || guestEmail.isBlank() || !guestEmail.contains("@")) {
            return new BookingCreateResult(BookingCreateResult.Status.INVALID_INPUT, "Email client invalide.", null);
        }
        Quote quote = pricingResult.quote();
        String idempotencyKey = buildIdempotencyKey(quote, guestFullName, guestEmail);

        Optional<Booking> existing = idempotencyStore.findCompletedBooking(idempotencyKey);
        if (existing.isPresent()) {
            return new BookingCreateResult(BookingCreateResult.Status.OK, "Réservation déjà confirmée (idempotence).", existing.get());
        }
        if (!idempotencyStore.claim(idempotencyKey)) {
            return new BookingCreateResult(BookingCreateResult.Status.ERROR, "Une demande de réservation identique est déjà en cours de traitement.", null);
        }

        BookingCreateResult result = doCreateBooking(pricingResult, guestFullName, guestEmail);
        if (result.status() == BookingCreateResult.Status.OK && result.booking() != null) {
            idempotencyStore.markCompleted(idempotencyKey, result.booking());
        } else {
            idempotencyStore.releaseClaim(idempotencyKey);
        }
        return result;
    }

    private BookingCreateResult doCreateBooking(PricingResult pricingResult, String guestFullName, String guestEmail) {
        try {
            Quote quote = pricingResult.quote();
            Booking booking;
            String message = "Réservation créée.";

            if (amadeusClient.enabled()) {
                String offerId = quote.amadeusOfferId();

                if (offerId == null || offerId.isBlank()) {
                    String cityCode = toCityCode(quote.city());
                    var offers = amadeusClient.searchHotelOffersByCity(
                            cityCode,
                            quote.checkIn().toString(),
                            quote.checkOut().toString(),
                            Math.max(1, quote.guests())
                    ).orElse(null);
                    if (offers != null && offers.path("data").isArray() && offers.path("data").size() > 0) {
                        offerId = extractFirstOfferId(offers);
                    }
                }

                if (offerId != null && !offerId.isBlank()) {
                    JsonNode repricedOffer = amadeusClient.getHotelOffer(offerId, "fr-FR").orElse(null);
                    String repricedOfferId = repricedOffer != null
                            ? repricedOffer.path("data").path("offer").path("id").asText(offerId)
                            : offerId;

                    String[] names = splitName(guestFullName);
                    JsonNode orderResponse = amadeusClient
                            .createHotelBooking(repricedOfferId, names[0], names[1], guestEmail)
                            .orElse(null);

                    if (orderResponse != null) {
                        booking = mapAmadeusOrderToBooking(orderResponse, quote, guestFullName, guestEmail);
                        bookings.put(booking.bookingRef(), booking);
                        return new BookingCreateResult(BookingCreateResult.Status.OK, "Réservation créée via Amadeus.", booking);
                    }
                }
                message = "Réservation créée (fallback local, réponse Amadeus indisponible).";
            }

            booking = buildLocalBooking(quote, guestFullName, guestEmail);
            bookings.put(booking.bookingRef(), booking);
            return new BookingCreateResult(BookingCreateResult.Status.OK, message, booking);
        } catch (Exception ex) {
            return new BookingCreateResult(BookingCreateResult.Status.ERROR, "Erreur création réservation: " + ex.getMessage(), null);
        }
    }

    private String buildIdempotencyKey(Quote quote, String guestFullName, String guestEmail) {
        String fingerprint = String.join("|",
                quote.city(),
                quote.checkIn().toString(),
                quote.checkOut().toString(),
                quote.roomType().name(),
                String.valueOf(quote.guests()),
                String.valueOf(quote.total()),
                guestFullName == null ? "" : guestFullName.trim().toLowerCase(),
                guestEmail == null ? "" : guestEmail.trim().toLowerCase()
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer la clé d'idempotence", e);
        }
    }

    private Booking mapAmadeusOrderToBooking(JsonNode orderResponse,
                                             Quote fallbackQuote,
                                             String guestFullName,
                                             String guestEmail) {
        JsonNode data = orderResponse.path("data");

        String bookingRef = textOrFallback(
                data.path("id").asText(null),
                data.path("associatedRecords").isArray() && !data.path("associatedRecords").isEmpty()
                        ? data.path("associatedRecords").get(0).path("reference").asText(null)
                        : null,
                localRefFallback()
        );

        String city = textOrFallback(
                data.path("hotel").path("address").path("cityName").asText(null),
                fallbackQuote.city()
        );

        LocalDate checkIn = parseDateOrFallback(data.path("checkInDate").asText(null), fallbackQuote.checkIn());
        LocalDate checkOut = parseDateOrFallback(data.path("checkOutDate").asText(null), fallbackQuote.checkOut());

        double totalPrice = doubleOrFallback(
                data.path("price").path("total").asDouble(Double.NaN),
                fallbackQuote.total()
        );

        Guest guest = new Guest(guestFullName, guestEmail);

        return new Booking(
                bookingRef,
                city,
                checkIn,
                checkOut,
                fallbackQuote.roomType(),
                fallbackQuote.guests(),
                totalPrice,
                guest
        );
    }

    private Booking buildLocalBooking(Quote quote, String guestFullName, String guestEmail) {
        return new Booking(
                localRefFallback(),
                quote.city(),
                quote.checkIn(),
                quote.checkOut(),
                quote.roomType(),
                quote.guests(),
                quote.total(),
                new Guest(guestFullName, guestEmail)
        );
    }

    private static String extractFirstOfferId(JsonNode offersResponse) {
        JsonNode data = offersResponse.path("data");
        if (!data.isArray() || data.isEmpty()) {
            return "";
        }

        JsonNode hotelNode = data.get(0);
        JsonNode offers = hotelNode.path("offers");
        if (!offers.isArray() || offers.isEmpty()) {
            return "";
        }

        return offers.get(0).path("id").asText("");
    }

    private static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"Guest", "Client"};
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], "Client"};
        }
        return parts;
    }

    private static String textOrFallback(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static String textOrFallback(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback;
    }

    private static LocalDate parseDateOrFallback(String value, LocalDate fallback) {
        try {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            return LocalDate.parse(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static double doubleOrFallback(double candidate, double fallback) {
        return Double.isNaN(candidate) ? fallback : candidate;
    }

    private static String localRefFallback() {
        return "BK-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String toCityCode(String city) {
        if (city == null || city.isBlank()) {
            return "PAR";
        }
        String upper = city.trim().toUpperCase();
        if (upper.length() >= 3) {
            return upper.substring(0, 3);
        }
        return (upper + "XXX").substring(0, 3);
    }
}
