package com.cirta.bookinghotelagent.domain;

public record AlternativeOffer(
        String offerId,
        String roomType,
        int availableRooms,
        String hotelName,
        Double totalPrice,
        String currency,
        String note
) {
}
