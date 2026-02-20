package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.Guest;
import com.cirta.bookinghotelagent.domain.Quote;
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

    @Tool("Crée une réservation à partir d'un devis (quote) et d'un email client. Retourne une référence de réservation.")
    public Booking createBooking(Quote quote, String guestFullName, String guestEmail) {
        int available = availabilityTool.getAvailable(quote.city(), quote.roomType());
        if (available <= 0) {
            throw new IllegalStateException("Plus de disponibilité pour ce type de chambre");
        }

        availabilityTool.decrementIfAvailable(quote.city(), quote.roomType());

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
        return booking;
    }

    @Tool("Récupère une réservation par référence (bookingRef).")
    public Booking getBooking(String bookingRef) {
        Booking b = bookings.get(bookingRef);
        if (b == null) throw new IllegalArgumentException("Réservation introuvable: " + bookingRef);
        return b;
    }
}
