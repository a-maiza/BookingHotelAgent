package com.cirta.bookinghotelagent.repo;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "booking_session_state")
public class BookingSessionStateEntity {
    @Id
    @Column(name = "session_id", nullable = false, length = 120)
    private String sessionId;

    @Lob
    @Column(name = "state_json", nullable = false)
    private String stateJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingSessionStateEntity() {}

    public BookingSessionStateEntity(String sessionId, String stateJson, Instant updatedAt) {
        this.sessionId = sessionId;
        this.stateJson = stateJson;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() { return sessionId; }
    public String getStateJson() { return stateJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
