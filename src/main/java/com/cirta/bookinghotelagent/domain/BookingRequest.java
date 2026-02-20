package com.cirta.bookinghotelagent.domain;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record BookingRequest(
        @NotBlank String city,
        @NotBlank String roomType,              // DOUBLE, SUITE...
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) @Max(10) int guests,
        @Email String email,
        @PositiveOrZero double budgetPerNight,
        @NotBlank String intent
) {}
