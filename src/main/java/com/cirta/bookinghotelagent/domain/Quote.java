package com.cirta.bookinghotelagent.domain;

import java.time.LocalDate;

public record Quote(
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        RoomType roomType,
        int guests,
        double pricePerNight,
        double taxes,
        double total
) {
}
