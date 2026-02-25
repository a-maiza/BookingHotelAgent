package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Quote;
import com.cirta.bookinghotelagent.domain.RoomType;
import com.cirta.bookinghotelagent.domain.result.PricingResult;
import com.cirta.bookinghotelagent.integration.AmadeusClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
public class PricingTool {
    private final AmadeusClient amadeusClient;

    public PricingTool(AmadeusClient amadeusClient) {
        this.amadeusClient = amadeusClient;
    }

    @Tool("""
    Calcule un devis (prix total) pour une réservation.
    Retourne PricingResult avec status:
    - OK si devis calculé
    - INVALID_INPUT si dates invalides ou paramètres incohérents
    - ERROR si erreur inattendue
    """)
    public PricingResult quote(String city, String roomType, int guests, String checkInIso, String checkOutIso, double targetBudgetPerNight, String selectedOfferId) {
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

            RoomType rt = RoomType.valueOf(roomType.toUpperCase(Locale.ROOT));

            if (amadeusClient.enabled()) {
                PricingResult amadeusResult = quoteViaAmadeus(city, rt, guests, checkInIso, checkOutIso, targetBudgetPerNight, checkIn, checkOut, nights, selectedOfferId);
                if (amadeusResult != null) {
                    return amadeusResult;
                }
            }

            double base = switch (rt) {
                case SUITE -> 260.0;
                case DOUBLE -> 160.0;
                case SINGLE -> 140.0;
            };

            double pricePerNight = (targetBudgetPerNight > 0) ? Math.min(base, targetBudgetPerNight) : base;
            double subtotal = pricePerNight * nights;
            double taxes = subtotal * 0.10;
            double total = subtotal + taxes;

            Quote quote = new Quote(
                    city.toUpperCase(Locale.ROOT),
                    checkIn,
                    checkOut,
                    rt,
                    guests,
                    pricePerNight,
                    taxes,
                    total,
                    null
            );
            return new PricingResult(PricingResult.Status.OK, "Devis calculé (fallback local).", quote);
        } catch (Exception ex) {
            return new PricingResult(PricingResult.Status.ERROR,
                    "Erreur calcul devis: " + ex.getMessage(), null);
        }
    }

    private PricingResult quoteViaAmadeus(String city,
                                          RoomType roomType,
                                          int guests,
                                          String checkInIso,
                                          String checkOutIso,
                                          double targetBudgetPerNight,
                                          LocalDate checkIn,
                                          LocalDate checkOut,
                                          long nights,
                                          String selectedOfferId) {
        if (selectedOfferId != null && !selectedOfferId.isBlank()) {
            JsonNode selectedOfferJson = amadeusClient.getHotelOffer(selectedOfferId, "fr-FR").orElse(null);
            PricingResult selected = buildPricingResultFromOfferJson(city, roomType, guests, checkIn, checkOut, nights, selectedOfferId, selectedOfferJson);
            if (selected != null) {
                return selected;
            }
        }

        JsonNode offersJson = amadeusClient.searchHotelOffersByCity(toCityCode(city), checkInIso, checkOutIso, Math.max(1, guests))
                .orElse(null);
        if (offersJson == null) {
            return null;
        }

        OfferCandidate candidate = findBestOffer(offersJson, targetBudgetPerNight);
        if (candidate == null || candidate.offerId == null || candidate.offerId.isBlank()) {
            return null;
        }

        JsonNode pricedOfferJson = amadeusClient.getHotelOffer(candidate.offerId, "fr-FR").orElse(null);
        if (pricedOfferJson == null) {
            return null;
        }

        PricingResult fromCandidate = buildPricingResultFromOfferJson(
                city,
                roomType,
                guests,
                checkIn,
                checkOut,
                nights,
                candidate.offerId,
                pricedOfferJson
        );
        if (fromCandidate != null) {
            return fromCandidate;
        }
        return null;
    }

    private PricingResult buildPricingResultFromOfferJson(String city,
                                                         RoomType roomType,
                                                         int guests,
                                                         LocalDate checkIn,
                                                         LocalDate checkOut,
                                                         long nights,
                                                         String fallbackOfferId,
                                                         JsonNode pricedOfferJson) {
        if (pricedOfferJson == null) {
            return null;
        }

        JsonNode offer = pricedOfferJson.path("data").path("offer");
        String offerId = offer.path("id").asText(fallbackOfferId);
        double total = parseAmount(offer.path("price").path("total"), -1);
        if (total <= 0) {
            return null;
        }

        double taxes = parseTaxes(offer.path("price"), total);
        double pricePerNight = nights > 0 ? (total - taxes) / nights : total;

        Quote quote = new Quote(
                city.toUpperCase(Locale.ROOT),
                checkIn,
                checkOut,
                roomType,
                guests,
                Math.max(pricePerNight, 0),
                Math.max(taxes, 0),
                total,
                offerId
        );
        return new PricingResult(PricingResult.Status.OK, "Devis calculé via Amadeus (offer re-pricée).", quote);
    }

    private OfferCandidate findBestOffer(JsonNode offersJson, double targetBudgetPerNight) {
        JsonNode hotels = offersJson.path("data");
        if (!hotels.isArray()) {
            return null;
        }

        OfferCandidate best = null;
        for (JsonNode hotel : hotels) {
            JsonNode offers = hotel.path("offers");
            if (!offers.isArray()) {
                continue;
            }
            for (JsonNode offer : offers) {
                String offerId = offer.path("id").asText("");
                double total = parseAmount(offer.path("price").path("total"), -1);
                if (offerId.isBlank() || total <= 0) {
                    continue;
                }

                double score;
                if (targetBudgetPerNight > 0) {
                    double nightly = parseAmount(offer.path("price").path("base"), total);
                    score = Math.abs(nightly - targetBudgetPerNight);
                } else {
                    score = total;
                }

                if (best == null || score < best.score) {
                    best = new OfferCandidate(offerId, total, score);
                }
            }
        }
        return best;
    }

    private static double parseTaxes(JsonNode priceNode, double fallbackFromTotal) {
        JsonNode taxes = priceNode.path("taxes");
        if (taxes.isArray() && !taxes.isEmpty()) {
            double sum = 0;
            for (JsonNode tax : taxes) {
                sum += parseAmount(tax.path("amount"), 0);
            }
            if (sum > 0) {
                return sum;
            }
        }
        double base = parseAmount(priceNode.path("base"), -1);
        if (base >= 0 && fallbackFromTotal >= base) {
            return fallbackFromTotal - base;
        }
        return 0;
    }

    private static double parseAmount(JsonNode amountNode, double fallback) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return fallback;
        }
        if (amountNode.isNumber()) {
            return amountNode.asDouble();
        }
        String text = amountNode.asText("").trim();
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String toCityCode(String city) {
        if (city == null || city.isBlank()) {
            return "PAR";
        }
        String upper = city.trim().toUpperCase(Locale.ROOT);
        if (upper.length() >= 3) {
            return upper.substring(0, 3);
        }
        return (upper + "XXX").substring(0, 3);
    }

    private record OfferCandidate(String offerId, double totalAmount, double score) {}
}
