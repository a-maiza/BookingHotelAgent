package com.cirta.bookinghotelagent.repo;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "booking_session_state")
public class BookingSessionStateEntity {
    @Id
    @Column(name = "session_id", nullable = false, length = 120)
    private String sessionId;

    @Setter
    @Lob
    @Column(name = "state_json", nullable = false)
    private String stateJson;

    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BookingSessionStateEntity(String sessionId, String stateJson, Instant updatedAt) {
        this.sessionId = sessionId;
        this.stateJson = stateJson;
        this.updatedAt = updatedAt;
    }

}
