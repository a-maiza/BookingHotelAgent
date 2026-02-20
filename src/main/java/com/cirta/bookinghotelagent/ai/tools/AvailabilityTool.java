package com.cirta.bookinghotelagent.ai.tools;

import com.cirta.bookinghotelagent.domain.Availability;
import com.cirta.bookinghotelagent.domain.RoomType;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AvailabilityTool {
    // mini “stock” en mémoire (pour apprendre). On passera DB plus tard.
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();

    public AvailabilityTool() {
        inventory.put("PARIS:DOUBLE", 3);
        inventory.put("PARIS:SUITE", 1);
        inventory.put("LYON:DOUBLE", 5);
    }

    @Tool("Vérifie la disponibilité de chambres pour une ville, des dates et un type de chambre (ex: DOUBLE, SUITE).")
    public Availability checkAvailability(String city, String roomType, String checkInIso, String checkOutIso) {
        LocalDate checkIn = LocalDate.parse(checkInIso);
        LocalDate checkOut = LocalDate.parse(checkOutIso);

        RoomType rt = RoomType.valueOf(roomType.toUpperCase());
        String key = city.toUpperCase() + ":" + roomType.toUpperCase();
        int available = inventory.getOrDefault(key, 0);

        return new Availability(city, checkIn, checkOut, rt, available);
    }

    // utilisé par BookingTool
    int decrementIfAvailable(String city, RoomType roomType) {
        String key = city.toUpperCase() + ":" + roomType.name();
        return inventory.compute(key, (k, v) -> (v == null || v <= 0) ? 0 : (v - 1));
    }

    int getAvailable(String city, RoomType roomType) {
        String key = city.toUpperCase() + ":" + roomType.name();
        return inventory.getOrDefault(key, 0);
    }
}
