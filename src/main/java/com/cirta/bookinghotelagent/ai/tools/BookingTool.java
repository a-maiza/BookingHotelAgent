package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.*;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class BookingTool {
    private final AvailabilityTool availabilityTool;
    private final ConcurrentMap<String, Booking> bookings = new ConcurrentHashMap<>();

    public BookingTool(AvailabilityTool availabilityTool) {
        this.availabilityTool = availabilityTool;
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
}
