package com.cirta.bookinghotelagent.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.Guest;
import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.integration.AmadeusClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BookingTool {
    private final AmadeusClient amadeusClient;
    private final ConcurrentMap<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingTool(AmadeusClient amadeusClient) {
        this.amadeusClient = amadeusClient;
    }

    @Tool("""
    Crée une réservation à partir d'un devis (quote) et d'un email.
    IMPORTANT: le Quote doit provenir d'un PricingToolResult avec status OK.
    Retourne BookingCreateResult. Ne pas inventer de bookingRef.
    """)
    public BookingCreateResult createBooking(PricingResult pricingResult, String guestFullName, String guestEmail) {
        try {
            if (guestEmail == null || guestEmail.isBlank() || !guestEmail.contains("@")) {
                return new BookingCreateResult(
                        BookingCreateResult.Status.INVALID_INPUT,
                        "Email client invalide.",
                        null
                );
            }
            Quote quote = pricingResult.quote();

            if (amadeusClient.enabled()) {
                String cityCode = toCityCode(quote.city());
                var offers = amadeusClient.searchHotelOffersByCity(
                        cityCode,
                        quote.checkIn().toString(),
                        quote.checkOut().toString(),
                        Math.max(1, quote.guests())
                ).orElse(null);

                if (offers != null && offers.path("data").isArray() && offers.path("data").size() > 0) {
                    String offerId = extractFirstOfferId(offers);
                    if (!offerId.isBlank()) {
                        String[] names = splitName(guestFullName);
                        amadeusClient.createHotelBooking(offerId, names[0], names[1], guestEmail);
                    }
                }
            }

            String ref = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Guest guest = new Guest(guestFullName, guestEmail);

            Booking booking = new Booking(
                    ref,
                    quote.city(),
                    quote.checkIn(),
                    quote.checkOut(),
                    quote.roomType(),
                    quote.guests(),
                    quote.total(),
                    guest
            );

            bookings.put(ref, booking);
            return new BookingCreateResult(
                    BookingCreateResult.Status.OK,
                    "Réservation créée.",
                    booking
            );
        } catch (Exception ex) {
            return new BookingCreateResult(
                    BookingCreateResult.Status.ERROR,
                    "Erreur création réservation: " + ex.getMessage(),
                    null
            );
        }
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
