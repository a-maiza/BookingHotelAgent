package com.cirta.bookinghotelagent.domain;

import java.time.LocalDate;

public record Booking(
        String bookingRef,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        RoomType roomType,
        int guests,
        double totalPrice,
        Guest guest
) {
}
