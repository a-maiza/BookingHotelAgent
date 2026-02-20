package com.cirta.bookinghotelagent.domain;

import java.time.LocalDate;

public record Availability(
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        RoomType roomType,
        int availableRooms
) {
}
