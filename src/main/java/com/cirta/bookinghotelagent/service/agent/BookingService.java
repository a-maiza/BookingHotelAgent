package com.cirta.bookinghotelagent.service.agent;

import com.cirta.bookinghotelagent.ai.tools.BookingTool;
import com.cirta.bookinghotelagent.domain.Booking;
import com.cirta.bookinghotelagent.domain.result.BookingCreateResult;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.service.BookingIdempotencyStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class BookingService {
    private final BookingTool bookingTool;
    private final BookingIdempotencyStore idempotencyStore;

    public BookingService(BookingTool bookingTool, BookingIdempotencyStore idempotencyStore) {
        this.bookingTool = bookingTool;
        this.idempotencyStore = idempotencyStore;
    }

    public Optional<Booking> findAlreadyConfirmed(String sessionId, PricingResult quote, String guestFullName, String email) {
        String key = buildBookingIdempotencyKey(sessionId, quote, guestFullName, email);
        return idempotencyStore.findCompletedBooking(key);
    }

    public boolean claim(String sessionId, PricingResult quote, String guestFullName, String email) {
        String key = buildBookingIdempotencyKey(sessionId, quote, guestFullName, email);
        return idempotencyStore.claim(key);
    }

    public BookingCreateResult create(PricingResult quote, String guestFullName, String email) {
        return bookingTool.createBooking(quote, guestFullName, email);
    }

    public void markCompleted(String sessionId, PricingResult quote, String guestFullName, String email, Booking booking) {
        String key = buildBookingIdempotencyKey(sessionId, quote, guestFullName, email);
        idempotencyStore.markCompleted(key, booking);
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
}
