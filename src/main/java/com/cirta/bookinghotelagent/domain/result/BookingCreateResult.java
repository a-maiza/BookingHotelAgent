package com.cirta.bookinghotelagent.domain.result;

import com.cirta.bookinghotelagent.domain.Booking;

public record BookingCreateResult(
        Status status,
        String message,
        Booking booking
) {
    public enum Status { OK, NO_AVAILABILITY, INVALID_INPUT, ERROR }
}
