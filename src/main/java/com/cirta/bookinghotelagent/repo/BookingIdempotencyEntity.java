package com.cirta.bookinghotelagent.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "booking_idempotency")
public class BookingIdempotencyEntity {
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Lob
    @Column(name = "booking_json")
    private String bookingJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingIdempotencyEntity() {
    }

    public BookingIdempotencyEntity(String idempotencyKey, String status, String bookingJson, Instant createdAt, Instant updatedAt) {
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.bookingJson = bookingJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public String getBookingJson() {
        return bookingJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBookingJson(String bookingJson) {
        this.bookingJson = bookingJson;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
