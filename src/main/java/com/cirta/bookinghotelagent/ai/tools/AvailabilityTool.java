package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.AlternativeOffer;
import com.cirta.bookinghotelagent.domain.result.AvailabilityCheckResult;
import com.cirta.bookinghotelagent.domain.RoomType;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AvailabilityTool {
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();

    public AvailabilityTool() {
        inventory.put("PARIS:DOUBLE", 0); // mets 0 pour tester les alternatives
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
                        city, checkIn, checkOut, normalizedRoom, null,
                        List.of()
                );
            }


            int available = getAvailable(normalizedCity, normalizedRoom);

            if (available > 0) {
                return new AvailabilityCheckResult(
                        AvailabilityCheckResult.Status.OK,
                        "Disponibilité trouvée.",
                        normalizedCity, checkIn, checkOut, normalizedRoom, available,
                        List.of()
                );
            }

            // alternatives: autres roomTypes dans la même ville avec dispo > 0
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
                    city, null, null, normalizedRoom, null,
                    List.of()
            );
        }
    }

    int getAvailable(String city, RoomType roomType) {
        String key = city.toUpperCase() + ":" + roomType.name();
        return inventory.getOrDefault(key, 0);
    }

    // utilisé par BookingTool
    void decrementIfAvailable(String cityUpper, RoomType roomTypeUpper) {
        String key = cityUpper + ":" + roomTypeUpper;
        inventory.compute(key, (k, v) -> {
            if (v == null || v <= 0) return 0;
            return v - 1;
        });
        getAvailable(cityUpper, roomTypeUpper);
    }
}
