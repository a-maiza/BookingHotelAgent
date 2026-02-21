package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.domain.RoomType;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class PricingTool {
    @Tool("""
    Calcule un devis (prix total) pour une réservation.
    Retourne PricingResult avec status:
    - OK si devis calculé
    - INVALID_INPUT si dates invalides ou paramètres incohérents
    - ERROR si erreur inattendue
    """)
    public PricingResult quote(String city, String roomType, int guests, String checkInIso, String checkOutIso, double targetBudgetPerNight) {
        try {
            if (city == null || city.isBlank()) {
                return new PricingResult(PricingResult.Status.INVALID_INPUT, "Ville manquante.", null);
            }
            if (roomType == null || roomType.isBlank()) {
                return new PricingResult(PricingResult.Status.INVALID_INPUT, "Type de chambre manquant.", null);
            }
            if (guests <= 0) {
                return new PricingResult(PricingResult.Status.INVALID_INPUT, "Nombre de voyageurs invalide.", null);
            }

            LocalDate checkIn = LocalDate.parse(checkInIso);
            LocalDate checkOut = LocalDate.parse(checkOutIso);
            long nights = ChronoUnit.DAYS.between(checkIn, checkOut);

            if (nights <= 0) {
                return new PricingResult(PricingResult.Status.INVALID_INPUT,
                        "Dates invalides: checkOut doit être après checkIn.", null);
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

            Quote quote = new Quote(
                    city.toUpperCase(),
                    checkIn,
                    checkOut,
                    rt,
                    guests,
                    pricePerNight,
                    taxes,
                    total
            );
            return new PricingResult(PricingResult.Status.OK, "Devis calculé.", quote);
        } catch (Exception ex) {
            return new PricingResult(PricingResult.Status.ERROR,
                    "Erreur calcul devis: " + ex.getMessage(), null);
        }
    }
}
