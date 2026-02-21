package com.cirta.bookinghotelagent.ai.structured;

import java.time.LocalDate;

public class BookingRequestState {
    public String city;
    public LocalDate checkIn;
    public LocalDate checkOut;
    public String roomType;        // "DOUBLE" / "SUITE"
    public Integer guests;
    public Double budgetPerNight;
    public String guestFullName;
    public String email;
    public boolean wantsToBookNow;

    public BookingRequestState merge(BookingRequestDraft d) {
        if (d == null) return this;

        if (notBlank(d.city())) this.city = d.city().trim();
        if (notBlank(d.roomType())) this.roomType = d.roomType().trim().toUpperCase();
        if (d.guests() != null) this.guests = d.guests();
        if (d.budgetPerNight() != null) this.budgetPerNight = d.budgetPerNight();
        if (notBlank(d.guestFullName())) this.guestFullName = d.guestFullName().trim();
        if (notBlank(d.email())) this.email = d.email().trim();
        if (d.wantsToBookNow() != null) this.wantsToBookNow = d.wantsToBookNow();

        if (notBlank(d.checkIn())) this.checkIn = LocalDate.parse(d.checkIn().trim());
        if (notBlank(d.checkOut())) this.checkOut = LocalDate.parse(d.checkOut().trim());

        return this;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
