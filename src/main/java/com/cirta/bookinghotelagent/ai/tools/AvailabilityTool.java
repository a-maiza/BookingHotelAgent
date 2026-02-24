package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.AlternativeOffer;
import com.cirta.bookinghotelagent.domain.RoomType;
import com.cirta.bookinghotelagent.domain.result.AvailabilityCheckResult;
import com.cirta.bookinghotelagent.integration.AmadeusClient;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AvailabilityTool {
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final AmadeusClient amadeusClient;

    public AvailabilityTool(AmadeusClient amadeusClient) {
        this.amadeusClient = amadeusClient;
        inventory.put("PARIS:DOUBLE", 0);
        inventory.put("PARIS:SUITE", 1);
        inventory.put("PARIS:SINGLE", 2);
        inventory.put("LYON:DOUBLE", 5);
    }

    @Tool("""
    Vérifie la disponibilité. Retourne un AvailabilityCheckResult avec status:
    - OK si dispo > 0
    - NO_AVAILABILITY si dispo = 0 (et alternatives si possibles)
    - INVALID_INPUT si dates invalides
    """)
    public AvailabilityCheckResult checkAvailability(String city, String roomType, String checkInIso, String checkOutIso) {
        String normalizedCity = city.toUpperCase();
        RoomType normalizedRoom = RoomType.valueOf(roomType.toUpperCase());

        try {
            LocalDate checkIn = LocalDate.parse(checkInIso);
            LocalDate checkOut = LocalDate.parse(checkOutIso);

            if (!checkOut.isAfter(checkIn)) {
                return new AvailabilityCheckResult(
                        AvailabilityCheckResult.Status.INVALID_INPUT,
                        "Dates invalides: checkOut doit être après checkIn.",
                        city, checkIn, checkOut, normalizedRoom, 0,
                        List.of()
                );
            }

            if (amadeusClient.enabled()) {
                String cityCode = toCityCode(normalizedCity);
                var offersJson = amadeusClient.searchHotelOffersByCity(cityCode, checkInIso, checkOutIso, 1).orElse(null);
                if (offersJson != null) {
                    int available = offersJson.path("data").isArray() ? offersJson.path("data").size() : 0;
                    if (available > 0) {
                        return new AvailabilityCheckResult(
                                AvailabilityCheckResult.Status.OK,
                                "Disponibilités récupérées via Amadeus.",
                                normalizedCity, checkIn, checkOut, normalizedRoom, available,
                                List.of()
                        );
                    }
                }
            }

            int available = getAvailable(normalizedCity, normalizedRoom);
            if (available > 0) {
                return new AvailabilityCheckResult(
                        AvailabilityCheckResult.Status.OK,
                        "Disponibilité trouvée (fallback local).",
                        normalizedCity, checkIn, checkOut, normalizedRoom, available,
                        List.of()
                );
            }

            List<AlternativeOffer> alternatives = inventory.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(normalizedCity + ":"))
                    .map(e -> Map.entry(e.getKey().split(":")[1], e.getValue()))
                    .filter(e -> e.getValue() != null && e.getValue() > 0)
                    .map(e -> new AlternativeOffer(e.getKey(), e.getValue(), "Alternative disponible à " + normalizedCity))
                    .toList();

            String msg = alternatives.isEmpty()
                    ? "Aucune chambre disponible dans cette ville pour ces dates."
                    : "Le type demandé n'est pas disponible. Alternatives possibles.";

            return new AvailabilityCheckResult(
                    AvailabilityCheckResult.Status.NO_AVAILABILITY,
                    msg,
                    normalizedCity, checkIn, checkOut, normalizedRoom, 0,
                    alternatives
            );

        } catch (Exception ex) {
            return new AvailabilityCheckResult(
                    AvailabilityCheckResult.Status.ERROR,
                    "Erreur lors de la vérification de disponibilité: " + ex.getMessage(),
                    city, null, null, normalizedRoom, 0,
                    List.of()
            );
        }
    }

    int getAvailable(String city, RoomType roomType) {
        String key = city.toUpperCase() + ":" + roomType.name();
        return inventory.getOrDefault(key, 0);
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
