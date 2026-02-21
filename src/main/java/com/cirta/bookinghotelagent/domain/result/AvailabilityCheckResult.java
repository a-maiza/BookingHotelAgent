package com.cirta.bookinghotelagent.domain.result;

import com.cirta.bookinghotelagent.domain.AlternativeOffer;
import com.cirta.bookinghotelagent.domain.RoomType;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityCheckResult(
        Status status,
        String message,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        RoomType requestedRoomType,
        Integer availableRooms,
        List<AlternativeOffer> alternatives
) {
    public enum Status { OK, NO_AVAILABILITY, INVALID_INPUT, ERROR }
}
