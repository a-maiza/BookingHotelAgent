package com.cirta.bookinghotelagent.domain;

public record AlternativeOffer(
        String roomType,
        int availableRooms,
        String note
) {
}
