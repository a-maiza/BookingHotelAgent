package com.cirta.bookinghotelagent.service;

import com.cirta.bookinghotelagent.ai.structured.BookingRequestState;
import com.cirta.bookinghotelagent.repo.BookingSessionStateEntity;
import com.cirta.bookinghotelagent.repo.BookingSessionStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;

@Service
public class BookingSessionStateStore {
    private final BookingSessionStateRepository repository;
    private final ObjectMapper mapper;

    public BookingSessionStateStore(BookingSessionStateRepository repository) {
        this.repository = repository;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule()); // LocalDate support
    }

    @Transactional(readOnly = true)
    public BookingRequestState loadOrNew(String sessionId) {
        return repository.findById(sessionId)
                .map(e -> fromJson(e.getStateJson()))
                .orElseGet(BookingRequestState::new);
    }

    @Transactional
    public void save(String sessionId, BookingRequestState state) {
        String json = toJson(state);
        BookingSessionStateEntity entity = repository.findById(sessionId)
                .orElseGet(() -> new BookingSessionStateEntity(sessionId, json, Instant.now()));

        entity.setStateJson(json);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void delete(String sessionId) {
        repository.deleteById(sessionId);
    }

    private String toJson(BookingRequestState state) {
        try {
            return mapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de sérialiser BookingRequestState", e);
        }
    }

    private BookingRequestState fromJson(String json) {
        try {
            return mapper.readValue(json, BookingRequestState.class);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de désérialiser BookingRequestState: " + json, e);
        }
    }
}
