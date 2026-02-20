package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.domain.RoomType;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class PricingTool {
    @Tool("Calcule un devis (prix total) pour une réservation. Fournir dates ISO (YYYY-MM-DD).")
    public Quote quote(String city, String roomType, int guests, String checkInIso, String checkOutIso, double targetBudgetPerNight) {
        LocalDate checkIn = LocalDate.parse(checkInIso);
        LocalDate checkOut = LocalDate.parse(checkOutIso);
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights <= 0) {
            throw new IllegalArgumentException("checkOut doit être après checkIn");
        }

        RoomType rt = RoomType.valueOf(roomType.toUpperCase());
        double base = switch (rt) {
            case SUITE -> 260.0;
            case DOUBLE -> 160.0;
            case SINGLE -> 140.0;
        };

        // Ajustement simple selon budget ciblé (pour démo)
        double pricePerNight = (targetBudgetPerNight > 0) ? Math.min(base, targetBudgetPerNight) : base;

        double subtotal = pricePerNight * nights;
        double taxes = subtotal * 0.10;
        double total = subtotal + taxes;

        return new Quote(city, checkIn, checkOut, rt, guests, pricePerNight, taxes, total);
    }
}
